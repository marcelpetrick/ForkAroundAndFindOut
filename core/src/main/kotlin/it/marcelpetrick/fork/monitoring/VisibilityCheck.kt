// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.Pose

/**
 * Vision §19 as a setup step: can the pose model keep both elbows and wrists of every
 * seated person in view from this camera position? Over a sliding window it counts the
 * people detected and the people whose shoulders, elbows and wrists are all visible.
 */
class VisibilityCheck(
    private val people: Int,
    private val windowMs: Long = WINDOW_MS,
) {
    private data class Sample(
        val time: Long,
        val detected: Int,
        val visible: Int,
    )

    private val samples = ArrayDeque<Sample>()

    data class Result(
        /** Most frequent number of detected people in the window. */
        val detected: Int,
        /** Share of frames in which at least [people] people had all arm joints visible. */
        val armsVisible: Double,
        /** Seconds of evidence collected, capped at the window. */
        val seconds: Double,
        val passed: Boolean,
    )

    fun add(
        poses: List<Pose>,
        timeMs: Long,
    ) {
        if (samples.isNotEmpty() && timeMs <= samples.last().time) return
        val visible = poses.count { pose -> ARM_JOINTS.all { pose.joint(it) != null } }
        samples.addLast(Sample(timeMs, poses.size, visible))
        while (timeMs - samples.first().time > windowMs) samples.removeFirst()
    }

    fun reset() = samples.clear()

    fun result(): Result {
        if (samples.isEmpty()) return Result(0, 0.0, 0.0, false)
        val detected =
            samples
                .groupingBy { it.detected }
                .eachCount()
                .maxWith(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                .key
        val armsVisible = samples.count { it.visible >= people }.toDouble() / samples.size
        val seconds = (samples.last().time - samples.first().time) / 1000.0
        val passed = seconds * 1000 >= windowMs * 0.8 && detected == people && armsVisible >= REQUIRED_SHARE
        return Result(detected, armsVisible, seconds, passed)
    }

    companion object {
        const val WINDOW_MS = 10_000L
        const val REQUIRED_SHARE = 0.8

        /** Shoulders, elbows, wrists (MediaPipe indices). */
        val ARM_JOINTS = listOf(11, 12, 13, 14, 15, 16)
    }
}
