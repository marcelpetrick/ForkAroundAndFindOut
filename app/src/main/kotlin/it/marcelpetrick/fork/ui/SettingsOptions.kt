// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.ui

import android.content.Context
import it.marcelpetrick.fork.R
import it.marcelpetrick.fork.monitoring.AudioMode
import it.marcelpetrick.fork.monitoring.Chime
import it.marcelpetrick.fork.monitoring.PoseModel
import it.marcelpetrick.fork.monitoring.Processor
import it.marcelpetrick.fork.monitoring.Sensitivity
import it.marcelpetrick.fork.monitoring.Settings
import it.marcelpetrick.fork.monitoring.VisualMode

/** Settings sections, in screen order (plan_v2 screen 07). */
enum class Group(
    val label: Int,
) {
    REMINDERS(R.string.group_reminders),
    SENSITIVITY(R.string.group_sensitivity),
    CAMERA(R.string.group_camera),
    DATA(R.string.group_data),
}

/** One adjustable setting; [change] steps by -1/+1 and always returns valid settings. */
class Option(
    val group: Group,
    val label: Int,
    val display: (Context, Settings) -> String,
    val change: (Settings, Int) -> Settings,
) {
    /** One-line explanation shown under the setting. */
    val explanation: Int
        get() = EXPLANATIONS.getValue(label)
}

private val EXPLANATIONS =
    mapOf(
        R.string.option_people to R.string.explain_people,
        R.string.option_camera to R.string.explain_camera,
        R.string.option_model to R.string.explain_model,
        R.string.option_trigger to R.string.explain_trigger,
        R.string.option_trigger_delay to R.string.explain_trigger_delay,
        R.string.option_clear_delay to R.string.explain_clear_delay,
        R.string.option_cooldown to R.string.explain_cooldown,
        R.string.option_grace to R.string.explain_grace,
        R.string.option_visual to R.string.explain_visual,
        R.string.option_audio to R.string.explain_audio,
        R.string.option_volume to R.string.explain_volume,
        R.string.option_repeat to R.string.explain_repeat,
        R.string.option_debug to R.string.explain_debug,
        R.string.option_statistics to R.string.explain_statistics,
        R.string.option_sensitivity to R.string.explain_sensitivity,
        R.string.option_chime to R.string.explain_chime,
        R.string.option_processor to R.string.explain_processor,
    )

private fun <T> cycle(
    values: List<T>,
    current: T,
    delta: Int,
): T = values[Math.floorMod(values.indexOf(current).coerceAtLeast(0) + delta, values.size)]

private fun step(
    value: Long,
    delta: Int,
    by: Long,
    range: LongRange,
): Long = (value + delta * by).coerceIn(range)

private fun Context.seconds(ms: Long) = getString(R.string.value_seconds, ms / 1000.0)

private fun Context.toggle(on: Boolean) = getString(if (on) R.string.on else R.string.off)

private val visualNames =
    mapOf(
        VisualMode.OFF to R.string.visual_off,
        VisualMode.BORDER to R.string.visual_border,
        VisualMode.ICON to R.string.visual_icon,
        VisualMode.FULL to R.string.visual_full,
        VisualMode.PULSE to R.string.visual_pulse,
    )
private val audioNames =
    mapOf(
        AudioMode.OFF to R.string.audio_off,
        AudioMode.ONCE to R.string.audio_once,
        AudioMode.REPEAT to R.string.audio_repeat,
        AudioMode.CONTINUOUS to R.string.audio_continuous,
    )

private val chimeNames =
    mapOf(
        Chime.BELL to R.string.chime_bell,
        Chime.MARIMBA to R.string.chime_marimba,
        Chime.GLASS to R.string.chime_glass,
    )
private val sensitivityNames =
    mapOf(
        Sensitivity.CONSERVATIVE to R.string.sensitivity_conservative,
        Sensitivity.NORMAL to R.string.sensitivity_normal,
        Sensitivity.RESPONSIVE to R.string.sensitivity_responsive,
    )

/** Rule evidence is currently 0.05/0.95; thresholds matter once a learned score exists. */
val TRIGGER_LEVELS = listOf(0.6, 0.75, 0.9)

/** A rear camera and a human label derived from its focal length. */
data class Lens(
    val id: String,
    val label: Int,
)

/**
 * Labels rear cameras by shortest focal length: the widest is "Wide" when there are
 * several, the longest "Tele" when there are three or more, the rest "Main".
 */
fun lensLabels(focalLengths: Map<String, Float>): List<Lens> {
    val sorted = focalLengths.entries.sortedWith(compareBy({ it.value }, { it.key }))
    return sorted.mapIndexed { index, (id, _) ->
        val label =
            when {
                sorted.size > 1 && index == 0 -> R.string.lens_wide
                sorted.size > 2 && index == sorted.lastIndex -> R.string.lens_tele
                else -> R.string.lens_main
            }
        Lens(id, label)
    }
}

/** Every setting in screen order: Reminders, Sensitivity, Camera and model, Data. */
fun settingsOptions(cameras: List<Lens>): List<Option> = reminderOptions() + sensitivityOptions() + cameraOptions(cameras) + dataOptions()

