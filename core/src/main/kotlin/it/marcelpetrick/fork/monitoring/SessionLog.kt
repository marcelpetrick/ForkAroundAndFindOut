// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.Detector
import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.detection.Landmark
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Polygon
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.SeatResult
import it.marcelpetrick.fork.detection.Timing
import java.util.Locale

/**
 * Session log: JSON Lines, one header, then frame and label lines. Landmarks (not derived
 * features) are stored so rules can be re-run after any change; no image data exists in
 * the format. Coordinates are normalized upright analysis-image coordinates.
 */
object SessionLog {
    const val SCHEMA = 1
    val LABELS = listOf("NORMAL", "LEFT", "RIGHT", "BOTH", "FALSE_ALARM", "MISSED_VIOLATION")

    fun header(
        session: String,
        app: String,
        settings: Settings,
    ): String {
        val table = settings.table?.let(::polygon) ?: "null"
        val seats = settings.seats.joinToString(",", "[", "]", transform = ::polygon)
        val t = settings.timing
        return """{"type":"header","schema":$SCHEMA,"session":${Json.quote(session)},"app":${Json.quote(app)},""" +
            """"model":"${settings.model.name}","people":${settings.people},"aspect":${n(settings.calibrationAspect)},""" +
            """"rotation":${settings.calibrationRotation},"table":$table,"seats":$seats,""" +
            """"timing":{"trigger":${n(t.trigger)},"clear":${n(t.clear)},"triggerMs":${t.triggerMs},"clearMs":${t.clearMs},""" +
            """"cooldownMs":${t.cooldownMs},"maxGapMs":${t.maxGapMs}},"imageRecorded":false}"""
    }

    fun frame(
        timeMs: Long,
        aspect: Double,
        poses: List<Pose>,
        seats: List<SeatResult>,
    ): String {
        val landmarks =
            poses.joinToString(",", "[", "]") { pose ->
                pose.landmarks.joinToString(",", "[", "]") { "${n(it.point.x)},${n(it.point.y)},${n(it.confidence)}" }
            }
        val arms =
            seats.flatMap { seat ->
                listOf("L" to seat.left, "R" to seat.right).map { (side, arm) ->
                    """[${seat.seat},"$side","${arm.state.name}",${arm.score?.let(::n) ?: "null"}]"""
                }
            }
        return """{"type":"frame","t":$timeMs,"aspect":${n(aspect)},"poses":$landmarks,"arms":${arms.joinToString(",", "[", "]")}}"""
    }

    fun label(
        timeMs: Long,
        label: String,
        seat: Int,
    ): String {
        require(label in LABELS) { "Unknown label $label" }
        return """{"type":"label","t":$timeMs,"label":"$label","seat":$seat}"""
    }

    private fun n(value: Double): String =
        String
            .format(Locale.ROOT, "%.4f", value)
            .trimEnd('0')
            .trimEnd('.')
            .ifEmpty { "0" }

    private fun polygon(polygon: Polygon): String = polygon.points.joinToString(",", "[", "]") { "[${n(it.x)},${n(it.y)}]" }

    /** Parses lines of a session log. A truncated last line (app killed mid-write) is ignored. */
    fun read(lines: Sequence<String>): Recording {
        var header: Map<*, *>? = null
        val frames = mutableListOf<Frame>()
        val labels = mutableListOf<Label>()
        val iterator = lines.filter { it.isNotBlank() }.iterator()
        while (iterator.hasNext()) {
            val line = iterator.next()
            val entry =
                try {
                    Json.parse(line) as Map<*, *>
                } catch (error: IllegalArgumentException) {
                    if (!iterator.hasNext()) break
                    throw error
                }
            when (entry["type"]) {
                "header" -> header = entry
                "frame" -> frames += frame(entry)
                "label" -> labels += Label(long(entry["t"]), entry["label"] as String, int(entry["seat"]))
            }
        }
        val h = requireNotNull(header) { "Session log has no header" }
        require(int(h["schema"]) == SCHEMA) { "Unsupported session log schema ${h["schema"]}" }
        return Recording(h["session"] as String, h["app"] as String, settings(h), frames, labels)
    }

    private fun settings(h: Map<*, *>): Settings {
        val t = h["timing"] as Map<*, *>
        return Settings(
            people = int(h["people"]),
            model = PoseModel.valueOf(h["model"] as String),
            table = (h["table"] as List<*>?)?.let(::toPolygon),
            seats = (h["seats"] as List<*>).map { toPolygon(it as List<*>) },
            timing =
                Timing(
                    double(t["trigger"]),
                    double(t["clear"]),
                    long(t["triggerMs"]),
                    long(t["clearMs"]),
                    long(t["cooldownMs"]),
                    long(t["maxGapMs"]),
                ),
            calibrationAspect = double(h["aspect"]),
            calibrationRotation = int(h["rotation"]),
        )
    }

    private fun frame(entry: Map<*, *>): Frame {
        val poses =
            (entry["poses"] as List<*>).map { values ->
                val v = (values as List<*>).map { double(it) }
                Pose((v.indices step 3).map { Landmark(Point(v[it], v[it + 1]), v[it + 2]) })
            }
        val arms =
            (entry["arms"] as List<*>).associate { arm ->
                val a = arm as List<*>
                (int(a[0]) to (a[1] == "L")) to ElbowState.valueOf(a[2] as String)
            }
        return Frame(long(entry["t"]), double(entry["aspect"]), poses, arms)
    }

