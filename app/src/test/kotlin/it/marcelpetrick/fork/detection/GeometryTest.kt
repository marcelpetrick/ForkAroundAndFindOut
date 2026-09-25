// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

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

class GeometryTest {
    @Test
    fun seatRegionsMustNotOverlap() {
        fun square(
            x: Double,
            y: Double,
            size: Double = 0.2,
        ) = Polygon(listOf(Point(x, y), Point(x + size, y), Point(x + size, y + size), Point(x, y + size)))
        assertTrue(square(0.1, 0.1).overlaps(square(0.2, 0.2)))
        assertTrue(square(0.1, 0.1).overlaps(square(0.3, 0.1))) // shared edge
        assertTrue(square(0.1, 0.1, 0.6).overlaps(square(0.3, 0.3))) // containment
        assertFalse(square(0.1, 0.1).overlaps(square(0.5, 0.1)))
        val diamond = Polygon(listOf(Point(0.5, 0.3), Point(0.7, 0.5), Point(0.5, 0.7), Point(0.3, 0.5)))
        assertFalse(diamond.overlaps(square(0.1, 0.1, 0.25))) // bounding boxes overlap, shapes do not
    }

    @Test
    fun distanceAndCalibration() {
        assertEquals(5.0, Point(0.0, 0.0).distance(Point(3.0, 4.0)), 1e-8)
        assertEquals(Point(0.5, 0.7), table.center())
        assertEquals(0.2, table.signedDistance(Point(0.5, 0.7)), 1e-8)
        assertEquals(-0.2, table.signedDistance(Point(0.5, 0.3)), 1e-8)
        assertEquals(-0.2, table.signedDistance(Point(0.0, 0.6), 2.0), 1e-8)
        assertTrue(table.contains(Point(0.1, 0.5)))
        assertFalse(table.contains(Point(0.01, 0.5)))
        assertTrue(Polygon(table.points.reversed()).contains(Point(0.5, 0.7)))
        assertEquals(90.0, angle(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0)), 1e-8)
        assertEquals(0.0, angle(Point(0.0, 0.0), Point(0.0, 0.0), Point(1.0, 1.0)), 1e-8)
    }

    @Test
    fun rejectsInvalidRegionsAndCoordinates() {
        val invalid =
            listOf(
                emptyList(),
                List(4) { Point(0.5, 0.5) },
                table.points.map { Point(it.x + 3, it.y) },
                listOf(table.points[0], table.points[2], table.points[1], table.points[3]),
            )
        invalid.forEach { points -> assertThrows(IllegalArgumentException::class.java) { Polygon(points) } }
        assertFalse(Point(Double.NaN, 0.0).inImage())
        assertFalse(Point(0.0, Double.POSITIVE_INFINITY).inImage())
        assertNull(Pose(emptyList()).center())
        assertNull(Pose(listOf(Landmark(Point(0.0, 0.0), Double.NaN))).joint(0))
        val partial = pose().landmarks.toMutableList().apply { this[12] = this[12].copy(confidence = 0.1) }
        assertNull(Pose(partial).center())
    }
}
