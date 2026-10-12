// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
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
import it.marcelpetrick.fork.monitoring.LocalStore
import it.marcelpetrick.fork.monitoring.MealSummary
import it.marcelpetrick.fork.monitoring.Monitor
import it.marcelpetrick.fork.monitoring.MonitorSession
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
    internal var cameraIds: () -> List<Lens> = { with(cameraScreen) { backCameras() } }

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
    internal val setupScreen = SetupScreen()
    internal val monitorScreen = MonitorScreen()
    private val demoScreen = DemoScreen()
    private val dataScreen = DataScreen()
    private val aboutScreen = AboutScreen()
    internal val cameraScreen = CameraScreen()

    internal val handler = Handler(Looper.getMainLooper())
    internal val ticker = Runnable { tick() }
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
            with(cameraScreen) { onPermission(granted) }
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
            with(cameraScreen) { requestCamera(target) }
            return
        }
        if (screen == Screen.MONITOR && target != Screen.MONITOR) endSession()
        if (target !in CAMERA_SCREENS) closeCamera()
        handler.removeCallbacks(ticker)
        setupScreen.taps.clear()
        screen = target
        with(cameraScreen) { holdStill(target) }
        build(target)
    }

    /** Builds the content of [target]; camera screens share one layout with a changing panel. */
    private fun build(target: Screen) {
        when (target) {
            Screen.WELCOME -> page(with(welcomeScreen) { welcome() })
            Screen.SETTINGS -> page(with(settingsScreen) { settingsPage() })
            Screen.DATA -> page(with(dataScreen) { dataPage() })
            Screen.ABOUT -> page(with(aboutScreen) { aboutPage() })
            Screen.TEXT -> page(with(aboutScreen) { textPage() })
            Screen.DEMO -> with(demoScreen) { startDemo() }
            else -> with(cameraScreen) { showCamera(target) }
        }
    }

    internal fun page(content: View) {
        stage = null
        preview = null
        panel = null
        val root = ScrollView(this).apply { setBackgroundColor(Palette.surface) }.also { it.addView(content) }
        setContentView(with(cameraScreen) { fadeIn(insetAware(root)) })
    }

    internal fun begin(target: Screen) {
        notice = null
        permissionDenied = false
        show(target)
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

    internal fun schedule() {
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, if (screen == Screen.DEMO) DEMO_FRAME_MS else TICK_MS)
    }

    /** Watchdog: runs even without camera callbacks, so stale evidence expires and silences. */
    internal fun tick() {
        val view = stage ?: return
        if (screen == Screen.DEMO) with(demoScreen) { demoTick(view) } else with(monitorScreen) { mealTick(view) }
    }

    internal fun openCamera() = with(cameraScreen) { openSource() }

    internal fun closeCamera() = with(cameraScreen) { closeSource() }

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
        val SEAT_COLOURS = listOf(R.string.seat_colour_1, R.string.seat_colour_2, R.string.seat_colour_3, R.string.seat_colour_4)
    }
}
