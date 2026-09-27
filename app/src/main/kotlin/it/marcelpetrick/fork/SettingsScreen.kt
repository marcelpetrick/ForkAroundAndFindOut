// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork

import android.view.View
import it.marcelpetrick.fork.MainActivity.Screen
import it.marcelpetrick.fork.monitoring.Sound
import it.marcelpetrick.fork.ui.Group
import it.marcelpetrick.fork.ui.Option
import it.marcelpetrick.fork.ui.Palette
import it.marcelpetrick.fork.ui.action
import it.marcelpetrick.fork.ui.card
import it.marcelpetrick.fork.ui.column
import it.marcelpetrick.fork.ui.dp
import it.marcelpetrick.fork.ui.label
import it.marcelpetrick.fork.ui.row
import it.marcelpetrick.fork.ui.settingsOptions
import it.marcelpetrick.fork.ui.title
import it.marcelpetrick.fork.ui.update

/**
 * Grouped settings: every option as a card with its explanation and − value + controls.
 * Its functions extend [MainActivity], the single host that owns the lifecycle, the camera
 * and navigation; the screen owns only its own views and state.
 */
internal class SettingsScreen {
    fun MainActivity.settingsPage(): View =
        column().apply {
            addView(title(getString(R.string.settings_title)))
            addView(label(getString(R.string.settings_help), color = Palette.muted))
            val options = settingsOptions(cameraIds())
            val refreshers = mutableListOf<() -> Unit>()
            for (group in Group.entries) {
                addView(label(getString(group.label), 20f, bold = true, color = Palette.green).apply { setPadding(0, dp(20), 0, 0) })
                if (group == Group.REMINDERS) {
                    addView(
                        action(getString(R.string.test_sound)) {
                            speaker.prepare(settings.chime)
                            speaker.play(Sound.BEEP, settings.volume)
                        },
                    )
                }
                if (group == Group.DATA) addView(action(getString(R.string.local_data)) { begin(Screen.DATA) })
                for (option in options.filter { it.group == group }) addView(settingCard(option, refreshers))
            }
            addView(action(getString(R.string.back), primary = true) { show(Screen.WELCOME) })
        }

    /**
     * One setting: name, a one-line explanation and − value + controls. [refresh] updates every
     * shown value, because a sensitivity preset also changes the raw timings below it.
     */
    private fun MainActivity.settingCard(
        option: Option,
        refreshers: MutableList<() -> Unit>,
    ): View {
        val name = getString(option.label)
        val value = label(option.display(this, settings), 18f, bold = true)
        refreshers += { value.update(option.display(this, settings)) }

        fun change(delta: Int) {
            settings = option.change(settings, delta)
            store.save(settings)
            refreshers.forEach { it() }
        }
        return card(
            label(name, bold = true),
            label(getString(option.explanation), 14f, color = Palette.muted),
            row(
                action("−") { change(-1) }.apply { contentDescription = getString(R.string.decrease, name) },
                value.apply { textAlignment = View.TEXT_ALIGNMENT_CENTER },
                action("+") { change(1) }.apply { contentDescription = getString(R.string.increase, name) },
            ),
        )
    }
}
