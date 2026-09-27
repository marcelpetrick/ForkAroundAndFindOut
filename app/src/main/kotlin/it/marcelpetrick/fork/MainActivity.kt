// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
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
import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.SeatResult
import it.marcelpetrick.fork.monitoring.LocalStore
import it.marcelpetrick.fork.monitoring.MealSummary
import it.marcelpetrick.fork.monitoring.Monitor
import it.marcelpetrick.fork.monitoring.MonitorSession
import it.marcelpetrick.fork.monitoring.SessionLog
import it.marcelpetrick.fork.monitoring.SessionRecorder
import it.marcelpetrick.fork.monitoring.Settings
import it.marcelpetrick.fork.monitoring.Sound
import it.marcelpetrick.fork.monitoring.VisualMode
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
import it.marcelpetrick.fork.ui.title
import org.json.JSONObject
import java.io.File
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
    enum class Screen { WELCOME, POSITION, TABLE, SEATS, MONITOR, DEMO, SETTINGS, DATA, ABOUT, TEXT }

    internal lateinit var store: LocalStore
    internal var settings = Settings()
        internal set
    internal var screen = Screen.WELCOME
        internal set
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
        internal set
    internal val monitor: Monitor?
        get() = meal?.monitor

    /** Summary of the last finished meal, shown on the welcome screen. */
    internal var lastSummary: MealSummary? = null
        internal set
    internal var stage: StageView? = null
        internal set

    // One object per screen family; each owns its views and state (see the *Screen.kt files).
    private val welcomeScreen = WelcomeScreen()
    private val settingsScreen = SettingsScreen()
    private val setupScreen = SetupScreen()
    private val monitorScreen = MonitorScreen()
    private val dataScreen = DataScreen()
    private val aboutScreen = AboutScreen()

    internal val handler = Handler(Looper.getMainLooper())
    private val ticker = Runnable { tick() }
    internal var source: FrameSource? = null
    internal var preview: PreviewView? = null
    internal var panel: LinearLayout? = null
    internal var status: TextView? = null
    internal var seatCards: LinearLayout? = null
    internal var trainingStatus: TextView? = null

    /** The camera permission was refused: the welcome card offers Android's app settings. */
    internal var permissionDenied = false
    internal var lastFrame = 0L
    internal var fps = 0.0
    internal val latencies = ArrayDeque<Long>()
    internal var frameInfo: FrameInfo? = null

    /** True once a frame (and with it the image geometry and view mapping) has arrived. */
    internal val cameraReady: Boolean
        get() = frameInfo != null && stage?.mapping != null
    private var demoMonitor: Monitor? = null
    private var demoStart = 0L
    private var pendingCamera: Screen? = null
    internal var recorder: SessionRecorder? = null
        internal set
    internal var exportLog: File? = null
    internal val sessionsDir: File
        get() = File(filesDir, "sessions")

    internal val exporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) with(dataScreen) { exportTo(uri) { it.write(store.export().toByteArray()) } }
        }

    internal val logExporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
            val log = exportLog
            exportLog = null
            if (uri != null && log != null) with(dataScreen) { exportTo(uri) { out -> log.inputStream().use { it.copyTo(out) } } }
        }

    internal val permission =
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
                screen == Screen.TEXT -> show(Screen.ABOUT)
                else -> show(Screen.WELCOME)
            }
        }
        show(Screen.WELCOME)
        handleShortcut(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut(intent)
    }

    /**
     * The launcher shortcut "Start dinner" goes straight to monitoring when the table is set
     * up; otherwise it explains what is missing. A meal already running is left alone.
     */
    private fun handleShortcut(intent: Intent?) {
        if (intent?.action != ACTION_START_DINNER || meal != null) return
        intent.action = null // handled once, not again after a configuration change
        if (settings.table == null) {
            notice = getString(R.string.shortcut_needs_setup)
            show(Screen.WELCOME)
        } else {
            startMonitoring()
        }
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

    internal val monitoring: Boolean
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
            if (meal?.active == true) with(monitorScreen) { togglePause() }
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
            with(monitorScreen) { render(current.tick(now)) } // show "Paused" / "Resume" when the user returns
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
            requestCamera(target)
            return
        }
        if (screen == Screen.MONITOR && target != Screen.MONITOR) endSession()
        if (target !in CAMERA_SCREENS) closeCamera()
        handler.removeCallbacks(ticker)
        setupScreen.taps.clear()
        screen = target
        holdStill(target)
        when (target) {
            Screen.WELCOME -> page(with(welcomeScreen) { welcome() })
            Screen.SETTINGS -> page(with(settingsScreen) { settingsPage() })
            Screen.DATA -> page(with(dataScreen) { dataPage() })
            Screen.ABOUT -> page(with(aboutScreen) { aboutPage() })
            Screen.TEXT -> page(with(aboutScreen) { textPage() })
            Screen.DEMO -> startDemo()
            else -> cameraScreen(target)
        }
    }

    /** Android's rationale case explains first; afterwards the system dialog asks. */
    private fun requestCamera(target: Screen) {
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
    }

    /** Camera screens and the demo keep the screen on; a propped-up phone must not rotate mid-meal. */
    private fun holdStill(target: Screen) {
        requestedOrientation =
            if (target in CAMERA_SCREENS) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (target in CAMERA_SCREENS || target == Screen.DEMO) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** The shared camera layout with the panel of one setup step or the monitor. */
    private fun cameraScreen(target: Screen) {
        if (source != null && sourceLimit != poseLimit(target)) closeCamera()
        if (preview == null) cameraLayout()
        panel!!.removeAllViews()
        stage!!.apply {
            onTap = null
            onDrag = null
            taps = emptyList()
            results = emptyList()
            warning = VisualMode.OFF
            table = settings.table
            seats = settings.seats
            skeleton = settings.debug || target != Screen.MONITOR
        }
        when (target) {
            Screen.POSITION -> with(setupScreen) { positionPanel() }
            Screen.TABLE -> with(setupScreen) { tablePanel() }
            Screen.SEATS -> with(setupScreen) { seatsPanel() }
            else -> with(monitorScreen) { monitorPanel() }
        }
        stage!!.refresh()
        if (source == null) openCamera()
    }

    internal fun page(content: View) {
        stage = null
        preview = null
        panel = null
        setContentView(fadeIn(insetAware(ScrollView(this).apply { setBackgroundColor(Palette.surface) }.also { it.addView(content) })))
    }

    internal fun begin(target: Screen) {
        notice = null
        permissionDenied = false
        show(target)
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

    internal fun updateSettings(next: Settings) {
        settings = next
        store.save(next)
    }

    internal fun startMonitoring() {
        notice = null
        meal = MonitorSession(settings, UUID.randomUUID().toString(), clock())
        lastSummary = null
        monitorScreen.diagnosticsOpen = false // adult tools start collapsed in every session
        speaker.prepare(settings.chime)
        show(Screen.MONITOR)
    }

    internal fun closeRecorder() {
        recorder?.close()
        recorder = null
    }

    internal fun persist(
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

    /**
     * One card per seat in its identity colour, with a words-and-colour chip per elbow.
     * Cards are built once and updated in place only when a state changes: rebuilding views
     * ten times a second would waste the main thread and flood accessibility services.
     */
    internal fun renderSeats(results: List<SeatResult>) {
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
    internal fun tick() {
        val view = stage ?: return
        if (screen == Screen.DEMO) demoTick(view) else mealTick(view)
    }

    /** The demo previews the configured visual warning but never sounds or stores anything. */
    private fun demoTick(view: StageView) {
        val now = clock()
        val demo = demoMonitor ?: return
        val aspect = if (view.width > 0 && view.height > 0) view.width.toDouble() / view.height else 1.0
        demo.frame(SyntheticDemo.poses(now - demoStart), now, now, aspect)
        view.warning = if (demo.tick(now)) settings.visual else VisualMode.OFF
        view.results = demo.results
        view.poses = emptyList()
        renderSeats(demo.results)
        view.reminder = if (view.warning != VisualMode.OFF) MonitorSession.firstViolation(demo.results) else null
        view.refresh()
        schedule()
    }

    private fun mealTick(view: StageView) {
        val now = clock()
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
        with(monitorScreen) { render(state) }
        view.refresh()
        schedule()
    }

    internal fun onFrame(
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
        if (screen == Screen.POSITION) with(setupScreen) { refreshVisibility(poses) }
        if (screen == Screen.SEATS) with(setupScreen) { refreshSeatCheck(poses) }
        stage?.refresh()
    }

    private fun onCameraError(message: String) {
        meal?.let { speaker.play(it.suspend(clock()), settings.volume) }
        silence()
        closeCamera()
        notice = getString(R.string.camera_error, message)
        show(Screen.WELCOME)
    }

    /**
     * How many people the pose model looks for. Monitoring needs exactly the configured
     * number; setup looks for up to four, so an extra person at the table is noticed.
     */
    private fun poseLimit(target: Screen) = if (target == Screen.MONITOR) settings.people else MAX_PEOPLE

    /** The pose limit the open source was built with (the engine cannot change it later). */
    private var sourceLimit = 0

    internal fun openCamera() {
        val view = preview ?: return
        sourceLimit = poseLimit(screen)
        source = sourceFactory(view, settings.copy(people = sourceLimit), ::onFrame, ::onCameraError).also { it.start() }
    }

    internal fun closeCamera() {
        source?.close()
        source = null
        frameInfo = null
        latencies.clear()
        lastFrame = 0L
        fps = 0.0
    }

    /** Clears every warning from the screen and stops any sound immediately. */
    internal fun silence() {
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
        monitorScreen.training = false
        val summary = current.summary(now)
        if (summary.activeSeconds > 0) lastSummary = summary
        if (settings.statistics && summary.activeSeconds > 0) {
            persist(
                listOf(
                    sessionRecord(
                        current.id,
                        summary.activeMs,
                        current.monitor.violations, // per-elbow episodes, as stored since 0.5.12
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
    internal fun thermal(): String = thermalLabel(thermalStatus())

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
        const val TEXT_PAGE_CHARS = 40_000
        const val SOURCE_URL = "https://github.com/marcelpetrick/ForkAroundAndFindOut"
        const val MAX_PEOPLE = 4
        const val ACTION_START_DINNER = "it.marcelpetrick.fork.action.START_DINNER"

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