private fun reminderOptions(): List<Option> =
    listOf(
        Option(Group.REMINDERS, R.string.option_visual, { c, s -> c.getString(visualNames.getValue(s.visual)) }) { s, d ->
            s.copy(visual = cycle(VisualMode.entries, s.visual, d))
        },
        Option(Group.REMINDERS, R.string.option_audio, { c, s -> c.getString(audioNames.getValue(s.audio)) }) { s, d ->
            s.copy(audio = cycle(AudioMode.entries, s.audio, d))
        },
        Option(Group.REMINDERS, R.string.option_chime, { c, s -> c.getString(chimeNames.getValue(s.chime)) }) { s, d ->
            s.copy(chime = cycle(Chime.entries, s.chime, d))
        },
        Option(Group.REMINDERS, R.string.option_volume, { c, s -> c.getString(R.string.value_percent, s.volume) }) { s, d ->
            s.copy(volume = (s.volume + d * 10).coerceIn(0, 100))
        },
        Option(Group.REMINDERS, R.string.option_repeat, { c, s -> c.seconds(s.repeatMs) }) { s, d ->
            s.copy(repeatMs = step(s.repeatMs, d, 1000, 1000L..30_000L))
        },
        Option(Group.REMINDERS, R.string.option_grace, { c, s -> c.seconds(s.graceMs) }) { s, d ->
            s.copy(graceMs = step(s.graceMs, d, 1000, 0L..30_000L))
        },
    )

/** A named feel first, the raw values below it. */
private fun sensitivityOptions(): List<Option> =
    listOf(
        Option(Group.SENSITIVITY, R.string.option_sensitivity, { c, s ->
            Sensitivity.of(s.timing)?.let { c.getString(sensitivityNames.getValue(it)) } ?: c.getString(R.string.sensitivity_custom)
        }) { s, d ->
            val current = Sensitivity.of(s.timing) ?: Sensitivity.NORMAL
            val next = if (Sensitivity.of(s.timing) == null) current else cycle(Sensitivity.entries, current, d)
            s.copy(timing = next.apply(s.timing))
        },
        Option(
            Group.SENSITIVITY,
            R.string.option_trigger,
            { c, s -> c.getString(R.string.value_percent, (s.timing.trigger * 100).toInt()) },
        ) { s, d ->
            val index = TRIGGER_LEVELS.indexOf(s.timing.trigger).takeIf { it >= 0 } ?: 1
            s.copy(timing = s.timing.copy(trigger = TRIGGER_LEVELS[(index + d).coerceIn(0, TRIGGER_LEVELS.lastIndex)]))
        },
        Option(Group.SENSITIVITY, R.string.option_trigger_delay, { c, s -> c.seconds(s.timing.triggerMs) }) { s, d ->
            s.copy(timing = s.timing.copy(triggerMs = step(s.timing.triggerMs, d, 250, 500L..5000L)))
        },
        Option(Group.SENSITIVITY, R.string.option_clear_delay, { c, s -> c.seconds(s.timing.clearMs) }) { s, d ->
            s.copy(timing = s.timing.copy(clearMs = step(s.timing.clearMs, d, 250, 250L..3000L)))
        },
        Option(Group.SENSITIVITY, R.string.option_cooldown, { c, s -> c.seconds(s.timing.cooldownMs) }) { s, d ->
            s.copy(timing = s.timing.copy(cooldownMs = step(s.timing.cooldownMs, d, 500, 0L..10_000L)))
        },
    )

private fun cameraOptions(cameras: List<Lens>): List<Option> {
    val choices = listOf("") + cameras.map { it.id }
    return listOf(
        Option(Group.CAMERA, R.string.option_people, { c, s -> c.getString(R.string.value_number, s.people.toString()) }) { s, d ->
            val people = (s.people + d).coerceIn(1, 4)
            s.copy(people = people, seats = if (s.seats.size > people) emptyList() else s.seats)
        },
        Option(Group.CAMERA, R.string.option_camera, { c, s ->
            val lens = cameras.firstOrNull { it.id == s.camera }
            when {
                s.camera.isEmpty() -> c.getString(R.string.camera_automatic)
                lens != null -> c.getString(R.string.camera_lens, c.getString(lens.label), s.camera)
                else -> c.getString(R.string.camera_id, s.camera)
            }
        }) { s, d ->
            // A different camera has different geometry: the old table outline is meaningless.
            val camera = cycle(choices, s.camera, d)
            if (camera == s.camera) s else s.copy(camera = camera, table = null, seats = emptyList(), calibrationAspect = 0.0)
        },
        Option(Group.CAMERA, R.string.option_model, { c, s ->
            c.getString(if (s.model == PoseModel.FULL) R.string.model_full else R.string.model_lite)
        }) { s, d -> s.copy(model = cycle(PoseModel.entries, s.model, d)) },
        Option(Group.CAMERA, R.string.option_processor, { c, s ->
            c.getString(if (s.processor == Processor.CPU) R.string.processor_cpu else R.string.processor_gpu)
        }) { s, d -> s.copy(processor = cycle(Processor.entries, s.processor, d)) },
    )
}

private fun dataOptions(): List<Option> =
    listOf(
        Option(Group.DATA, R.string.option_debug, { c, s -> c.toggle(s.debug) }) { s, _ -> s.copy(debug = !s.debug) },
        Option(Group.DATA, R.string.option_statistics, { c, s -> c.toggle(s.statistics) }) { s, _ -> s.copy(statistics = !s.statistics) },
    )
