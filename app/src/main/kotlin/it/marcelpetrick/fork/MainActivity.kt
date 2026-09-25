// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
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
import it.marcelpetrick.fork.camera.FrameSource
import it.marcelpetrick.fork.demo.SyntheticDemo
import it.marcelpetrick.fork.detection.ArmResult
import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Polygon
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.SeatResult
import it.marcelpetrick.fork.monitoring.AlarmPolicy
import it.marcelpetrick.fork.monitoring.LocalStore
import it.marcelpetrick.fork.monitoring.Monitor
import it.marcelpetrick.fork.monitoring.Settings
import it.marcelpetrick.fork.monitoring.Sound
import it.marcelpetrick.fork.monitoring.VisualMode
import it.marcelpetrick.fork.ui.Palette
import it.marcelpetrick.fork.ui.Speaker
import it.marcelpetrick.fork.ui.StageView
import it.marcelpetrick.fork.ui.ToneSpeaker
import it.marcelpetrick.fork.ui.action
import it.marcelpetrick.fork.ui.card
import it.marcelpetrick.fork.ui.column
import it.marcelpetrick.fork.ui.label
import it.marcelpetrick.fork.ui.row
import it.marcelpetrick.fork.ui.settingsOptions
import it.marcelpetrick.fork.ui.title

typealias SourceFactory = (
    PreviewView,
    Settings,
    (List<Pose>, Long, Double, Long) -> Unit,
    (String) -> Unit,
) -> FrameSource

/** Single-activity native UI. All state lives on the main thread. */
class MainActivity : ComponentActivity() {
    enum class Screen { WELCOME, POSITION, TABLE, SEATS, MONITOR, DEMO, SETTINGS }

    internal lateinit var store: LocalStore
    internal var settings = Settings()
        private set
    internal var screen = Screen.WELCOME
        private set
    internal var notice: String? = null
    internal var sourceFactory: SourceFactory = { view, s, frame, error -> CameraSession(this, this, view, s, frame, error) }
    internal var speaker: Speaker = ToneSpeaker()
    internal var clock: () -> Long = SystemClock::uptimeMillis
    internal var cameraIds: () -> List<String> = ::backCameras
    internal var monitor: Monitor? = null
        private set
    internal var stage: StageView? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = Runnable { tick() }
    private var source: FrameSource? = null
    private var preview: PreviewView? = null
    private var panel: LinearLayout? = null
    private var status: TextView? = null
    private var seatsText: TextView? = null
    private var diagnosticsText: TextView? = null
    private var alarm = AlarmPolicy()
    private val taps = mutableListOf<Point>()
    private val seats = mutableListOf<Polygon>()
    private var lastFrame = 0L
    private var fps = 0.0
    private var latency = 0L
    private var resumedAt = 0L
    private var demoMonitor: Monitor? = null
    private var demoStart = 0L
    private var diagnosticsOpen = false
    private var pendingCamera: Screen? = null

    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val target = pendingCamera ?: return@registerForActivityResult
            pendingCamera = null
            if (granted) {
                show(target)
            } else {
                notice = getString(R.string.permission_needed)
                show(Screen.WELCOME)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LocalStore(this)
        settings = store.settings()
        notice = store.notice
        onBackPressedDispatcher.addCallback(this) {
            if (screen == Screen.WELCOME) finish() else show(Screen.WELCOME)
        }
        show(Screen.WELCOME)
    }

    override fun onStart() {
        super.onStart()
        if (screen in CAMERA_SCREENS && source == null && preview != null) openCamera()
        if (screen == Screen.MONITOR || screen == Screen.DEMO) schedule()
    }

    /** Backgrounding silences immediately and releases the camera; the user resumes explicitly. */
    override fun onStop() {
        monitor?.pause(clock())
        silence()
        closeCamera()
        handler.removeCallbacks(ticker)
        refreshMonitorPanel()
        super.onStop()
    }