    private fun toPolygon(points: List<*>): Polygon = Polygon(points.map { (it as List<*>).let { p -> Point(double(p[0]), double(p[1])) } })

    private fun double(value: Any?): Double = (value as Number).toDouble()

    private fun long(value: Any?): Long = (value as Number).toLong()

    private fun int(value: Any?): Int = (value as Number).toInt()
}

data class Frame(
    val timeMs: Long,
    val aspect: Double,
    val poses: List<Pose>,
    /** Live states as the phone computed them, keyed by (seat, left). */
    val live: Map<Pair<Int, Boolean>, ElbowState>,
)

data class Label(
    val timeMs: Long,
    val label: String,
    val seat: Int,
)

data class Recording(
    val session: String,
    val app: String,
    val settings: Settings,
    val frames: List<Frame>,
    val labels: List<Label>,
)

/** Outcome of re-running the detector over one recorded session. */
data class ReplayReport(
    val session: String,
    val frames: Int,
    val durationMs: Long,
    /** Reminder onsets: an arm entering VIOLATION. */
    val reminders: Int,
    /** Reminders within the window of a FALSE_ALARM or NORMAL label on the same seat. */
    val remindersNearNegativeLabels: Int,
    /** LEFT/RIGHT/BOTH/MISSED_VIOLATION labels and how many saw a matching VIOLATION in the window. */
    val positiveLabels: Int,
    val positiveDetected: Int,
    val unknownFraction: Double,
    /**
     * Fraction of arm-frames where the replayed state equals the state logged live, after
     * the warm-up: a log started mid-session replays from a cold detector.
     */
    val agreement: Double,
) {
    fun describe(): String =
        String.format(
            Locale.ROOT,
            "%s: %d frames, %.1f min, %d reminders (%d near FALSE_ALARM/NORMAL labels), " +
                "positive labels detected %d/%d, UNKNOWN %.1f%%, live/replay agreement %.1f%%",
            session,
            frames,
            durationMs / 60_000.0,
            reminders,
            remindersNearNegativeLabels,
            positiveDetected,
            positiveLabels,
            unknownFraction * 100,
            agreement * 100,
        )
}

/** Deterministic replay of a recording through [Detector]; [timing] allows tuning experiments. */
object Replay {
    /** One second of motion history plus the default one-second dwell. */
    const val WARMUP_MS = 2_000L

    fun run(
        recording: Recording,
        timing: Timing = recording.settings.timing,
        windowMs: Long = 5_000,
        warmupMs: Long = WARMUP_MS,
    ): ReplayReport {
        val settings = recording.settings
        val table = requireNotNull(settings.table) { "Recording has no table calibration" }
        val detector = Detector(table, settings.people, settings.seats, timing)
        val onsets = mutableListOf<Triple<Long, Int, Boolean>>()
        val violating = mutableMapOf<Pair<Int, Boolean>, MutableList<Long>>()
        var previous = emptySet<Pair<Int, Boolean>>()
        var armFrames = 0
        var unknown = 0
        var compared = 0
        var agreed = 0
        for (frame in recording.frames) {
            val results = detector.process(frame.poses, frame.timeMs, frame.aspect)
            val current = mutableSetOf<Pair<Int, Boolean>>()
            for (seat in results) {
                for ((left, arm) in listOf(true to seat.left, false to seat.right)) {
                    val key = seat.seat to left
                    armFrames++
                    if (arm.state == ElbowState.UNKNOWN) unknown++
                    frame.live[key]?.takeIf { frame.timeMs - recording.frames.first().timeMs >= warmupMs }?.let {
                        compared++
                        if (it == arm.state) agreed++
                    }
                    if (arm.state == ElbowState.VIOLATION) {
                        current += key
                        violating.getOrPut(key) { mutableListOf() } += frame.timeMs
                    }
                }
            }
            (current - previous).forEach { onsets += Triple(frame.timeMs, it.first, it.second) }
            previous = current
        }

        fun near(
            label: Label,
            time: Long,
        ) = time in label.timeMs - windowMs..label.timeMs + windowMs
        val negatives = recording.labels.filter { it.label == "FALSE_ALARM" || it.label == "NORMAL" }
        val positives = recording.labels.filter { it.label in setOf("LEFT", "RIGHT", "BOTH", "MISSED_VIOLATION") }
        val detected =
            positives.count { label ->
                val sides =
                    when (label.label) {
                        "LEFT" -> listOf(true)
                        "RIGHT" -> listOf(false)
                        else -> listOf(true, false)
                    }
                sides.any { left -> violating[label.seat to left].orEmpty().any { near(label, it) } }
            }
        return ReplayReport(
            session = recording.session,
            frames = recording.frames.size,
            durationMs = if (recording.frames.isEmpty()) 0 else recording.frames.last().timeMs - recording.frames.first().timeMs,
            reminders = onsets.size,
            remindersNearNegativeLabels = onsets.count { (time, seat, _) -> negatives.any { it.seat == seat && near(it, time) } },
            positiveLabels = positives.size,
            positiveDetected = detected,
            unknownFraction = if (armFrames == 0) 0.0 else unknown.toDouble() / armFrames,
            agreement = if (compared == 0) 1.0 else agreed.toDouble() / compared,
        )
    }
}
