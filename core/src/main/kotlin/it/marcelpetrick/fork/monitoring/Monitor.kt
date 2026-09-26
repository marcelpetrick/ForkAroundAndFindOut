// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.Detector
import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.SeatResult
import kotlin.math.abs

/** How well the phone keeps up; derived from the median interval between accepted frames. */
enum class Health { MEASURING, OK, SLOW, TOO_SLOW }

/**
 * Main-thread state owner. Caller ticks regularly even if the camera stops producing.
 * Freshness (how old a result may be on arrival) and continuity (how sparse the stream
 * may be) are separate budgets, both widened from the measured inference period so a
 * slow phone degrades to an explained state instead of a silent permanent UNKNOWN.
 */
class Monitor(
    val settings: Settings,
) {
    var active = false
        private set
    var results = emptyList<SeatResult>()
        private set
    var violations = 0
        private set
    var confidenceTotal = 0.0
        private set
    var confidenceCount = 0
        private set
    var elapsedMs = 0L
        private set
    var calibrationInvalid = false
        private set
    private var detector: Detector? = null
    private var started = 0L
    private var lastFrame: Long? = null
    private var previouslyViolating = emptySet<Pair<Int, Boolean>>()
    private val periods = ArrayDeque<Long>()

    /** Median interval between accepted frames over the last [WINDOW] frames, if known. */
    val medianPeriodMs: Long?
        get() = if (periods.size < 3) null else periods.sorted()[periods.size / 2]

    val health: Health
        get() =
            when (val period = medianPeriodMs) {
                null -> Health.MEASURING
                in 0..SLOW_MS -> Health.OK
                in SLOW_MS..TOO_SLOW_MS -> Health.SLOW
                else -> Health.TOO_SLOW
            }

    /** Continuity budget: never below the configured floor. */
    val gapMs: Long
        get() = maxOf(settings.timing.maxGapMs, 3 * (medianPeriodMs ?: 0))

    /** Freshness budget for a result's age on arrival. */
    val freshnessMs: Long
        get() = maxOf(FRESHNESS_FLOOR_MS, 3 * (medianPeriodMs ?: 0))

    fun start(now: Long): Boolean {
        val table = settings.table ?: return false
        detector = Detector(table, settings.people, settings.seats, settings.timing)
        results = emptyList()
        previouslyViolating = emptySet()
        periods.clear()
        started = now
        lastFrame = null
        active = true
        calibrationInvalid = false
        return true
    }

    fun pause(now: Long) {
        if (active) elapsedMs += (now - started).coerceAtLeast(0)
        active = false
        results = emptyList()
        detector = null
        previouslyViolating = emptySet()
    }

    /** Returns true if the frame was fresh and processed (false: paused, stale, duplicate or invalid). */
    fun frame(
        poses: List<Pose>,
        captured: Long,
        now: Long,
        aspect: Double,
        rotation: Int = settings.calibrationRotation,
    ): Boolean {
        if (!active) return false
        val aspectChanged = settings.calibrationAspect > 0 && abs(aspect - settings.calibrationAspect) > 0.03
        val rotationChanged = settings.calibrationRotation >= 0 && rotation != settings.calibrationRotation
        if (!aspect.isFinite() || aspect <= 0 || aspectChanged || rotationChanged) {
            calibrationInvalid = true
            pause(now)
            return false
        }
        if (captured > now || now - captured > freshnessMs || (lastFrame != null && captured <= lastFrame!!)) return false
        lastFrame?.let {
            periods.addLast(captured - it)
            if (periods.size > WINDOW) periods.removeFirst()
        }
        lastFrame = captured
        results = detector!!.process(poses, captured, aspect, gapMs)
        val current = mutableSetOf<Pair<Int, Boolean>>()
        for (seat in results) {
            for ((left, arm) in listOf(true to seat.left, false to seat.right)) {
                if (arm.state == ElbowState.VIOLATION) current += seat.seat to left
                arm.features?.let {
                    confidenceTotal += it.confidence
                    confidenceCount++
                }
            }
        }
        violations += (current - previouslyViolating).size
        previouslyViolating = current
        return true
    }

    fun tick(now: Long): Boolean {
        if (!active) return false
        val last = lastFrame
        if (last == null || now - last > gapMs) {
            detector = Detector(settings.table!!, settings.people, settings.seats, settings.timing)
            results = emptyList()
            previouslyViolating = emptySet()
            return false
        }
        // Evidence this sparse is not trustworthy enough to warn anyone.
        if (health == Health.TOO_SLOW) return false
        return now - started >= settings.graceMs &&
            results.any { it.left.state == ElbowState.VIOLATION || it.right.state == ElbowState.VIOLATION }
    }

    companion object {
        const val WINDOW = 15
        const val SLOW_MS = 200L
        const val TOO_SLOW_MS = 700L
        const val FRESHNESS_FLOOR_MS = 1500L
    }
}

enum class Sound { NONE, BEEP, START, STOP }

/** Audio decisions are independent from detection; every false alarm signal silences. */
class AlarmPolicy {
    private var wasActive = false
    private var continuous = false
    private var lastBeep = 0L

    fun update(
        active: Boolean,
        mode: AudioMode,
        now: Long,
        repeatMs: Long,
    ): Sound {
        if (!active || mode == AudioMode.OFF) {
            val stop = wasActive || continuous
            wasActive = false
            continuous = false
            return if (stop) Sound.STOP else Sound.NONE
        }
        val first = !wasActive
        wasActive = true
        if (mode == AudioMode.CONTINUOUS) {
            if (continuous) return Sound.NONE
            continuous = true
            return Sound.START
        }
        if (continuous) {
            continuous = false
            wasActive = false
            return Sound.STOP
        }
        if (first || (mode == AudioMode.REPEAT && now - lastBeep >= repeatMs)) {
            lastBeep = now
            return Sound.BEEP
        }
        return Sound.NONE
    }
}
