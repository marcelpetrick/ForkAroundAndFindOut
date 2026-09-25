// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.detection

/** One-to-one matching with ambiguity rejection, never pose-list index identity. */
class SeatTracker(
    private val count: Int,
    private val zones: List<Polygon> = emptyList(),
) {
    init {
        require(count in 1..4 && zones.size <= count)
    }

    private var centers = mutableMapOf<Int, Point>()

    fun assign(poses: List<Pose>): Map<Int, Pose> {
        val candidates = poses.mapNotNull { pose -> pose.center()?.let { pose to it } }
        val result = mutableMapOf<Int, Pose>()
        if (zones.isNotEmpty()) {
            for ((index, zone) in zones.withIndex()) {
                val matches = candidates.filter { (_, center) -> zone.contains(center) && zones.count { it.contains(center) } == 1 }
                if (matches.size == 1) result[index] = matches.single().first
            }
        } else {
            val assigned = mutableSetOf<Pose>()
            for ((seat, center) in centers) {
                val near = candidates.filter { (_, p) -> center.distance(p) < 0.15 }.sortedBy { (_, p) -> center.distance(p) }
                if (near.isEmpty()) continue
                val best = near.first()
                if (near.size > 1 && center.distance(near[1].second) - center.distance(best.second) < 0.04) continue
                if (centers.values.count { it.distance(best.second) < 0.15 } != 1) continue
                if (assigned.add(best.first)) result[seat] = best.first
            }
            // New arrivals do not inherit a just-lost seat's evidence in this frame.
            val free = (0 until count).filter { it !in centers && it !in result }.toMutableList()
            for ((pose, _) in candidates.sortedBy { it.second.x }) {
                if (pose !in assigned && free.isNotEmpty()) result[free.removeAt(0)] = pose
            }
        }
        centers = result.mapValues { it.value.center()!! }.toMutableMap()
        return result
    }
}
