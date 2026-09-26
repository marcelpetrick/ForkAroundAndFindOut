// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.util.Linkify
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import it.marcelpetrick.fork.camera.CameraSession
import it.marcelpetrick.fork.camera.FrameInfo
import it.marcelpetrick.fork.camera.FrameSource
import it.marcelpetrick.fork.demo.SyntheticDemo
import it.marcelpetrick.fork.detection.ArmResult
import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Polygon
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.SeatProposal
import it.marcelpetrick.fork.detection.SeatResult
import it.marcelpetrick.fork.monitoring.LocalStore
import it.marcelpetrick.fork.monitoring.MealSummary
import it.marcelpetrick.fork.monitoring.Monitor
import it.marcelpetrick.fork.monitoring.MonitorSession
import it.marcelpetrick.fork.monitoring.MonitorUiState
import it.marcelpetrick.fork.monitoring.PoseModel
import it.marcelpetrick.fork.monitoring.SessionFiles
import it.marcelpetrick.fork.monitoring.SessionLog
import it.marcelpetrick.fork.monitoring.SessionRecorder
import it.marcelpetrick.fork.monitoring.Settings
import it.marcelpetrick.fork.monitoring.Sound
import it.marcelpetrick.fork.monitoring.Status
import it.marcelpetrick.fork.monitoring.VisibilityCheck
import it.marcelpetrick.fork.monitoring.VisualMode
import it.marcelpetrick.fork.monitoring.sampleRecord
import it.marcelpetrick.fork.monitoring.sessionRecord
import it.marcelpetrick.fork.ui.ChimeSpeaker
import it.marcelpetrick.fork.ui.Lens
import it.marcelpetrick.fork.ui.Palette
import it.marcelpetrick.fork.ui.Speaker
import it.marcelpetrick.fork.ui.StageView
import it.marcelpetrick.fork.ui.action
import it.marcelpetrick.fork.ui.card
import it.marcelpetrick.fork.ui.chip
import it.marcelpetrick.fork.ui.column
import it.marcelpetrick.fork.ui.label
import it.marcelpetrick.fork.ui.lensLabels
import it.marcelpetrick.fork.ui.row
import it.marcelpetrick.fork.ui.settingsOptions
import it.marcelpetrick.fork.ui.title
import it.marcelpetrick.fork.ui.update
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID

internal fun thermalLabel(status: Int): String =
    when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "none"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "severe"
        else -> "critical"
    }

typealias SourceFactory = (
    PreviewView,
    Settings,
    (List<Pose>, Long, FrameInfo) -> Unit,
    (String) -> Unit,
) -> FrameSource

/** Single-activity native UI. All state lives on the main thread. */
class MainActivity : ComponentActivity() {
    enum class Screen { WELCOME, POSITION, TABLE, SEATS, MONITOR, DEMO, SETTINGS, DATA, ABOUT }

    internal lateinit var store: LocalStore
    internal var settings = Settings()
        private set
    internal var screen = Screen.WELCOME
        private set
    internal var notice: String? = null
    internal var sourceFactory: SourceFactory = { view, s, frame, error -> CameraSession(this, this, view, s, frame, error) }
    internal var speaker: Speaker = ChimeSpeaker(this)
    internal var clock: () -> Long = SystemClock::uptimeMillis
    internal var cameraIds: () -> List<Lens> = ::backCameras

    /** Android thermal status (PowerManager.THERMAL_STATUS_*); replaceable in tests. */
    internal var thermalStatus: () -> Int = {
        getSystemService(PowerManager::class.java)?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
    }

    /** The running meal, or null. */
    internal var meal: MonitorSession? = null
        private set
    internal val monitor: Monitor?
        get() = meal?.monitor

    /** Summary of the last finished meal, shown on the welcome screen. */
    internal var lastSummary: MealSummary? = null
        private set
    internal var stage: StageView? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = Runnable { tick() }
    private var source: FrameSource? = null
    private var preview: PreviewView? = null
    private var panel: LinearLayout? = null
    private var status: TextView? = null
    private var seatCards: LinearLayout? = null
    private var sessionLine: TextView? = null
    private var banner: LinearLayout? = null
    private var diagnosticsText: TextView? = null
    private var trainingStatus: TextView? = null
    private var advice: TextView? = null
    private var seatCheck: TextView? = null

    /** The camera permission was refused: the welcome card offers Android's app settings. */
    private var permissionDenied = false
    private var visibilityCheck: VisibilityCheck? = null
    private val taps = mutableListOf<Point>()
    private val seats = mutableListOf<Polygon>()
    private var lastFrame = 0L
    private var fps = 0.0
    private val latencies = ArrayDeque<Long>()
    private var frameInfo: FrameInfo? = null

    /** True once a frame (and with it the image geometry and view mapping) has arrived. */
    internal val cameraReady: Boolean
        get() = frameInfo != null && stage?.mapping != null
    private var demoMonitor: Monitor? = null
    private var demoStart = 0L
    private var diagnosticsOpen = false
    private var pendingCamera: Screen? = null
    private var adult: LinearLayout? = null
    private var training = false
    private var trainingSeat = 1
    internal var recorder: SessionRecorder? = null
        private set
    private var exportLog: File? = null
    internal val sessionsDir: File
        get() = File(filesDir, "sessions")

