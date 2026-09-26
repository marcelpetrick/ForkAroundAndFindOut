// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.detection

data class ArmResult(
    val state: ElbowState,
    val score: Double?,
    val features: Features?,
)

data class SeatResult(
    val seat: Int,
    val pose: Pose?,
    val left: ArmResult,
    val right: ArmResult,
)

class Detector(
    private val table: Polygon,
    private val people: Int = 4,
    zones: List<Polygon> = emptyList(),
    private val timing: Timing = Timing(),
) {
    private val tracker = SeatTracker(people, zones)
    private val classifiers = mutableMapOf<Pair<Int, Boolean>, ArmClassifier>()
    private val filters = mutableMapOf<Pair<Int, Boolean>, TemporalFilter>()

    fun process(
        poses: List<Pose>,
        timeMs: Long,
        aspect: Double = 1.0,
        maxGapMs: Long = timing.maxGapMs,
    ): List<SeatResult> {
        val assigned = tracker.assign(poses)
        return (0 until people).map { seat ->
            val pose = assigned[seat]

            fun arm(left: Boolean): ArmResult {
                val key = seat to left
                if (pose == null) {
                    classifiers.remove(key)
                    filters.remove(key)
                    return ArmResult(ElbowState.UNKNOWN, null, null)
                }
                val evidence = classifiers.getOrPut(key) { ArmClassifier() }.evaluate(pose, left, table, timeMs, aspect, maxGapMs)
                val state = filters.getOrPut(key) { TemporalFilter(timing) }.update(timeMs, evidence.score, maxGapMs, evidence.hidden)
                return ArmResult(state, evidence.score, evidence.features)
            }
            SeatResult(seat + 1, pose, arm(true), arm(false))
        }
    }
}
