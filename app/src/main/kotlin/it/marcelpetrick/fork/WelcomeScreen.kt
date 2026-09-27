// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork

import android.content.Intent
import android.net.Uri
import android.view.View
import it.marcelpetrick.fork.MainActivity.Companion.SEAT_COLOURS
import it.marcelpetrick.fork.MainActivity.Screen
import it.marcelpetrick.fork.monitoring.MealSummary
import it.marcelpetrick.fork.monitoring.Settings
import it.marcelpetrick.fork.ui.Palette
import it.marcelpetrick.fork.ui.action
import it.marcelpetrick.fork.ui.card
import it.marcelpetrick.fork.ui.clockText
import it.marcelpetrick.fork.ui.column
import it.marcelpetrick.fork.ui.label
import it.marcelpetrick.fork.ui.title

/**
 * The welcome / ready screen with the meal summary and the permission card.
 * Its functions extend [MainActivity], the single host that owns the lifecycle, the camera
 * and navigation; the screen owns only its own views and state.
 */
internal class WelcomeScreen {
    fun MainActivity.welcome(): View =
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
    private fun MainActivity.summaryCard(summary: MealSummary): View =
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

    /** After a refusal Android no longer asks; only the app's system settings can grant it. */
    private fun MainActivity.openAppSettings() {
        startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
