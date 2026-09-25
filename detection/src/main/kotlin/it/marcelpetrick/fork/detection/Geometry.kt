// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.detection

import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.min

/** Image coordinates are normalized; metric calculations compensate for aspect ratio. */
data class Point(
    val x: Double,
    val y: Double,
) {
    fun distance(other: Point): Double = hypot(x - other.x, y - other.y)

    fun metric(aspect: Double): Point = Point(x * aspect, y)

    fun inImage(): Boolean = x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0
}

class Polygon(
    val points: List<Point>,
) {
    init {
        require(points.size == 4 && points.all { it.inImage() }) { "Tap four corners inside the image" }
        val turns = points.indices.map { i -> cross(points[i], points[(i + 1) % 4], points[(i + 2) % 4]) }
        require(turns.all { it > 0.0001 } || turns.all { it < -0.0001 }) {
            "Corners must form a non-crossing convex table or seat region"
        }
    }

    fun contains(point: Point): Boolean = signedDistance(point) >= -1e-9

    /** Positive inside; negative outside. Zero on the perimeter. */
    fun signedDistance(
        point: Point,
        aspect: Double = 1.0,
    ): Double {
        val p = point.metric(aspect)
        var distance = Double.POSITIVE_INFINITY
        val signs = mutableListOf<Double>()
        for (i in points.indices) {
            val a = points[i].metric(aspect)
            val b = points[(i + 1) % points.size].metric(aspect)
            signs += cross(a, b, p)
            val dx = b.x - a.x
            val dy = b.y - a.y
            val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
            distance = min(distance, p.distance(Point(a.x + t * dx, a.y + t * dy)))
        }
        return if (signs.all { it >= -1e-9 } || signs.all { it <= 1e-9 }) distance else -distance
    }

    fun center(): Point = Point(points.sumOf { it.x } / 4, points.sumOf { it.y } / 4)

    /** Separating-axis test for convex regions; touching edges count as overlap. */
    fun overlaps(other: Polygon): Boolean =
        (edges() + other.edges()).none { (a, b) ->
            val nx = a.y - b.y
            val ny = b.x - a.x
            val mine = points.map { it.x * nx + it.y * ny }
            val theirs = other.points.map { it.x * nx + it.y * ny }
            mine.max() < theirs.min() || theirs.max() < mine.min()
        }

    private fun edges(): List<Pair<Point, Point>> = points.indices.map { points[it] to points[(it + 1) % points.size] }
}

private fun cross(
    a: Point,
    b: Point,
    c: Point,
): Double = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

fun angle(
    a: Point,
    vertex: Point,
    b: Point,
): Double {
    val divisor = a.distance(vertex) * b.distance(vertex)
    if (divisor < 1e-9) return 0.0
    val dot = (a.x - vertex.x) * (b.x - vertex.x) + (a.y - vertex.y) * (b.y - vertex.y)
    return Math.toDegrees(acos((dot / divisor).coerceIn(-1.0, 1.0)))
}
