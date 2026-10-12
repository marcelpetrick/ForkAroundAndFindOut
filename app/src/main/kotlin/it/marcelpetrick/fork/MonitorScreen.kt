// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork

import android.os.BatteryManager
import android.os.PowerManager
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import it.marcelpetrick.fork.MainActivity.Companion.SEAT_COLOURS
import it.marcelpetrick.fork.MainActivity.Screen
import it.marcelpetrick.fork.detection.ArmResult
import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.detection.SeatResult
import it.marcelpetrick.fork.monitoring.Monitor
import it.marcelpetrick.fork.monitoring.MonitorSession
import it.marcelpetrick.fork.monitoring.MonitorUiState
import it.marcelpetrick.fork.monitoring.PoseModel
import it.marcelpetrick.fork.monitoring.SessionLog
import it.marcelpetrick.fork.monitoring.SessionRecorder
import it.marcelpetrick.fork.monitoring.Status
import it.marcelpetrick.fork.monitoring.VisualMode
import it.marcelpetrick.fork.monitoring.sampleRecord
import it.marcelpetrick.fork.ui.Palette
import it.marcelpetrick.fork.ui.StageView
import it.marcelpetrick.fork.ui.action
import it.marcelpetrick.fork.ui.card
import it.marcelpetrick.fork.ui.chip
import it.marcelpetrick.fork.ui.clockText
import it.marcelpetrick.fork.ui.column
import it.marcelpetrick.fork.ui.label
import it.marcelpetrick.fork.ui.row
import it.marcelpetrick.fork.ui.title
import it.marcelpetrick.fork.ui.update
import java.util.UUID

/**
 * The running meal: controls, status, hints, seat cards, adult diagnostics and training.
 * Its functions extend [MainActivity], the single host that owns the lifecycle, the camera
 * and navigation; the screen owns only its own views and state.
 */
internal class MonitorScreen {
    // Direct references: render() runs every 100 ms and should not search the view tree.
    private var pauseButton: TextView? = null
    private var diagnosticsButton: TextView? = null
    private var recalibrateButton: View? = null
    private var trainingButton: TextView? = null
    private var seatButton: TextView? = null
    private var labelsRow: View? = null
    private var sessionLine: TextView? = null

    private var banner: LinearLayout? = null

    private var hiddenHint: TextView? = null

    private var diagnosticsText: TextView? = null

    var diagnosticsOpen = false

    var adult: LinearLayout? = null

    var training = false

    private var trainingSeat = 1

