// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
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
        /** Where in the picture each person missing an arm joint is. */
        val hiddenAt: List<Side>,
    )

    // Running counts over the window, so result() does not rescan it on every frame.
    private val detectedCounts = mutableMapOf<Int, Int>()
    private var enoughVisible = 0
    private val sideCounts = IntArray(Side.entries.size)

    /** Why the check has not passed (or [PASSED]), so the screen can say what to change. */
    enum class Reason { PASSED, MEASURING, NOBODY, TOO_FEW, TOO_MANY, ARMS_HIDDEN }

    /** Where in the picture the person with hidden arms is. */
    enum class Side { LEFT, MIDDLE, RIGHT }

    private val samples = ArrayDeque<Sample>()

    data class Result(
        /** Most frequent number of detected people in the window. */
        val detected: Int,
        /** Share of frames in which at least [people] people had all arm joints visible. */
        val armsVisible: Double,
        /** Seconds of evidence collected, capped at the window. */
        val seconds: Double,
        val passed: Boolean,
        val reason: Reason = Reason.MEASURING,
        /** For [Reason.ARMS_HIDDEN]: where the person whose arms are most often hidden sits. */
        val hiddenSide: Side? = null,
    )

    fun add(
        poses: List<Pose>,
        timeMs: Long,
    ) {
        if (samples.isNotEmpty() && timeMs <= samples.last().time) return
        val (visible, hidden) = poses.partition { pose -> ARM_JOINTS.all { pose.joint(it) != null } }
        val hiddenAt =
            hidden.mapNotNull { pose ->
                pose.landmarks
                    .filter { it.visible() }
                    .takeIf { it.isNotEmpty() }
                    ?.map { it.point.x }
                    ?.average()
                    ?.let(::side)
            }
        val sample = Sample(timeMs, poses.size, visible.size, hiddenAt)
        samples.addLast(sample)
        count(sample, 1)
        while (timeMs - samples.first().time > windowMs) count(samples.removeFirst(), -1)
    }

    private fun side(x: Double) =
        if (x < LEFT_THIRD) {
            Side.LEFT
        } else if (x > RIGHT_THIRD) {
            Side.RIGHT
        } else {
            Side.MIDDLE
        }

    private fun count(
        sample: Sample,
        delta: Int,
    ) {
        detectedCounts.merge(sample.detected, delta, Int::plus)
        if (sample.visible >= people) enoughVisible += delta
        sample.hiddenAt.forEach { sideCounts[it.ordinal] += delta }
    }

    fun reset() {
        samples.clear()
        detectedCounts.clear()
        enoughVisible = 0
        sideCounts.fill(0)
    }

    fun result(): Result {
        if (samples.isEmpty()) return Result(0, 0.0, 0.0, false)
        // Most frequent head count; a tie goes to the larger count.
        val detected =
            detectedCounts.entries
                .filter { it.value > 0 }
                .maxWith(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                .key
        val armsVisible = enoughVisible.toDouble() / samples.size
        val seconds = (samples.last().time - samples.first().time) / 1000.0
        val enough = seconds * 1000 >= windowMs * MIN_COVERAGE
        val reason =
            when {
                detected == 0 -> Reason.NOBODY
                detected < people -> Reason.TOO_FEW
                detected > people -> Reason.TOO_MANY
                armsVisible < REQUIRED_SHARE -> Reason.ARMS_HIDDEN
                !enough -> Reason.MEASURING
                else -> Reason.PASSED
            }
        // Side where arms are hidden most often; a tie resolves left, middle, right.
        val hiddenSide =
            if (reason != Reason.ARMS_HIDDEN || sideCounts.all { it == 0 }) {
                null
            } else {
                Side.entries.maxBy { sideCounts[it.ordinal] }
            }
        return Result(detected, armsVisible, seconds, reason == Reason.PASSED, reason, hiddenSide)
    }

    companion object {
        const val WINDOW_MS = 10_000L
        const val REQUIRED_SHARE = 0.8

        /** Share of the window that must be covered by evidence. */
        const val MIN_COVERAGE = 0.8
        const val LEFT_THIRD = 1.0 / 3
        const val RIGHT_THIRD = 2.0 / 3

        /** Shoulders, elbows, wrists (MediaPipe indices). */
        val ARM_JOINTS = listOf(11, 12, 13, 14, 15, 16)
    }
}
