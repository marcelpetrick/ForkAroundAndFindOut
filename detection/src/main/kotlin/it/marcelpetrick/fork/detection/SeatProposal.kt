// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.detection

import kotlin.math.sqrt

/**
 * Proposes one seat region per person from the table outline, so setup needs no tapping
 * in the common case. People are spread over the table edges in proportion to their
 * length (a long side gets a second person before a short end gets its first); each
 * edge is split into equal places, and each place becomes a band that starts slightly
 * inside the table (shoulders overlap the tabletop in a high corner view) and extends
 * outward. Bands are clipped to the image; a place that cannot form a valid region or
 * would overlap another is left out, so the caller shows fewer proposals rather than
 * wrong ones. The regions are only a starting point: the user can clear and mark them.
 */
object SeatProposal {
    /** Outward depth as a fraction of the mean edge length. */
    const val DEPTH = 0.6

    /** Share of the depth that reaches inside the table. */
    const val INSIDE = 0.2

    /** Gap at each end of a place, as a fraction of the place's width. */
    const val INSET = 0.08

    fun propose(
        table: Polygon,
        people: Int,
    ): List<Polygon> {
        require(people in 1..4)
        val corners = table.points
        val edges = corners.indices.map { corners[it] to corners[(it + 1) % corners.size] }
        val lengths = edges.map { (a, b) -> a.distance(b) }
        val places = IntArray(edges.size)
        repeat(people) {
            val next = edges.indices.maxWith(compareBy<Int> { lengths[it] / (places[it] + 1) }.thenBy { lengths[it] })
            places[next]++
        }
        val depth = DEPTH * lengths.average()
        val center = table.center()
        val proposals = mutableListOf<Polygon>()
        for ((index, edge) in edges.withIndex()) {
            val (a, b) = edge
            val count = places[index]
            if (count == 0) continue
            // Outward unit normal: pointing away from the table centre.
            val dx = b.x - a.x
            val dy = b.y - a.y
            val length = sqrt(dx * dx + dy * dy)
            var nx = dy / length
            var ny = -dx / length
            val mid = Point((a.x + b.x) / 2, (a.y + b.y) / 2)
            if ((mid.x - center.x) * nx + (mid.y - center.y) * ny < 0) {
                nx = -nx
                ny = -ny
            }
            for (place in 0 until count) {
                val from = (place + INSET) / count
                val to = (place + 1 - INSET) / count
                val start = Point(a.x + dx * from, a.y + dy * from)
                val end = Point(a.x + dx * to, a.y + dy * to)

                fun shift(
                    p: Point,
                    by: Double,
                    along: Double = 0.0,
                ) = Point(
                    (p.x + nx * by + dx / length * along).coerceIn(0.0, 1.0),
                    (p.y + ny * by + dy / length * along).coerceIn(0.0, 1.0),
                )
                val inside = INSIDE * depth
                val outside = (1 - INSIDE) * depth
                // The inner edge is mitred at 45°, so bands of neighbouring sides never meet inside the table.
                val region =
                    runCatching {
                        Polygon(
                            listOf(shift(start, -inside, inside), shift(end, -inside, -inside), shift(end, outside), shift(start, outside)),
                        )
                    }.getOrNull() ?: continue
                if (proposals.none { it.overlaps(region) }) proposals += region
            }
        }
        return proposals
    }
}