    override fun onDestroy() {
        endSession()
        speaker.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    internal fun show(target: Screen) {
        if (target == Screen.MONITOR && monitor == null) return show(Screen.WELCOME)
        if (target in CAMERA_SCREENS && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingCamera = target
            permission.launch(Manifest.permission.CAMERA)
            return
        }
        if (screen == Screen.MONITOR && target != Screen.MONITOR) endSession()
        if (target !in CAMERA_SCREENS) closeCamera()
        handler.removeCallbacks(ticker)
        taps.clear()
        screen = target
        val keepAwake = target in CAMERA_SCREENS || target == Screen.DEMO
        if (keepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        when (target) {
            Screen.WELCOME -> page(welcome())
            Screen.SETTINGS -> page(settingsPage())
            Screen.DEMO -> startDemo()
            else -> {
                if (preview == null) cameraLayout()
                panel!!.removeAllViews()
                stage!!.onTap = null
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
        setContentView(ScrollView(this).apply { setBackgroundColor(Palette.SURFACE) }.also { it.addView(content) })
    }

    private fun welcome(): View =
        column().apply {
            notice?.let { addView(card(label(it, color = Palette.RED))) }
            addView(title(getString(R.string.app_name)))
            addView(label(getString(R.string.welcome_intro), 19f))
            addView(
                card(
                    label(getString(R.string.welcome_privacy)),
                    label(getString(R.string.welcome_placement)),
                    label(getString(R.string.welcome_limits), color = Palette.MUTED),
                ),
            )
            if (settings.table != null) {
                addView(action(getString(R.string.start_monitoring), primary = true) { startMonitoring() })
                addView(action(getString(R.string.recalibrate)) { begin(Screen.POSITION) })
            } else {
                addView(label(getString(R.string.needs_setup), color = Palette.MUTED))
                addView(action(getString(R.string.setup_camera), primary = true) { begin(Screen.POSITION) })
            }
            addView(action(getString(R.string.try_demo)) { begin(Screen.DEMO) })
            addView(action(getString(R.string.settings)) { begin(Screen.SETTINGS) })
        }

    private fun begin(target: Screen) {
        notice = null
        show(target)
    }

    private fun settingsPage(): View =
        column().apply {
            addView(title(getString(R.string.settings_title)))
            addView(label(getString(R.string.settings_help), color = Palette.MUTED))
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
                        label(name, color = Palette.MUTED),
                        row(
                            action("−") { change(-1) }.apply { contentDescription = getString(R.string.decrease, name) },
                            value.apply { textAlignment = View.TEXT_ALIGNMENT_CENTER },
                            action("+") { change(1) }.apply { contentDescription = getString(R.string.increase, name) },
                        ),
                    ),
                )
            }
            addView(action(getString(R.string.back), primary = true) { show(Screen.WELCOME) })
        }

    private fun cameraLayout(landscape: Boolean = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        val frame = FrameLayout(this).apply { setBackgroundColor(Palette.STAGE) }
        val view = PreviewView(this)
        frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val overlay = StageView(this).also { it.clock = clock }
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
                setBackgroundColor(Palette.SURFACE)
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
        setContentView(root)
    }

    private fun positionPanel() {
        panel!!.apply {
            addView(title(getString(R.string.position_title)))
            addView(label(getString(R.string.position_help)))
            status = label(getString(R.string.people_detected, 0), bold = true).also(::addView)
            addView(action(getString(R.string.mark_table), primary = true) { show(Screen.TABLE) })
            addView(action(getString(R.string.back)) { show(Screen.WELCOME) })
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
        stage!!.onTap = { point -> edit { onPoint(point) } }
        stage!!.onRejectedTap = { status?.text = getString(R.string.tap_inside_image) }
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
        val table =
            try {
                Polygon(taps.toList())
            } catch (error: IllegalArgumentException) {
                status?.text = error.message
                return
            }
        val view = stage!!
        val aspect = if (view.width > 0 && view.height > 0) view.width.toDouble() / view.height else 0.0
        updateSettings(settings.copy(table = table, seats = emptyList(), calibrationAspect = aspect))
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
            addView(action(getString(R.string.clear_seats)) { edit { seats.clear() } })
            addView(action(getString(R.string.finish_setup), primary = true) { finishSetup() })
            addView(action(getString(R.string.back)) { show(Screen.WELCOME) })
        }
        tapInput { if (taps.size < 4 && seats.size < settings.people) taps += it }
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
        updateSettings(settings.copy(seats = seats.toList()))
        show(Screen.WELCOME)
    }

    private fun updateSettings(next: Settings) {
        settings = next
        store.save(next)
    }

    private fun startMonitoring() {
        notice = null
        monitor = Monitor(settings).also { it.start(clock()) }
        resumedAt = clock()
        alarm = AlarmPolicy()
        show(Screen.MONITOR)
    }

    private fun monitorPanel() {
        panel!!.apply {
            addView(title(getString(R.string.monitor_title)))
            status = label("", 19f, bold = true).also(::addView)
            seatsText = label("").also(::addView)
            addView(action(getString(R.string.pause), primary = true) { togglePause() }.apply { tag = PAUSE_TAG })
            addView(action(getString(R.string.stop)) { show(Screen.WELCOME) })
            addView(action(getString(R.string.show_diagnostics)) { toggleDiagnostics() }.apply { tag = DIAGNOSTICS_TAG })
            diagnosticsText = label("", 14f, color = Palette.MUTED).also(::addView)
        }
        refreshMonitorPanel()
        schedule()
    }

    private fun togglePause() {
        val active = monitor ?: return
        val now = clock()
        if (active.active) {
            active.pause(now)
            silence()
        } else {
            active.start(now)
            resumedAt = now
        }
        refreshMonitorPanel()
    }

    private fun toggleDiagnostics() {
        diagnosticsOpen = !diagnosticsOpen
        refreshMonitorPanel()
    }

    private fun refreshMonitorPanel() {
        val active = monitor ?: return
        if (screen != Screen.MONITOR || panel == null) return
        val now = clock()
        panel!!.findViewWithTag<TextView>(PAUSE_TAG)?.text = getString(if (active.active) R.string.pause else R.string.resume)
        panel!!.findViewWithTag<TextView>(DIAGNOSTICS_TAG)?.text =
            getString(if (diagnosticsOpen) R.string.hide_diagnostics else R.string.show_diagnostics)
        status?.text =
            when {
                !active.active -> getString(R.string.paused)
                now - resumedAt < settings.graceMs -> getString(R.string.grace, (settings.graceMs - (now - resumedAt) + 999) / 1000)
                active.results.isEmpty() -> getString(R.string.waiting)
                stage?.warning != VisualMode.OFF -> getString(R.string.warning_text)
                else -> getString(R.string.monitor_title)
            }
        seatsText?.text = seatLines(active.results)
        diagnosticsText?.visibility = if (diagnosticsOpen) View.VISIBLE else View.GONE
        diagnosticsText?.text = diagnostics(active)
    }

    private fun seatLines(results: List<SeatResult>): String =
        results.joinToString("\n") { seat ->
            if (seat.pose == null) {
                getString(R.string.seat_empty, seat.seat)
            } else {
                getString(R.string.seat_status, seat.seat, word(seat.left.state), word(seat.right.state))
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
        val header = getString(R.string.diagnostics, fps, latency, settings.model.name, active.violations, confidence)
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
            addView(label(getString(R.string.demo_banner), bold = true, color = Palette.AMBER))
            addView(label(getString(R.string.demo_help)))
            seatsText = label("").also(::addView)
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
            seatsText?.text = seatLines(demo.results)
            view.refresh()
            schedule()
            return
        }
        val active = monitor ?: return
        val alarming = active.tick(now)
        if (active.calibrationInvalid) {
            notice = getString(R.string.calibration_changed)
            show(Screen.WELCOME)
            return
        }
        view.results = active.results
        view.warning = if (alarming) settings.visual else VisualMode.OFF
        speaker.play(alarm.update(alarming, settings.audio, now, settings.repeatMs), settings.volume)
        refreshMonitorPanel()
        view.refresh()
        schedule()
    }

    private fun onFrame(
        poses: List<Pose>,
        time: Long,
        aspect: Double,
        delay: Long,
    ) {
        val now = clock()
        if (lastFrame in 1 until time) fps = if (fps == 0.0) 1000.0 / (time - lastFrame) else fps * 0.8 + 200.0 / (time - lastFrame)
        lastFrame = time
        latency = delay
        stage?.bounds = source?.bounds
        stage?.poses = poses
        if (screen == Screen.MONITOR) monitor?.frame(poses, time, now, aspect)
        if (screen == Screen.POSITION) status?.text = getString(R.string.people_detected, poses.size)
        stage?.refresh()
    }

    private fun onCameraError(message: String) {
        monitor?.pause(clock())
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
        lastFrame = 0L
        fps = 0.0
    }

    private fun silence() {
        stage?.warning = VisualMode.OFF
        stage?.refresh()
        alarm.update(false, settings.audio, clock(), settings.repeatMs)
        speaker.play(Sound.STOP, settings.volume)
    }

    private fun endSession() {
        val active = monitor ?: return
        active.pause(clock())
        silence()
        monitor = null
    }

    private fun backCameras(): List<String> =
        try {
            val manager = getSystemService(CameraManager::class.java)
            manager.cameraIdList.filter {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (_: Exception) {
            emptyList()
        }

    internal companion object {
        val CAMERA_SCREENS = setOf(Screen.POSITION, Screen.TABLE, Screen.SEATS, Screen.MONITOR)
        const val TICK_MS = 100L
        const val DEMO_FRAME_MS = 66L
        const val PAUSE_TAG = "pause"
        const val DIAGNOSTICS_TAG = "diagnostics"
    }
}
