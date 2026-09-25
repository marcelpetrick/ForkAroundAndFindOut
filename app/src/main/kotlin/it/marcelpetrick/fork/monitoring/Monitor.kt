// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.Detector
import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.SeatResult
import kotlin.math.abs

/** Main-thread state owner. Caller ticks regularly even if the camera stops producing. */
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

    fun start(now: Long): Boolean {
        val table = settings.table ?: return false
        detector = Detector(table, settings.people, settings.seats, settings.timing)
        results = emptyList()
        previouslyViolating = emptySet()
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

    fun frame(
        poses: List<Pose>,
        captured: Long,
        now: Long,
        aspect: Double,
    ) {
        if (!active) return
        if (!aspect.isFinite() || aspect <= 0 || (settings.calibrationAspect > 0 && abs(aspect - settings.calibrationAspect) > 0.03)) {
            calibrationInvalid = true
            pause(now)
            return
        }
        if (captured > now || now - captured > settings.timing.maxGapMs || (lastFrame != null && captured <= lastFrame!!)) return
        lastFrame = captured
        results = detector!!.process(poses, captured, aspect)
        val current = mutableSetOf<Pair<Int, Boolean>>()
        for (seat in results) {
            for ((left, arm) in listOf(true to seat.left, false to seat.right)) {
                if (arm.state == ElbowState.VIOLATION) current += seat.seat to left
                if (arm.features != null) {
                    confidenceTotal += arm.features.confidence
                    confidenceCount++
                }
            }
        }
        violations += (current - previouslyViolating).size
        previouslyViolating = current
    }

    fun tick(now: Long): Boolean {
        if (!active) return false
        val last = lastFrame
        if (last == null || now - last > settings.timing.maxGapMs) {
            detector = Detector(settings.table!!, settings.people, settings.seats, settings.timing)
            results = emptyList()
            previouslyViolating = emptySet()
            return false
        }
        return now - started >= settings.graceMs &&
            results.any { it.left.state == ElbowState.VIOLATION || it.right.state == ElbowState.VIOLATION }
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