    private val exporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) exportTo(uri) { it.write(store.export().toByteArray()) }
        }

    private val logExporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
            val log = exportLog
            exportLog = null
            if (uri != null && log != null) exportTo(uri) { out -> log.inputStream().use { it.copyTo(out) } }
        }

    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val target = pendingCamera ?: return@registerForActivityResult
            pendingCamera = null
            if (granted) {
                show(target)
            } else {
                notice = getString(R.string.permission_needed)
                permissionDenied = true
                show(Screen.WELCOME)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.load(this)
        store = LocalStore(this)
        settings = store.settings()
        notice = store.notice
        onBackPressedDispatcher.addCallback(this) {
            when {
                screen == Screen.WELCOME -> finish()
                screen == Screen.MONITOR && monitor != null -> confirmStop()
                else -> show(Screen.WELCOME)
            }
        }
        show(Screen.WELCOME)
    }

    override fun onStart() {
        super.onStart()
        if (screen in CAMERA_SCREENS && source == null && preview != null) openCamera()
        if (screen == Screen.MONITOR || screen == Screen.DEMO) schedule()
    }

    /**
     * Dark mode, font scale, locale or rotation outside camera screens: re-render in place.
     * Recreating the activity would end a running meal session.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Palette.load(this)
        if (screen in CAMERA_SCREENS) {
            preview = null
            closeCamera()
        }
        show(screen)
    }

    private val monitoring: Boolean
        get() = screen == Screen.MONITOR && meal != null

    /**
     * Holding volume-down pauses the meal without looking at the phone. A short press still
     * lowers the volume; the key is tracked so the long press can take it over.
     */
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && monitoring) {
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && monitoring) {
            if (meal?.active == true) togglePause()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && monitoring) {
            if (event.isTracking && !event.isCanceled) {
                getSystemService(AudioManager::class.java)
                    ?.adjustSuggestedStreamVolume(
                        AudioManager.ADJUST_LOWER,
                        AudioManager.USE_DEFAULT_STREAM_TYPE,
                        AudioManager.FLAG_SHOW_UI,
                    )
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun confirmStop() {
        AlertDialog
            .Builder(this)
            .setMessage(R.string.stop_confirm)
            .setPositiveButton(R.string.stop) { _, _ -> show(Screen.WELCOME) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Backgrounding silences immediately and releases the camera; the user resumes explicitly. */
    override fun onStop() {
        meal?.let { current ->
            val now = clock()
            speaker.play(current.suspend(now), settings.volume)
            render(current.tick(now)) // show "Paused" / "Resume" when the user returns
        }
        silence()
        closeCamera()
        handler.removeCallbacks(ticker)
        super.onStop()
    }

    override fun onDestroy() {
        endSession()
        closeRecorder()
        speaker.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    internal fun show(target: Screen) {
        if (target == Screen.MONITOR && monitor == null) return show(Screen.WELCOME)
        if (target in CAMERA_SCREENS && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingCamera = target
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                AlertDialog
                    .Builder(this)
                    .setMessage(R.string.permission_rationale)
                    .setPositiveButton(R.string.continue_label) { _, _ -> permission.launch(Manifest.permission.CAMERA) }
                    .setNegativeButton(R.string.cancel) { _, _ -> pendingCamera = null }
                    .show()
            } else {
                permission.launch(Manifest.permission.CAMERA)
            }
            return
        }
        if (screen == Screen.MONITOR && target != Screen.MONITOR) endSession()
        if (target !in CAMERA_SCREENS) closeCamera()
        handler.removeCallbacks(ticker)
        taps.clear()
        screen = target
        val keepAwake = target in CAMERA_SCREENS || target == Screen.DEMO
        // A propped-up phone must not rotate mid-setup or mid-meal: that would change geometry.
        requestedOrientation =
            if (target in CAMERA_SCREENS) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (keepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        when (target) {
            Screen.WELCOME -> page(welcome())
            Screen.SETTINGS -> page(settingsPage())
            Screen.DATA -> page(dataPage())
            Screen.ABOUT -> page(aboutPage())
            Screen.DEMO -> startDemo()
            else -> {
                if (preview == null) cameraLayout()
                panel!!.removeAllViews()
                stage!!.onTap = null
                stage!!.onDrag = null
                stage!!.taps = emptyList()
                stage!!.results = emptyList()
                stage!!.warning = VisualMode.OFF
                stage!!.table = settings.table
                stage!!.seats = settings.seats
                stage!!.skeleton = settings.debug || target != Screen.MONITOR
                when (target) {
                    Screen.POSITION -> positionPanel()
                    Screen.TABLE -> tablePanel()
                    Screen.SEATS -> seatsPanel()
                    else -> monitorPanel()
                }
                stage!!.refresh()
                if (source == null) openCamera()
            }
        }
    }

    private fun page(content: View) {
        stage = null
        preview = null
        panel = null
        setContentView(fadeIn(insetAware(ScrollView(this).apply { setBackgroundColor(Palette.surface) }.also { it.addView(content) })))
    }

    private fun welcome(): View =
        column().apply {
            notice?.let {
                val warning = card(label(it, color = Palette.red))
                if (permissionDenied) {
                    warning.addView(label(getString(R.string.permission_rationale), 15f, color = Palette.muted))
                    warning.addView(action(getString(R.string.open_settings)) { openAppSettings() })
                }
                addView(warning)
            }
            addView(title(getString(R.string.app_name)))
            lastSummary?.let { addView(summaryCard(it)) }
            addView(label(getString(R.string.welcome_intro), 19f))
            addView(
                card(
                    label(getString(R.string.welcome_privacy)),
                    label(getString(R.string.welcome_placement)),
                    label(getString(R.string.welcome_limits), color = Palette.muted),
                ),
            )
            if (settings.table != null) {
                val lens =
                    cameraIds().firstOrNull { it.id == settings.camera }?.let { getString(it.label) }
                        ?: getString(R.string.camera_automatic)
                addView(label(getString(R.string.ready_line, settings.people, settings.seats.size, settings.model.name, lens), bold = true))
                addView(action(getString(R.string.start_monitoring), primary = true) { startMonitoring() })
                addView(action(getString(R.string.recalibrate)) { begin(Screen.POSITION) })
            } else {
                addView(label(getString(R.string.needs_setup), color = Palette.muted))
                addView(action(getString(R.string.setup_camera), primary = true) { begin(Screen.POSITION) })
            }
            addView(action(getString(R.string.try_demo)) { begin(Screen.DEMO) })
            addView(action(getString(R.string.settings)) { begin(Screen.SETTINGS) })
            addView(action(getString(R.string.local_data)) { begin(Screen.DATA) })
            addView(action(getString(R.string.about)) { begin(Screen.ABOUT) })
        }

    /** Positive end-of-meal card: time, reminders, the calm record, per-seat counts. */
    private fun summaryCard(summary: MealSummary): View =
        card(
            label(getString(R.string.summary_title), 19f, bold = true, color = Palette.green),
            label(
                getString(
                    R.string.summary_line,
                    clockText(summary.activeSeconds),
                    summary.reminders,
                    clockText(summary.longestCalmSeconds),
                ),
            ),
            label(
                if (summary.remindersBySeat.isEmpty()) {
                    getString(R.string.summary_none)
                } else {
                    summary.remindersBySeat.entries.joinToString(" · ") { (seat, count) ->
                        getString(R.string.summary_seat, getString(SEAT_COLOURS.getOrElse(seat - 1) { R.string.seat_colour_1 }), count)
                    }
                },
                15f,
                color = Palette.muted,
            ),
        )

    private fun begin(target: Screen) {
        notice = null
        permissionDenied = false
        show(target)
    }

    /** After a refusal Android no longer asks; only the app's system settings can grant it. */
    private fun openAppSettings() {
        startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun settingsPage(): View =
        column().apply {
            addView(title(getString(R.string.settings_title)))
            addView(label(getString(R.string.settings_help), color = Palette.muted))
            for (option in settingsOptions(cameraIds())) {
                val name = getString(option.label)
                val value = label(option.display(this@MainActivity, settings), 18f, bold = true)

                fun change(delta: Int) {
                    settings = option.change(settings, delta)
                    store.save(settings)
                    value.text = option.display(this@MainActivity, settings)
                }
                addView(
                    card(
                        label(name, bold = true),
                        label(getString(option.explanation), 14f, color = Palette.muted),
                        row(
                            action("−") { change(-1) }.apply { contentDescription = getString(R.string.decrease, name) },
                            value.apply { textAlignment = View.TEXT_ALIGNMENT_CENTER },
                            action("+") { change(1) }.apply { contentDescription = getString(R.string.increase, name) },
                        ),
                    ),
                )
            }
            addView(
                action(getString(R.string.test_sound)) {
                    speaker.prepare()
                    speaker.play(Sound.BEEP, settings.volume)
                },
            )
            addView(action(getString(R.string.back), primary = true) { show(Screen.WELCOME) })
        }

    private fun cameraLayout(landscape: Boolean = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        val frame = FrameLayout(this).apply { setBackgroundColor(Palette.stageBackground) }
        val view = PreviewView(this)
        frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val overlay = StageView(this).also { it.clock = clock }
        // The loupe magnifies a still of the preview taken when a finger lands on it.
        overlay.snapshot = { view.bitmap }
        frame.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        splitLayout(frame, landscape)
        preview = view
        stage = overlay
    }

    private fun splitLayout(
        frame: FrameLayout,
        landscape: Boolean,
    ) {
        val controls = column(16)
        val root =
            LinearLayout(this).apply {
                orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                setBackgroundColor(Palette.surface)
                addView(
                    frame,
                    if (landscape) {
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            3f,
                        )
                    } else {
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f)
                    },
                )
                addView(
                    ScrollView(this@MainActivity).apply { addView(controls) },
                    if (landscape) {
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            2f,
                        )
                    } else {
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f)
                    },
                )
            }
        panel = controls
        setContentView(fadeIn(insetAware(root)))
    }

    /** Screens change with a short 180 ms fade over the same background, never a hard cut. */
    private fun fadeIn(root: View): View =
        root.apply {
            alpha = 0f
            animate().alpha(1f).setDuration(FADE_MS).start()
        }

    /** Keeps content clear of system bars and cutouts (edge-to-edge is enforced from API 35). */
    private fun insetAware(root: View): View =
        root.apply {
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

    /** Vision §19 as a gate: the table is marked once everyone's arms are reliably visible. */
    private fun positionPanel() {
        chooseWidestLens()
        visibilityCheck = VisibilityCheck(settings.people)
        panel!!.apply {
            addView(title(getString(R.string.position_title)))
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.placement)
                    adjustViewBounds = true
                    contentDescription = getString(R.string.placement_image)
                },
            )
            addView(label(getString(R.string.position_help)))
            addView(peopleStepper())
            lensChips()?.let(::addView)
            status = label(getString(R.string.people_detected, 0), bold = true).also(::addView)
            advice = label(getString(R.string.visibility_tips), color = Palette.muted).also(::addView)
            addView(
                action(getString(R.string.mark_table), primary = true) {
                    if (visibilityCheck?.result()?.passed == true) show(Screen.TABLE)
                }.apply {
                    tag = CONTINUE_TAG
                    isEnabled = false
                    alpha = 0.5f
                },
            )
            addView(action(getString(R.string.continue_anyway)) { show(Screen.TABLE) })
            addView(action(getString(R.string.back)) { show(Screen.WELCOME) })
        }
    }

    /** People at the table, right where the check needs it; a change restarts the check. */
    private fun peopleStepper(): View {
        val name = getString(R.string.option_people)

        fun change(delta: Int) {
            val people = (settings.people + delta).coerceIn(1, 4)
            if (people == settings.people) return
            updateSettings(settings.copy(people = people, seats = if (settings.seats.size == people) settings.seats else emptyList()))
            show(Screen.POSITION)
        }
        return row(
            action("−") { change(-1) }.apply { contentDescription = getString(R.string.decrease, name) },
            label(getString(R.string.people_count, settings.people), 18f, bold = true).apply { textAlignment = View.TEXT_ALIGNMENT_CENTER },
            action("+") { change(1) }.apply { contentDescription = getString(R.string.increase, name) },
        )
    }

    /** One chip per rear lens (Wide / Main / Tele); switching lens invalidates the table outline. */
    private fun lensChips(): View? {
        val lenses = cameraIds()
        if (lenses.size < 2) return null
        return row(
            *lenses
                .map { lens ->
                    action(getString(lens.label), primary = lens.id == settings.camera) { selectLens(lens.id) }.apply {
                        contentDescription = getString(R.string.lens_choice, getString(lens.label))
                    }
                }.toTypedArray(),
        )
    }

    private fun selectLens(id: String) {
        if (id == settings.camera) return
        updateSettings(settings.copy(camera = id, table = null, seats = emptyList(), calibrationAspect = 0.0, calibrationRotation = -1))
        closeCamera()
        show(Screen.POSITION)
    }

    /** A new setup starts on the widest rear lens: from a corner it sees the most of the table. */
    private fun chooseWidestLens() {
        if (settings.table != null || settings.camera.isNotEmpty()) return
        val wide = cameraIds().firstOrNull { it.label == R.string.lens_wide } ?: return
        updateSettings(settings.copy(camera = wide.id))
        closeCamera()
    }

    private fun visibilityAdvice(result: VisibilityCheck.Result): String =
        when {
            result.passed -> getString(R.string.visibility_passed)
            fps > 0 && fps < SLOW_FPS && result.seconds >= 3 -> getString(R.string.visibility_slow, fps)
            else ->
                when (result.reason) {
                    VisibilityCheck.Reason.NOBODY -> getString(R.string.visibility_nobody)
                    VisibilityCheck.Reason.TOO_FEW -> getString(R.string.visibility_too_few, result.detected, settings.people)
                    VisibilityCheck.Reason.TOO_MANY -> getString(R.string.visibility_too_many, result.detected, settings.people)
                    VisibilityCheck.Reason.ARMS_HIDDEN ->
                        getString(
                            R.string.visibility_hidden,
                            getString(
                                when (result.hiddenSide) {
                                    VisibilityCheck.Side.LEFT -> R.string.side_left
                                    VisibilityCheck.Side.RIGHT -> R.string.side_right
                                    else -> R.string.side_middle
                                },
                            ),
                        )
                    else -> getString(R.string.visibility_tips)
                }
        }

    private fun refreshVisibility(poses: List<Pose>) {
        val check = visibilityCheck ?: return
        check.add(poses, lastFrame)
        val result = check.result()
        status?.text =
            getString(
                R.string.visibility_status,
                result.detected,
                settings.people,
                (result.armsVisible * 100).toInt(),
                fps,
                result.seconds,
            )
        advice?.update(visibilityAdvice(result))
        panel?.findViewWithTag<View>(CONTINUE_TAG)?.apply {
            isEnabled = result.passed
            alpha = if (result.passed) 1f else 0.5f
        }
    }

    private fun tablePanel() {
        stage!!.table = null
        stage!!.seats = emptyList()
        panel!!.apply {
            addView(title(getString(R.string.table_title)))
            addView(label(getString(R.string.table_help)))
            status = label(getString(R.string.table_progress, 0), bold = true).also(::addView)
            addView(
                row(
                    action(getString(R.string.undo)) { edit { taps.removeLastOrNull() } },
                    action(getString(R.string.reset)) { edit { taps.clear() } },
                ),
            )
            addView(action(getString(R.string.save_table), primary = true) { saveTable() })
            addView(action(getString(R.string.back)) { show(Screen.WELCOME) })
        }
        tapInput { if (taps.size < 4) taps += it }
    }

    private fun tapInput(onPoint: (Point) -> Unit) {
        stage!!.onTap = { point ->
            // Until the first frame the image-to-view mapping is unknown; a tap cannot be placed.
            if (!cameraReady) status?.text = getString(R.string.waiting_for_image) else edit { onPoint(point) }
        }
        stage!!.onRejectedTap = { status?.text = getString(R.string.tap_inside_image) }
        stage!!.onDrag = { index, point -> edit { taps[index] = point } }
    }

    private fun edit(change: () -> Unit) {
        change()
        stage!!.taps = taps.toList()
        stage!!.seats = if (screen == Screen.SEATS) seats.toList() else emptyList()
        status?.text =
            if (screen == Screen.TABLE) {
                getString(R.string.table_progress, taps.size)
            } else {
                getString(R.string.seats_progress, seats.size, settings.people, taps.size)
            }
        stage!!.refresh()
    }

    private fun saveTable() {
        // Calibration is stored in image space together with the geometry it depends on.
        val geometry = frameInfo
        if (geometry == null) {
            status?.text = getString(R.string.waiting_for_image)
            return
        }
        val table =
            try {
                Polygon(taps.toList())
            } catch (error: IllegalArgumentException) {
                status?.text = error.message
                return
            }
        updateSettings(
            settings.copy(table = table, seats = emptyList(), calibrationAspect = geometry.aspect, calibrationRotation = geometry.rotation),
        )
        show(Screen.SEATS)
    }

    private fun seatsPanel() {
        seats.clear()
        panel!!.apply {
            addView(title(getString(R.string.seats_title)))
            addView(label(getString(R.string.seats_help, settings.people)))
            status = label(getString(R.string.seats_progress, 0, settings.people, 0), bold = true).also(::addView)
            addView(
                row(
                    action(getString(R.string.undo)) { edit { taps.removeLastOrNull() } },
                    action(getString(R.string.add_seat)) { addSeat() },
                ),
            )
            addView(
                row(
                    action(getString(R.string.suggest_seats)) { suggestSeats() },
                    action(getString(R.string.clear_seats)) { edit { seats.clear() } },
                ),
            )
            seatCheck = label("", 15f, color = Palette.muted).also(::addView)
            addView(action(getString(R.string.finish_setup), primary = true) { finishSetup() })
            addView(action(getString(R.string.back)) { show(Screen.WELCOME) })
        }
        tapInput { if (taps.size < 4 && seats.size < settings.people) taps += it }
    }

    /** One region per person, proposed from the table edges; the live count shows whether it fits. */
    private fun suggestSeats() {
        val table = settings.table ?: return
        val proposed = SeatProposal.propose(table, settings.people)
        if (proposed.size < settings.people) {
            status?.text = getString(R.string.seats_suggest_failed)
            return
        }
        edit {
            seats.clear()
            seats += proposed
            taps.clear()
        }
        seatCheck?.text = getString(R.string.seats_suggested)
    }

    /** Seats only assign people inside them: count who currently falls in exactly one. */
    private fun refreshSeatCheck(poses: List<Pose>) {
        if (seats.isEmpty()) return
        val inside = poses.count { pose -> pose.center()?.let { c -> seats.count { it.contains(c) } == 1 } == true }
        seatCheck?.update(getString(R.string.seats_inside, inside, settings.people))
    }

    private fun addSeat() {
        if (seats.size >= settings.people) {
            status?.text = getString(R.string.seats_full)
            return
        }
        val seat =
            try {
                Polygon(taps.toList())
            } catch (error: IllegalArgumentException) {
                status?.text = error.message
                return
            }
        if (seats.any { it.overlaps(seat) }) {
            taps.clear()
            stage!!.taps = emptyList()
            stage!!.refresh()
            status?.text = getString(R.string.seat_overlap)
            return
        }
        edit {
            seats += seat
            taps.clear()
        }
    }

    private fun finishSetup() {
        // Seat regions assign people only inside them: partial regions would leave people unwatched.
        if (seats.isNotEmpty() && seats.size != settings.people) {
            status?.text = getString(R.string.seats_incomplete, settings.people)
            return
        }
        updateSettings(settings.copy(seats = seats.toList()))
        show(Screen.WELCOME)
    }

    private fun updateSettings(next: Settings) {
        settings = next
        store.save(next)
    }

    private fun startMonitoring() {
        notice = null
        meal = MonitorSession(settings, UUID.randomUUID().toString(), clock())
        lastSummary = null
        diagnosticsOpen = false // adult tools start collapsed in every session
        speaker.prepare()
        show(Screen.MONITOR)
    }

    private fun monitorPanel() {
        panel!!.apply {
            addView(title(getString(R.string.monitor_title)))
            status = label("", 19f, bold = true).also(::addView)
            addView(action(getString(R.string.recalibrate)) { begin(Screen.POSITION) }.apply { tag = RECALIBRATE_TAG })
            // Pause stays above the seat cards: always visible, one tap, however many seats.
            addView(action(getString(R.string.pause), primary = true) { togglePause() }.apply { tag = PAUSE_TAG })
            // Stop and the adult toggle sit next to Pause; the seat cards follow below.
            addView(
                row(
                    action(getString(R.string.stop)) { show(Screen.WELCOME) },
                    action(getString(R.string.show_diagnostics)) { toggleDiagnostics() }.apply { tag = DIAGNOSTICS_TAG },
                ),
            )
            banner =
                card(
                    label("", 15f, bold = true, color = Palette.amber),
                    action(getString(R.string.use_lite)) { switchToLite() },
                ).apply { visibility = View.GONE }.also(::addView)
            adult = adultPanel().also(::addView)
            seatCards = column(0).also(::addView)
            sessionLine = label("", 15f, color = Palette.muted).also(::addView)
            addView(label(getString(R.string.volume_pause_hint), 14f, color = Palette.muted))
        }
        tick()
    }

    private fun togglePause() {
        val current = meal ?: return
        val sound = current.togglePause(clock())
        if (!current.active) silence()
        speaker.play(sound, settings.volume)
        tick()
    }

    private fun toggleDiagnostics() {
        diagnosticsOpen = !diagnosticsOpen
        tick()
    }

    /** Renders one [MonitorUiState]; all decisions were made by [MonitorSession]. */
    private fun render(state: MonitorUiState) {
        val current = meal ?: return
        if (screen != Screen.MONITOR || panel == null) return
        panel!!.findViewWithTag<TextView>(PAUSE_TAG)?.update(getString(if (state.paused) R.string.resume else R.string.pause))
        panel!!.findViewWithTag<TextView>(DIAGNOSTICS_TAG)?.update(
            getString(if (diagnosticsOpen) R.string.hide_diagnostics else R.string.show_diagnostics),
        )
        panel!!.findViewWithTag<View>(RECALIBRATE_TAG)?.visibility =
            if (state.status == Status.NOBODY_FOR_A_WHILE) View.VISIBLE else View.GONE
        status?.update(
            when (state.status) {
                Status.PAUSED -> getString(R.string.paused)
                Status.GRACE -> getString(R.string.grace, state.countdown)
                Status.TOO_SLOW -> getString(R.string.too_slow, fps)
                Status.RESTING -> getString(R.string.snoozed, state.countdown)
                Status.NOBODY_FOR_A_WHILE -> getString(R.string.nobody_for_a_while)
                Status.WAITING -> getString(R.string.waiting)
                Status.SLOW -> getString(R.string.slow_processing, fps)
                Status.REMINDING -> getString(R.string.warning_text)
                Status.WATCHING -> getString(R.string.watching)
            },
        )
        renderSeats(state.seats)
        renderBanner(state)
        sessionLine?.update(getString(R.string.session_line, clockText(state.activeSeconds), state.reminders))
        adult?.visibility = if (diagnosticsOpen) View.VISIBLE else View.GONE
        if (diagnosticsOpen) diagnosticsText?.update(diagnostics(current.monitor))
        adult?.findViewWithTag<TextView>(TRAINING_TAG)?.update(getString(if (training) R.string.training_on else R.string.training_off))
        adult?.findViewWithTag<TextView>(SEAT_TAG)?.update(getString(R.string.training_seat, trainingSeat))
        adult?.findViewWithTag<View>(LABELS_TAG)?.visibility = if (training) View.VISIBLE else View.GONE
    }

    /**
     * A warm or slow phone gets an explanation and a one-tap switch to the lighter model;
     * only offered while the Full model runs.
     */
    private fun renderBanner(state: MonitorUiState) {
        val card = banner ?: return
        val warm = thermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE
        val slow = state.status == Status.SLOW || state.status == Status.TOO_SLOW
        val show = settings.model == PoseModel.FULL && !state.paused && (warm || slow)
        card.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            (
                card.getChildAt(
                    0,
                ) as TextView
            ).update(if (warm) getString(R.string.banner_warm) else getString(R.string.banner_slow, fps))
        }
    }

    /** Restarts inference with the Lite model; the meal and its calibration continue. */
    private fun switchToLite() {
        updateSettings(settings.copy(model = PoseModel.LITE))
        closeCamera()
        openCamera()
        tick()
    }

    private fun clockText(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)

    /** Adult-only tools, collapsed by default: diagnostics, feedback, explicit training. */
    private fun adultPanel(): LinearLayout =
        column(0).apply {
            diagnosticsText = label("", 14f, color = Palette.muted).also(::addView)
            addView(
                row(
                    action(getString(R.string.false_alarm)) { feedback("FALSE_ALARM") },
                    action(getString(R.string.missed_violation)) { feedback("MISSED_VIOLATION") },
                ),
            )
            addView(action(getString(R.string.training_off)) { toggleTraining() }.apply { tag = TRAINING_TAG })
            addView(
                column(0).apply {
                    tag = LABELS_TAG
                    addView(label(getString(R.string.training_help), 14f, color = Palette.muted))
                    addView(
                        action(getString(R.string.training_seat, 1)) {
                            trainingSeat = trainingSeat % settings.people + 1
                            tick()
                        }.apply { tag = SEAT_TAG },
                    )
                    addView(
                        row(
                            action(getString(R.string.label_normal)) { labelEvent("NORMAL") },
                            action(getString(R.string.label_left)) { labelEvent("LEFT") },
                        ),
                    )
                    addView(
                        row(
                            action(getString(R.string.label_right)) { labelEvent("RIGHT") },
                            action(getString(R.string.label_both)) { labelEvent("BOTH") },
                        ),
                    )
                },
            )
            trainingStatus = label("", 15f, bold = true).also(::addView)
        }

    /** Training mode records this session's landmarks (never images) into a local log. */
    private fun toggleTraining() {
        training = !training
        if (training) {
            recorder =
                try {
                    SessionRecorder(sessionsDir, meal?.id ?: UUID.randomUUID().toString(), BuildConfig.VERSION_NAME, settings)
                } catch (error: IllegalStateException) {
                    training = false
                    trainingStatus?.text = getString(R.string.storage_failed, error.message)
                    null
                }
        } else {
            closeRecorder()
        }
        tick()
    }

    private fun closeRecorder() {
        recorder?.close()
        recorder = null
    }

    private fun feedback(label: String) {
        val current = meal ?: return
        val now = clock()
        if (label == "FALSE_ALARM") {
            // The adult corrected a wrong reminder: stop it now and give the table a short rest.
            silence()
            speaker.play(current.falseAlarm(now), settings.volume)
        } else {
            current.missedViolation()
        }
        recorder?.label(SessionLog.label(now, label, 0))
        persist(listOf(sampleRecord(current.id, now, label, current.monitor.results, 0)), label)
        tick()
    }

    private fun labelEvent(label: String) {
        val log = recorder ?: return
        log.label(SessionLog.label(clock(), label, trainingSeat))
        trainingStatus?.text = getString(if (log.full) R.string.log_full else R.string.labelled, label, trainingSeat)
    }

    private fun persist(
        records: List<JSONObject>,
        label: String,
    ): Boolean =
        try {
            store.addAll(records)
            trainingStatus?.text = getString(R.string.feedback_saved, "$label ×${records.size}")
            true
        } catch (error: IllegalStateException) {
            trainingStatus?.text = getString(R.string.storage_failed, error.message)
            false
        }

    private fun dataPage(): View =
        column().apply {
            addView(title(getString(R.string.data_title)))
            addView(label(getString(R.string.data_help)))
            val count = store.records().length()
            store.notice?.let { addView(card(label(it, color = Palette.red))) }
            notice?.let { addView(label(it, bold = true)) }
            addView(label(getString(R.string.data_count, count), 19f, bold = true))
            addView(action(getString(R.string.export)) { exporter.launch("fork-around-samples.json") })
            val logs = SessionFiles.list(sessionsDir)
            addView(label(getString(R.string.session_logs, logs.size, SessionFiles.totalBytes(sessionsDir) / 1024), 19f, bold = true))
            for (log in logs) {
                addView(
                    card(
                        label(getString(R.string.session_entry, log.name, log.length() / 1024)),
                        row(
                            action(getString(R.string.export_log)) {
                                exportLog = log
                                logExporter.launch(log.name)
                            },
                            action(getString(R.string.delete_log)) {
                                confirm(R.string.delete_log_confirm) {
                                    log.delete()
                                    notice = getString(R.string.deleted_log)
                                }
                            },
                        ),
                    ),
                )
            }
            addView(
                action(getString(R.string.delete)) {
                    confirm(R.string.delete_confirm) {
                        store.delete()
                        SessionFiles.deleteAll(sessionsDir)
                        notice = getString(R.string.deleted)
                    }
                },
            )
            addView(action(getString(R.string.back), primary = true) { begin(Screen.WELCOME) })
        }

    private fun aboutPage(): View =
        column().apply {
            addView(title(getString(R.string.about)))
            addView(label(getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), 19f, bold = true))
            addView(card(label(getString(R.string.about_license)), label(getString(R.string.welcome_privacy))))
            addView(card(label(getString(R.string.about_notices), 15f)))
            addView(label(getString(R.string.about_source)).apply { autoLinkMask = Linkify.WEB_URLS })
            addView(label(getString(R.string.welcome_limits), color = Palette.muted))
            addView(action(getString(R.string.back), primary = true) { show(Screen.WELCOME) })
        }

    private fun confirm(
        message: Int,
        action: () -> Unit,
    ) {
        AlertDialog
            .Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.delete) { _, _ ->
                action()
                show(Screen.DATA)
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportTo(
        uri: Uri,
        write: (OutputStream) -> Unit,
    ) {
        notice =
            try {
                contentResolver.openOutputStream(uri, "wt")!!.use(write)
                getString(R.string.exported)
            } catch (error: Exception) {
                getString(R.string.export_failed, error.message)
            }
        show(Screen.DATA)
    }

    /**
     * One card per seat in its identity colour, with a words-and-colour chip per elbow.
     * Cards are built once and updated in place only when a state changes: rebuilding views
     * ten times a second would waste the main thread and flood accessibility services.
     */
    private fun renderSeats(results: List<SeatResult>) {
        val container = seatCards ?: return
        val shown = results.map { Triple(it.pose != null, it.left.state, it.right.state) }
        if (container.tag == shown) return
        container.tag = shown
        container.removeAllViews()
        for (seat in results) {
            val name = label(getString(R.string.seat_name, seat.seat), 17f, bold = true, color = Palette.seat(seat.seat))
            val card =
                if (seat.pose == null) {
                    card(name, label(getString(R.string.nobody_detected), 15f, color = Palette.muted))
                } else {
                    card(
                        name,
                        row(
                            chip(getString(R.string.chip_left, word(seat.left.state)), seat.left.state),
                            chip(getString(R.string.chip_right, word(seat.right.state)), seat.right.state),
                        ),
                    )
                }
            card.contentDescription =
                if (seat.pose == null) {
                    getString(R.string.seat_empty, seat.seat)
                } else {
                    getString(R.string.seat_status, seat.seat, word(seat.left.state), word(seat.right.state))
                }
            container.addView(card)
        }
    }

    private fun word(state: ElbowState): String =
        getString(
            when (state) {
                ElbowState.UNKNOWN -> R.string.state_unknown
                ElbowState.CLEAR -> R.string.state_clear
                ElbowState.SUSPECT -> R.string.state_suspect
                ElbowState.VIOLATION -> R.string.state_violation
            },
        )

    private fun diagnostics(active: Monitor): String {
        val confidence = if (active.confidenceCount == 0) 0.0 else active.confidenceTotal / active.confidenceCount
        val sorted = latencies.sorted()

        fun percentile(p: Int) = if (sorted.isEmpty()) 0L else sorted[(sorted.size - 1) * p / 100]
        val header =
            getString(
                R.string.diagnostics,
                fps,
                percentile(50),
                percentile(95),
                source?.dropped ?: 0L,
                settings.model.name,
                thermal(),
                getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0,
                active.violations,
                confidence,
            )
        val arms =
            active.results.flatMap { seat ->
                listOf(getString(R.string.left) to seat.left, getString(R.string.right) to seat.right).map { (side, arm) ->
                    armLine(seat.seat, side, arm)
                }
            }
        return (listOf(header) + arms).joinToString("\n")
    }

    private fun armLine(
        seat: Int,
        side: String,
        arm: ArmResult,
    ): String {
        val none = getString(R.string.none)

        fun Double?.fmt(pattern: String) = this?.let { String.format(resources.configuration.locales[0], pattern, it) } ?: none
        return getString(
            R.string.arm_score,
            seat,
            side,
            arm.score.fmt("%.2f"),
            arm.features?.elbowDistance.fmt("%.2f"),
            arm.features?.elbowAngle.fmt("%.0f°"),
        )
    }

    private fun startDemo() {
        val frame = FrameLayout(this)
        val overlay = StageView(this).also { it.clock = clock }.apply { synthetic = true }
        frame.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        splitLayout(frame, resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        stage = overlay
        preview = null
        overlay.table = SyntheticDemo.table
        panel!!.apply {
            addView(title(getString(R.string.demo_title)))
            addView(label(getString(R.string.demo_banner), bold = true, color = Palette.amber))
            addView(label(getString(R.string.demo_help)))
            seatCards = column(0).also(::addView)
            addView(action(getString(R.string.restart), primary = true) { restartDemo() })
            addView(action(getString(R.string.back)) { show(Screen.WELCOME) })
        }
        restartDemo()
    }

    private fun restartDemo() {
        demoStart = clock()
        demoMonitor = Monitor(SyntheticDemo.settings).also { it.start(demoStart) }
        handler.removeCallbacks(ticker)
        tick()
    }

    private fun schedule() {
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, if (screen == Screen.DEMO) DEMO_FRAME_MS else TICK_MS)
    }

    /** Watchdog: runs even without camera callbacks, so stale evidence expires and silences. */
    private fun tick() {
        val now = clock()
        val view = stage ?: return
        if (screen == Screen.DEMO) {
            val demo = demoMonitor ?: return
            val aspect = if (view.width > 0 && view.height > 0) view.width.toDouble() / view.height else 1.0
            demo.frame(SyntheticDemo.poses(now - demoStart), now, now, aspect)
            // The demo previews the configured visual warning but never sounds or stores anything.
            view.warning = if (demo.tick(now)) settings.visual else VisualMode.OFF
            view.results = demo.results
            view.poses = emptyList()
            renderSeats(demo.results)
            view.reminder = if (view.warning != VisualMode.OFF) MonitorSession.firstViolation(demo.results) else null
            view.refresh()
            schedule()
            return
        }
        val current = meal ?: return
        val state = current.tick(now)
        if (current.monitor.calibrationInvalid) {
            notice = getString(R.string.calibration_changed)
            show(Screen.WELCOME)
            return
        }
        view.results = state.seats
        view.warning = if (state.warning) settings.visual else VisualMode.OFF
        view.reminder = state.reminder
        view.thanks = state.thanks
        view.dimmed = state.paused
        speaker.play(state.sound, settings.volume)
        render(state)
        view.refresh()
        schedule()
    }

    private fun onFrame(
        poses: List<Pose>,
        time: Long,
        info: FrameInfo,
    ) {
        val now = clock()
        if (lastFrame in 1 until time) fps = if (fps == 0.0) 1000.0 / (time - lastFrame) else fps * 0.8 + 200.0 / (time - lastFrame)
        lastFrame = time
        latencies.addLast(info.latencyMs)
        if (latencies.size > 60) latencies.removeFirst()
        frameInfo = info
        stage?.mapping = source?.mapping
        stage?.poses = poses
        if (screen == Screen.MONITOR) {
            meal?.let { current ->
                // Only frames the monitor accepted are logged, so replay sees exactly what it decided on.
                if (current.frame(poses, time, now, info.aspect, info.rotation)) {
                    recorder?.frame(SessionLog.frame(time, info.aspect, poses, current.monitor.results))
                }
            }
        }
        if (screen == Screen.POSITION) refreshVisibility(poses)
        if (screen == Screen.SEATS) refreshSeatCheck(poses)
        stage?.refresh()
    }

    private fun onCameraError(message: String) {
        meal?.let { speaker.play(it.suspend(clock()), settings.volume) }
        silence()
        closeCamera()
        notice = getString(R.string.camera_error, message)
        show(Screen.WELCOME)
    }

    private fun openCamera() {
        val view = preview ?: return
        source = sourceFactory(view, settings, ::onFrame, ::onCameraError).also { it.start() }
    }

    private fun closeCamera() {
        source?.close()
        source = null
        frameInfo = null
        latencies.clear()
        lastFrame = 0L
        fps = 0.0
    }

    /** Clears every warning from the screen and stops any sound immediately. */
    private fun silence() {
        stage?.warning = VisualMode.OFF
        stage?.reminder = null
        stage?.thanks = false
        stage?.refresh()
        speaker.play(Sound.STOP, settings.volume)
    }

    private fun endSession() {
        val current = meal ?: return
        val now = clock()
        current.suspend(now)
        silence()
        meal = null
        closeRecorder()
        training = false
        val summary = current.summary(now)
        if (summary.activeSeconds > 0) lastSummary = summary
        if (settings.statistics && summary.activeSeconds > 0) {
            persist(
                listOf(
                    sessionRecord(
                        current.id,
                        summary.activeMs,
                        summary.reminders,
                        summary.falseAlarms,
                        summary.missedViolations,
                        summary.meanConfidence,
                    ),
                ),
                "session",
            )
        }
    }

    /** Thermal throttling explains slow processing on a phone that has run for a whole meal. */
    private fun thermal(): String = thermalLabel(thermalStatus())

    private fun backCameras(): List<Lens> =
        try {
            val manager = getSystemService(CameraManager::class.java)
            lensLabels(
                manager.cameraIdList
                    .map { it to manager.getCameraCharacteristics(it) }
                    .filter { (_, info) -> info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                    .associate { (id, info) ->
                        id to (info.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: 0f)
                    },
            )
        } catch (_: Exception) {
            emptyList()
        }

    internal companion object {
        val CAMERA_SCREENS = setOf(Screen.POSITION, Screen.TABLE, Screen.SEATS, Screen.MONITOR)
        const val TICK_MS = 100L
        const val DEMO_FRAME_MS = 66L
        const val FADE_MS = 180L

        /** Below this the setup suggests the Lite model. */
        const val SLOW_FPS = 5.0
        const val PAUSE_TAG = "pause"
        const val DIAGNOSTICS_TAG = "diagnostics"
        const val TRAINING_TAG = "training"
        const val CONTINUE_TAG = "continue"
        const val RECALIBRATE_TAG = "recalibrate"
        val SEAT_COLOURS = listOf(R.string.seat_colour_1, R.string.seat_colour_2, R.string.seat_colour_3, R.string.seat_colour_4)
        const val SEAT_TAG = "seat"
        const val LABELS_TAG = "labels"
    }
}
