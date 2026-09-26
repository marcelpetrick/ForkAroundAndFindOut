// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.SeatResult

/** What the monitor screen should say, most important first. */
enum class Status { PAUSED, GRACE, TOO_SLOW, RESTING, NOBODY_FOR_A_WHILE, WAITING, SLOW, REMINDING, WATCHING }

/** Everything the monitor screen renders; immutable, produced on every tick. */
data class MonitorUiState(
    val status: Status,
    /** Seconds left for GRACE or RESTING countdowns, else 0. */
    val countdown: Long,
    val seats: List<SeatResult>,
    val warning: Boolean,
    /** Seat number and side (true = left) the reminder card names, or null. */
    val reminder: Pair<Int, Boolean>?,
    val thanks: Boolean,
    val paused: Boolean,
    val activeSeconds: Long,
    val reminders: Int,
    val sound: Sound,
    /**
     * Seat number and side (true = left) of an arm that has been hidden most of the time
     * although its person is in view — likely a pot or bottle in the way — or null.
     */
    val hiddenArm: Pair<Int, Boolean>? = null,
)

/** Positive end-of-meal summary. */
data class MealSummary(
    /** Active (unpaused) monitoring time in milliseconds. */
    val activeMs: Long,
    val reminders: Int,
    /** Episodes per seat number in which an elbow rested (only seats that had any). */
    val remindersBySeat: Map<Int, Int>,
    /** Longest stretch of active monitoring without any reminder, in seconds. */
    val longestCalmSeconds: Long,
    val falseAlarms: Int,
    val missedViolations: Int,
    val meanConfidence: Double?,
) {
    val activeSeconds: Long
        get() = activeMs / 1000
}

/**
 * One meal: owns the [Monitor], [AlarmPolicy], grace period, false-alarm rest, thank-you
 * after a correction, per-seat reminder counts and the calm-stretch record. Pure Kotlin and
 * deterministic (time is passed in), so the screen logic is tested on the JVM; the activity
 * only renders [MonitorUiState] and plays [MonitorUiState.sound].
 */
