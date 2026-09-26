// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatTrackerTest {
    private val leftZone = Polygon(listOf(Point(0.0, 0.0), Point(0.49, 0.0), Point(0.49, 0.5), Point(0.0, 0.5)))
    private val rightZone = Polygon(listOf(Point(0.51, 0.0), Point(1.0, 0.0), Point(1.0, 0.5), Point(0.51, 0.5)))

    @Test
    fun assignsStableSeatsWhenInferenceOrderChanges() {
        for (zones in listOf(emptyList(), listOf(leftZone, rightZone))) {
            val tracker = SeatTracker(2, zones)
            val a = pose(offset = -0.25)
            val b = pose(offset = 0.25)
            assertEquals(mapOf(0 to a, 1 to b), tracker.assign(listOf(b, a)))
            assertEquals(mapOf(0 to a, 1 to b), tracker.assign(listOf(a, b)))
            assertEquals(mapOf(1 to b), tracker.assign(listOf(b)))
            assertTrue(tracker.assign(emptyList()).isEmpty())
        }
    }

    @Test
    fun rejectsUncertainAndAmbiguousAssignments() {
        assertThrows(IllegalArgumentException::class.java) { SeatTracker(5) }
        assertTrue(SeatTracker(1).assign(listOf(Pose(emptyList()))).isEmpty())
        assertTrue(SeatTracker(2, listOf(leftZone, leftZone)).assign(listOf(pose(offset = -0.2))).isEmpty())
        assertTrue(SeatTracker(1, listOf(leftZone)).assign(listOf(pose(offset = -0.2), pose(offset = -0.21))).isEmpty())
        val tracker = SeatTracker(1)
        tracker.assign(listOf(pose()))
        assertTrue(tracker.assign(listOf(pose(offset = -0.01), pose(offset = 0.01))).isEmpty())
        tracker.assign(listOf(pose()))
        assertTrue(tracker.assign(listOf(pose(offset = 0.3))).isEmpty())
        val crowded = SeatTracker(2)
        crowded.assign(listOf(pose(offset = -0.05), pose(offset = 0.05)))
        assertTrue(crowded.assign(listOf(pose())).isEmpty())
    }
}
