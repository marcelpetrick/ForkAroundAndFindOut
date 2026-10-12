// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork

import android.Manifest
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.camera.view.PreviewView
import it.marcelpetrick.fork.MainActivity.Companion.CAMERA_SCREENS
import it.marcelpetrick.fork.MainActivity.Companion.FADE_MS
import it.marcelpetrick.fork.MainActivity.Companion.MAX_PEOPLE
import it.marcelpetrick.fork.MainActivity.Screen
import it.marcelpetrick.fork.camera.FrameInfo
import it.marcelpetrick.fork.camera.FrameSource
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.monitoring.SessionLog
import it.marcelpetrick.fork.monitoring.Settings
import it.marcelpetrick.fork.monitoring.VisualMode
import it.marcelpetrick.fork.ui.Lens
import it.marcelpetrick.fork.ui.Palette
import it.marcelpetrick.fork.ui.StageView
import it.marcelpetrick.fork.ui.column
import it.marcelpetrick.fork.ui.lensLabels

typealias SourceFactory = (
    PreviewView,
    Settings,
    (List<Pose>, Long, FrameInfo) -> Unit,
    (String) -> Unit,
) -> FrameSource

/**
 * The camera plumbing shared by the setup steps and the monitor: permission, the split
 * preview/panel layout, opening and closing the frame source, and routing its frames.
 * Its functions extend [MainActivity], the single host that owns the lifecycle, the camera
 * and navigation; this class owns only the camera's own state.
 */
internal class CameraScreen {
    /** The camera screen waiting for the permission answer. */
    private var pendingCamera: Screen? = null

    /** The pose limit the open source was built with (the engine cannot change it later). */
    private var sourceLimit = 0

    /** Android's rationale case explains first; afterwards the system dialog asks. */
    fun MainActivity.requestCamera(target: Screen) {
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

    fun MainActivity.onPermission(granted: Boolean) {
        val target = pendingCamera ?: return
        pendingCamera = null
        if (granted) {
            show(target)
        } else {
            notice = getString(R.string.permission_needed)
            permissionDenied = true
            show(Screen.WELCOME)
        }
    }

    /** Camera screens and the demo keep the screen on; a propped-up phone must not rotate mid-meal. */
    fun MainActivity.holdStill(target: Screen) {
        requestedOrientation =
            if (target in CAMERA_SCREENS) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (target in CAMERA_SCREENS || target == Screen.DEMO) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** The shared camera layout with the panel of one setup step or the monitor. */
    fun MainActivity.showCamera(target: Screen) {
        if (source != null && sourceLimit != poseLimit(target)) closeSource()
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
        if (source == null) openSource()
    }

    private fun MainActivity.cameraLayout(landscape: Boolean = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
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

    fun MainActivity.splitLayout(
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
                    ScrollView(this@splitLayout).apply { addView(controls) },
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
    fun fadeIn(root: View): View =
        root.apply {
            alpha = 0f
            animate().alpha(1f).setDuration(FADE_MS).start()
        }

    /** Keeps content clear of system bars and cutouts (edge-to-edge is enforced from API 35). */
    fun insetAware(root: View): View =
        root.apply {
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

    private fun MainActivity.onFrame(
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
        // No unconditional redraw: the overlay invalidates itself when drawn state changes.
    }

    private fun MainActivity.onCameraError(message: String) {
        meal?.let { speaker.play(it.suspend(clock()), settings.volume) }
        silence()
        closeSource()
        notice = getString(R.string.camera_error, message)
        show(Screen.WELCOME)
    }

    /**
     * How many people the pose model looks for. Monitoring needs exactly the configured
     * number; setup looks for up to four, so an extra person at the table is noticed.
     */
    private fun MainActivity.poseLimit(target: Screen) = if (target == Screen.MONITOR) settings.people else MAX_PEOPLE

    fun MainActivity.openSource() {
        val view = preview ?: return
        sourceLimit = poseLimit(screen)
        source =
            sourceFactory(view, settings.copy(people = sourceLimit), { poses, time, info -> onFrame(poses, time, info) }) {
                onCameraError(it)
            }.also { it.start() }
    }

    fun MainActivity.closeSource() {
        source?.close()
        source = null
        frameInfo = null
        latencies.clear()
        lastFrame = 0L
        fps = 0.0
    }

    fun MainActivity.backCameras(): List<Lens> =
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
}