class MonitorSession(
    val settings: Settings,
    val id: String,
    now: Long,
) {
    val monitor = Monitor(settings)
    private val policy = AlarmPolicy()
    private var resumedAt = now
    private var restUntil = 0L
    private var thanksUntil = 0L
    private var alarmingBefore = false
    private var calmSince = 0L
    private var longestCalm = 0L

    /** Last time an accepted frame showed anyone; a long gap suggests the phone moved. */
    private var lastSeen = now
    private val bySeat = mutableMapOf<Int, Int>()
    private var violating = emptySet<Int>()
    private var reminders = 0

    /** Per seat and side: smoothed share of frames with the person in view but the arm hidden. */
    private val hiddenShare = mutableMapOf<Pair<Int, Boolean>, Double>()
    private val inViewMs = mutableMapOf<Pair<Int, Boolean>, Long>()
    private var lastAccepted: Long? = null
    var falseAlarms = 0
        private set
    var missedViolations = 0
        private set

    init {
        monitor.start(now)
    }

    val active: Boolean
        get() = monitor.active

    private fun activeMs(now: Long) = monitor.elapsedMs + if (monitor.active) (now - resumedAt).coerceAtLeast(0) else 0

    /** Returns true when the frame was accepted (fresh, same geometry). */
    fun frame(
        poses: List<Pose>,
        captured: Long,
        now: Long,
        aspect: Double,
        rotation: Int,
    ): Boolean {
        val accepted = monitor.frame(poses, captured, now, aspect, rotation)
        if (!accepted) return false
        if (monitor.results.any { it.pose != null }) lastSeen = now
        trackHiddenArms(captured)
        return true
    }

    /** Exponential average over about [HIDDEN_TAU_MS]; only frames with the person in view count. */
    private fun trackHiddenArms(captured: Long) {
        val dt = lastAccepted?.let { (captured - it).coerceIn(0, 1000) } ?: 0
        lastAccepted = captured
        val weight = 1 - kotlin.math.exp(-dt / HIDDEN_TAU_MS.toDouble())
        for (seat in monitor.results) {
            for ((left, arm) in listOf(true to seat.left, false to seat.right)) {
                val key = seat.seat to left
                if (seat.pose == null) {
                    inViewMs.remove(key)
                    hiddenShare.remove(key)
                    continue
                }
                val hidden = if (arm.state == ElbowState.UNKNOWN && arm.features == null) 1.0 else 0.0
                val share = hiddenShare[key]
                hiddenShare[key] = if (share == null) hidden else share + (hidden - share) * weight
                inViewMs[key] = (inViewMs[key] ?: 0) + dt
            }
        }
    }

    /** The arm hidden longest (most often) while its person is in view, if it passes the bar. */
    private fun hiddenArm(): Pair<Int, Boolean>? =
        hiddenShare.entries
            .filter { (key, share) -> share >= HIDDEN_SHARE && (inViewMs[key] ?: 0) >= HIDDEN_MIN_MS }
            .maxByOrNull { it.value }
            ?.key

    fun tick(now: Long): MonitorUiState {
        val alarming = monitor.tick(now) && now >= restUntil
        if (alarming && !alarmingBefore) {
            reminders++
            longestCalm = maxOf(longestCalm, activeMs(now) - calmSince)
        }
        if (alarmingBefore && !alarming) {
            calmSince = activeMs(now)
            // A correction (not a pause, rest or lost view) earns a short thank-you.
            if (monitor.active && now >= restUntil && monitor.results.isNotEmpty()) thanksUntil = now + THANKS_MS
        }
        alarmingBefore = alarming
        // A seat counts once per reminder episode, whether one or both elbows are down, and only
        // while the table is actually reminded (not during grace, a rest or a too-slow phone).
        val current =
            if (!alarming) {
                emptySet()
            } else {
                monitor.results
                    .filter { it.left.state == ElbowState.VIOLATION || it.right.state == ElbowState.VIOLATION }
                    .map { it.seat }
                    .toSet()
            }
        (current - violating).forEach { seat -> bySeat[seat] = (bySeat[seat] ?: 0) + 1 }
        violating = current
        val sound = policy.update(alarming, settings.audio, now, settings.repeatMs)
        return state(now, alarming, sound)
    }

    private fun state(
        now: Long,
        alarming: Boolean,
        sound: Sound,
    ): MonitorUiState {
        val graceLeft = settings.graceMs - (now - resumedAt)
        val status =
            when {
                !monitor.active -> Status.PAUSED
                graceLeft > 0 -> Status.GRACE
                monitor.health == Health.TOO_SLOW -> Status.TOO_SLOW
                now < restUntil -> Status.RESTING
                now - lastSeen > NOBODY_MS -> Status.NOBODY_FOR_A_WHILE
                monitor.results.isEmpty() -> Status.WAITING
                monitor.health == Health.SLOW -> Status.SLOW
                alarming -> Status.REMINDING
                else -> Status.WATCHING
            }
        val countdown =
            when (status) {
                Status.GRACE -> secondsLeft(graceLeft)
                Status.RESTING -> secondsLeft(restUntil - now)
                else -> 0
            }
        return MonitorUiState(
            status = status,
            countdown = countdown,
            seats = monitor.results,
            warning = alarming,
            reminder = if (alarming) firstViolation(monitor.results) else null,
            thanks = !alarming && now < thanksUntil,
            paused = !monitor.active,
            activeSeconds = activeMs(now) / 1000,
            reminders = reminders,
            sound = sound,
            hiddenArm = if (monitor.active) hiddenArm() else null,
        )
    }

    /** Whole seconds left, rounded up, so a countdown never shows 0 while it still runs. */
    private fun secondsLeft(ms: Long) = (ms + MS_PER_SECOND - 1) / MS_PER_SECOND

    /** Pause silences at once; resume restarts the grace period and ends any rest. */
    fun togglePause(now: Long): Sound {
        if (monitor.active) {
            monitor.pause(now)
            return silence(now)
        }
        monitor.start(now)
        resumedAt = now
        lastSeen = now
        lastAccepted = null // the pause is not time "in view"
        restUntil = 0L
        return Sound.NONE
    }

    /** Backgrounding or camera loss: pause without changing counters. */
    fun suspend(now: Long): Sound {
        if (monitor.active) monitor.pause(now)
        return silence(now)
    }

    /** An adult marks a wrong reminder: silence now and rest reminders for a while. */
    fun falseAlarm(now: Long): Sound {
        falseAlarms++
        restUntil = now + REST_MS
        return silence(now)
    }

    fun missedViolation() {
        missedViolations++
    }

    private fun silence(now: Long): Sound {
        // An interrupted reminder ends here: the next calm stretch starts now, not before it.
        if (alarmingBefore) calmSince = activeMs(now)
        alarmingBefore = false
        violating = emptySet()
        thanksUntil = 0L
        policy.update(false, settings.audio, now, settings.repeatMs)
        return Sound.STOP
    }

    fun summary(now: Long): MealSummary {
        val active = activeMs(now)
        val calm = maxOf(longestCalm, if (alarmingBefore) 0 else active - calmSince)
        return MealSummary(
            activeMs = active,
            reminders = reminders,
            remindersBySeat = bySeat.toSortedMap(),
            longestCalmSeconds = calm / 1000,
            falseAlarms = falseAlarms,
            missedViolations = missedViolations,
            meanConfidence = if (monitor.confidenceCount == 0) null else monitor.confidenceTotal / monitor.confidenceCount,
        )
    }

    companion object {
        const val REST_MS = 30_000L
        const val THANKS_MS = 2_000L
        const val NOBODY_MS = 20_000L
        const val MS_PER_SECOND = 1000L

        /** An arm hidden in this share of recent in-view frames is reported. */
        const val HIDDEN_SHARE = 0.7

        /** ... once its person has been in view at least this long. */
        const val HIDDEN_MIN_MS = 20_000L
        const val HIDDEN_TAU_MS = 10_000L

        fun firstViolation(results: List<SeatResult>): Pair<Int, Boolean>? =
            results.firstNotNullOfOrNull { seat ->
                when {
                    seat.left.state == ElbowState.VIOLATION -> seat.seat to true
                    seat.right.state == ElbowState.VIOLATION -> seat.seat to false
                    else -> null
                }
            }
    }
}
