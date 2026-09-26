// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
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
        val result = if (zones.isNotEmpty()) byZone(candidates) else byContinuity(candidates)
        centers = result.mapValues { it.value.center()!! }.toMutableMap()
        return result
    }

    /** Each seat region takes the one person whose shoulder centre lies in it and in no other. */
    private fun byZone(candidates: List<Pair<Pose, Point>>): Map<Int, Pose> {
        val result = mutableMapOf<Int, Pose>()
        for ((index, zone) in zones.withIndex()) {
            val matches = candidates.filter { (_, center) -> zone.contains(center) && zones.count { it.contains(center) } == 1 }
            if (matches.size == 1) result[index] = matches.single().first
        }
        return result
    }

    /** Without regions: keep each seat on the nearest unambiguous person, then seat newcomers. */
    private fun byContinuity(candidates: List<Pair<Pose, Point>>): Map<Int, Pose> {
        val result = mutableMapOf<Int, Pose>()
        val assigned = mutableSetOf<Pose>()
        for ((seat, center) in centers) {
            val best = nearest(center, candidates) ?: continue
            if (assigned.add(best.first)) result[seat] = best.first
        }
        // New arrivals do not inherit a just-lost seat's evidence in this frame.
        val free = (0 until count).filter { it !in centers && it !in result }.toMutableList()
        for ((pose, _) in candidates.sortedBy { it.second.x }) {
            if (pose !in assigned && free.isNotEmpty()) result[free.removeAt(0)] = pose
        }
        return result
    }

    /** The closest candidate, unless another is almost as close or another seat is near it too. */
    private fun nearest(
        center: Point,
        candidates: List<Pair<Pose, Point>>,
    ): Pair<Pose, Point>? {
        val near = candidates.filter { (_, p) -> center.distance(p) < MATCH_RADIUS }.sortedBy { (_, p) -> center.distance(p) }
        val best = near.firstOrNull() ?: return null
        val ambiguous = near.size > 1 && center.distance(near[1].second) - center.distance(best.second) < AMBIGUITY_MARGIN
        val shared = centers.values.count { it.distance(best.second) < MATCH_RADIUS } != 1
        return if (ambiguous || shared) null else best
    }

    private companion object {
        /** A seat keeps a person whose shoulder centre moved less than this (image units). */
        const val MATCH_RADIUS = 0.15

        /** Two candidates closer than this to each other's distance are ambiguous. */
        const val AMBIGUITY_MARGIN = 0.04
    }
}
