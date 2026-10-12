// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork

import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import it.marcelpetrick.fork.MainActivity.Screen
import it.marcelpetrick.fork.demo.SyntheticDemo
import it.marcelpetrick.fork.monitoring.Monitor
import it.marcelpetrick.fork.monitoring.MonitorSession
import it.marcelpetrick.fork.monitoring.VisualMode
import it.marcelpetrick.fork.ui.Palette
import it.marcelpetrick.fork.ui.StageView
import it.marcelpetrick.fork.ui.action
import it.marcelpetrick.fork.ui.column
import it.marcelpetrick.fork.ui.label
import it.marcelpetrick.fork.ui.title

/**
 * The synthetic demo: scripted poses through a real [Monitor], without camera, sound or storage.
 * Its functions extend [MainActivity], the single host that owns the lifecycle, the camera
 * and navigation; the screen owns only its own views and state.
 */
internal class DemoScreen {
    private var demoMonitor: Monitor? = null
    private var demoStart = 0L

    fun MainActivity.startDemo() {
        val frame = FrameLayout(this)
        val overlay = StageView(this).also { it.clock = clock }.apply { synthetic = true }
        frame.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        with(cameraScreen) { splitLayout(frame, resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) }
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

    private fun MainActivity.restartDemo() {
        demoStart = clock()
        demoMonitor = Monitor(SyntheticDemo.settings).also { it.start(demoStart) }
        handler.removeCallbacks(ticker)
        tick()
    }

    /** The demo previews the configured visual warning but never sounds or stores anything. */
    fun MainActivity.demoTick(view: StageView) {
        val now = clock()
        val demo = demoMonitor ?: return
        val aspect = if (view.width > 0 && view.height > 0) view.width.toDouble() / view.height else 1.0
        demo.frame(SyntheticDemo.poses(now - demoStart), now, now, aspect)
        view.warning = if (demo.tick(now)) settings.visual else VisualMode.OFF
        view.results = demo.results
        view.poses = emptyList()
        with(monitorScreen) { renderSeats(demo.results) }
        view.reminder = if (view.warning != VisualMode.OFF) MonitorSession.firstViolation(demo.results) else null
        view.refresh()
        schedule()
    }
}
