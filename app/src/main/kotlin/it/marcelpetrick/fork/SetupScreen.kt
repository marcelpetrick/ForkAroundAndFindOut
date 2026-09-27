// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import it.marcelpetrick.fork.MainActivity.Companion.CONTINUE_TAG
import it.marcelpetrick.fork.MainActivity.Companion.SLOW_FPS
import it.marcelpetrick.fork.MainActivity.Screen
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Polygon
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.SeatProposal
import it.marcelpetrick.fork.monitoring.VisibilityCheck
import it.marcelpetrick.fork.ui.Palette
import it.marcelpetrick.fork.ui.action
import it.marcelpetrick.fork.ui.column
import it.marcelpetrick.fork.ui.label
import it.marcelpetrick.fork.ui.row
import it.marcelpetrick.fork.ui.title
import it.marcelpetrick.fork.ui.update

/**
 * Camera setup: position and visibility check, table marking, seat regions.
 * Its functions extend [MainActivity], the single host that owns the lifecycle, the camera
 * and navigation; the screen owns only its own views and state.
 */
internal class SetupScreen {
    private var advice: TextView? = null

    private var seatCheck: TextView? = null

    private var visibilityCheck: VisibilityCheck? = null

    val taps = mutableListOf<Point>()

    val seats = mutableListOf<Polygon>()

    /** Vision §19 as a gate: the table is marked once everyone's arms are reliably visible. */
    fun MainActivity.positionPanel() {
        chooseWidestLens()
        visibilityCheck = VisibilityCheck(settings.people)
        panel!!.apply {
            addView(title(getString(R.string.position_title)))
            addView(
                ImageView(this@positionPanel).apply {
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
            // Everyone sits down in their own time: start the ten seconds again once all are settled.
            addView(action(getString(R.string.restart_check)) { restartVisibilityCheck() })
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
    private fun MainActivity.peopleStepper(): View {
        val name = getString(R.string.option_people)

        fun change(delta: Int) {
            val people = (settings.people + delta).coerceIn(1, 4)
            if (people == settings.people) return
            updateSettings(settings.copy(people = people, seats = if (settings.seats.size == people) settings.seats else emptyList()))
            show(Screen.POSITION)
        }
        // The label sits above the buttons: squeezed between them it would wrap on phones.
        return column(0).apply {
            addView(label(getString(R.string.people_count, settings.people), 18f, bold = true))
            addView(
                row(
                    action("−") { change(-1) }.apply { contentDescription = getString(R.string.decrease, name) },
                    action("+") { change(1) }.apply { contentDescription = getString(R.string.increase, name) },
                ),
            )
        }
    }

    /** One chip per rear lens (Wide / Main / Tele); switching lens invalidates the table outline. */
    private fun MainActivity.lensChips(): View? {
        val lenses = cameraIds()
        if (lenses.size < 2) return null
        return row(
            lenses.map { lens ->
                action(getString(lens.label), primary = lens.id == settings.camera) { selectLens(lens.id) }.apply {
                    contentDescription = getString(R.string.lens_choice, getString(lens.label))
                }
            },
        )
    }

    private fun MainActivity.selectLens(id: String) {
        if (id == settings.camera) return
        updateSettings(settings.copy(camera = id, table = null, seats = emptyList(), calibrationAspect = 0.0, calibrationRotation = -1))
        closeCamera()
        show(Screen.POSITION)
    }

    /** A new setup starts on the widest rear lens: from a corner it sees the most of the table. */
    private fun MainActivity.chooseWidestLens() {
        if (settings.table != null || settings.camera.isNotEmpty()) return
        val wide = cameraIds().firstOrNull { it.label == R.string.lens_wide } ?: return
        updateSettings(settings.copy(camera = wide.id))
        closeCamera()
    }

    private fun MainActivity.visibilityAdvice(result: VisibilityCheck.Result): String =
        when {
            result.passed -> {
                getString(R.string.visibility_passed)
            }

            fps > 0 && fps < SLOW_FPS && result.seconds >= 3 -> {
                getString(R.string.visibility_slow, fps)
            }

            else -> {
                when (result.reason) {
                    VisibilityCheck.Reason.NOBODY -> {
                        getString(R.string.visibility_nobody)
                    }

                    VisibilityCheck.Reason.TOO_FEW -> {
                        getString(R.string.visibility_too_few, result.detected, settings.people)
                    }

                    VisibilityCheck.Reason.TOO_MANY -> {
                        getString(R.string.visibility_too_many, result.detected, settings.people)
                    }

                    VisibilityCheck.Reason.ARMS_HIDDEN -> {
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
                    }

                    else -> {
                        getString(R.string.visibility_tips)
                    }
                }
            }
        }

    /** Discards the evidence so far; the check runs its full ten seconds from now. */
    private fun MainActivity.restartVisibilityCheck() {
        visibilityCheck?.reset()
        status?.text = getString(R.string.people_detected, 0)
        advice?.text = getString(R.string.visibility_restarted)
        panel?.findViewWithTag<View>(CONTINUE_TAG)?.apply {
            isEnabled = false
            alpha = 0.5f
        }
    }

    fun MainActivity.refreshVisibility(poses: List<Pose>) {
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

    fun MainActivity.tablePanel() {
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

    private fun MainActivity.tapInput(onPoint: (Point) -> Unit) {
        stage!!.onTap = { point ->
            // Until the first frame the image-to-view mapping is unknown; a tap cannot be placed.
            if (!cameraReady) status?.text = getString(R.string.waiting_for_image) else edit { onPoint(point) }
        }
        stage!!.onRejectedTap = { status?.text = getString(R.string.tap_inside_image) }
        stage!!.onDrag = { index, point -> edit { taps[index] = point } }
    }

    fun MainActivity.edit(change: () -> Unit) {
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

    private fun MainActivity.saveTable() {
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

    fun MainActivity.seatsPanel() {
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
    private fun MainActivity.suggestSeats() {
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
    fun MainActivity.refreshSeatCheck(poses: List<Pose>) {
        if (seats.isEmpty()) return
        val inside = poses.count { pose -> pose.center()?.let { c -> seats.count { it.contains(c) } == 1 } == true }
        seatCheck?.update(getString(R.string.seats_inside, inside, settings.people))
    }

    private fun MainActivity.addSeat() {
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

    private fun MainActivity.finishSetup() {
        // Seat regions assign people only inside them: partial regions would leave people unwatched.
        if (seats.isNotEmpty() && seats.size != settings.people) {
            status?.text = getString(R.string.seats_incomplete, settings.people)
            return
        }
        updateSettings(settings.copy(seats = seats.toList()))
        show(Screen.WELCOME)
    }
}