    fun MainActivity.monitorPanel() {
        panel!!.apply {
            addView(title(getString(R.string.monitor_title)))
            // Pause, Stop and the adult toggle come first and never move: everything whose size
            // changes during the meal (status, hints, banner, diagnostics, seats) sits below them,
            // so a control never shifts under a finger that is about to tap it.
            addView(action(getString(R.string.pause), primary = true) { togglePause() }.also { pauseButton = it })
            addView(
                row(
                    action(getString(R.string.stop)) { show(Screen.WELCOME) },
                    action(getString(R.string.show_diagnostics)) { toggleDiagnostics() }.also { diagnosticsButton = it },
                ),
            )
            status = label("", 19f, bold = true).also(::addView)
            addView(action(getString(R.string.recalibrate)) { begin(Screen.POSITION) }.also { recalibrateButton = it })
            hiddenHint = label("", 15f, bold = true, color = Palette.amber).apply { visibility = View.GONE }.also(::addView)
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

    fun MainActivity.togglePause() {
        val current = meal ?: return
        val sound = current.togglePause(clock())
        if (!current.active) silence()
        speaker.play(sound, settings.volume)
        tick()
    }

    private fun MainActivity.toggleDiagnostics() {
        diagnosticsOpen = !diagnosticsOpen
        tick()
    }

    /** Renders one [MonitorUiState]; all decisions were made by [MonitorSession]. */
    fun MainActivity.render(state: MonitorUiState) {
        val current = meal ?: return
        if (screen != Screen.MONITOR || panel == null) return
        pauseButton?.update(getString(if (state.paused) R.string.resume else R.string.pause))
        diagnosticsButton?.update(
            getString(if (diagnosticsOpen) R.string.hide_diagnostics else R.string.show_diagnostics),
        )
        recalibrateButton?.visibility =
            if (state.status == Status.NOBODY_FOR_A_WHILE) View.VISIBLE else View.GONE
        status?.update(statusText(state))
        renderSeats(state.seats)
        renderBanner(state)
        renderHiddenArm(state)
        sessionLine?.update(getString(R.string.session_line, clockText(state.activeSeconds), state.reminders))
        renderAdult(current)
    }

    /** One watchdog step of the running meal: decide, sound, draw, and schedule the next. */
    fun MainActivity.mealTick(view: StageView) {
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
        render(state)
        schedule()
    }

    /**
     * One card per seat in its identity colour, with a words-and-colour chip per elbow.
     * Cards are built once and updated in place only when a state changes: rebuilding views
     * ten times a second would waste the main thread and flood accessibility services.
     */
    fun MainActivity.renderSeats(results: List<SeatResult>) {
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

    private fun MainActivity.word(state: ElbowState): String =
        getString(
            when (state) {
                ElbowState.UNKNOWN -> R.string.state_unknown
                ElbowState.CLEAR -> R.string.state_clear
                ElbowState.SUSPECT -> R.string.state_suspect
                ElbowState.VIOLATION -> R.string.state_violation
            },
        )

    private fun MainActivity.statusText(state: MonitorUiState): String =
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
        }

    /** A pot, bottle or glass in the way: say which arm, so the table can be rearranged. */
    private fun MainActivity.renderHiddenArm(state: MonitorUiState) {
        hiddenHint?.apply {
            val arm = state.hiddenArm
            visibility = if (arm == null) View.GONE else View.VISIBLE
            if (arm != null) {
                update(
                    getString(
                        R.string.hidden_arm,
                        getString(SEAT_COLOURS.getOrElse(arm.first - 1) { R.string.seat_colour_1 }),
                        getString(if (arm.second) R.string.arm_left else R.string.arm_right),
                    ),
                )
            }
        }
    }

    /** Adult tools: diagnostics readout and the training controls. */
    private fun MainActivity.renderAdult(current: MonitorSession) {
        adult?.visibility = if (diagnosticsOpen) View.VISIBLE else View.GONE
        if (diagnosticsOpen) diagnosticsText?.update(diagnostics(current.monitor))
        trainingButton?.update(getString(if (training) R.string.training_on else R.string.training_off))
        seatButton?.update(getString(R.string.training_seat, trainingSeat))
        labelsRow?.visibility = if (training) View.VISIBLE else View.GONE
    }

    /**
     * A warm or slow phone gets an explanation and a one-tap switch to the lighter model;
     * only offered while the Full model runs.
     */
    private fun MainActivity.renderBanner(state: MonitorUiState) {
        val card = banner ?: return
        val warm = thermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE
        val slow = state.status == Status.SLOW || state.status == Status.TOO_SLOW
        val show = settings.model == PoseModel.FULL && !state.paused && (warm || slow)
        card.visibility = if (show) View.VISIBLE else View.GONE
        val message = card.getChildAt(0) as TextView
        if (show) message.update(if (warm) getString(R.string.banner_warm) else getString(R.string.banner_slow, fps))
    }

    /** Restarts inference with the Lite model; the meal and its calibration continue. */
    private fun MainActivity.switchToLite() {
        updateSettings(settings.copy(model = PoseModel.LITE))
        closeCamera()
        openCamera()
        tick()
    }

    /** Adult-only tools, collapsed by default: diagnostics, feedback, explicit training. */
    private fun MainActivity.adultPanel(): LinearLayout =
        column(0).apply {
            diagnosticsText = label("", 14f, color = Palette.muted).also(::addView)
            addView(
                row(
                    action(getString(R.string.false_alarm)) { feedback("FALSE_ALARM") },
                    action(getString(R.string.missed_violation)) { feedback("MISSED_VIOLATION") },
                ),
            )
            addView(action(getString(R.string.training_off)) { toggleTraining() }.also { trainingButton = it })
            addView(
                column(0).apply {
                    labelsRow = this
                    addView(label(getString(R.string.training_help), 14f, color = Palette.muted))
                    addView(
                        action(getString(R.string.training_seat, 1)) {
                            trainingSeat = trainingSeat % settings.people + 1
                            tick()
                        }.also { seatButton = it },
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
    private fun MainActivity.toggleTraining() {
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

    fun MainActivity.feedback(label: String) {
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

    private fun MainActivity.labelEvent(label: String) {
        val log = recorder ?: return
        log.label(SessionLog.label(clock(), label, trainingSeat))
        trainingStatus?.text = getString(if (log.full) R.string.log_full else R.string.labelled, label, trainingSeat)
    }

    fun MainActivity.diagnostics(active: Monitor): String {
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
                "${settings.model.name} · ${source?.processor?.name ?: settings.processor.name}",
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

    private fun MainActivity.armLine(
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
}
