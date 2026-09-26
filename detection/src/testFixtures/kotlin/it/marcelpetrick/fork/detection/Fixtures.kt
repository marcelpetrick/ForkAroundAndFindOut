// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.detection

/** Shared synthetic fixtures: a calibrated table and a seated person (no real footage). */
val table = Polygon(listOf(Point(0.1, 0.5), Point(0.9, 0.5), Point(0.9, 0.9), Point(0.1, 0.9)))

fun pose(
    left: Point = Point(0.4, 0.55),
    right: Point = Point(0.6, 0.55),
    offset: Double = 0.0,
): Pose {
    val points = MutableList(33) { Landmark(Point(0.5 + offset, 0.2), 0.99) }
    points[11] = Landmark(Point(0.4 + offset, 0.3), 0.99)
    points[12] = Landmark(Point(0.6 + offset, 0.3), 0.99)
    points[13] = Landmark(Point(left.x + offset, left.y), 0.99)
    points[14] = Landmark(Point(right.x + offset, right.y), 0.99)
    points[15] = Landmark(Point(0.51 + offset, 0.48), 0.99)
    points[16] = Landmark(Point(0.49 + offset, 0.48), 0.99)
    points[23] = Landmark(Point(0.4 + offset, 0.7), 0.99)
    points[24] = Landmark(Point(0.6 + offset, 0.7), 0.99)
    return Pose(points)
}
