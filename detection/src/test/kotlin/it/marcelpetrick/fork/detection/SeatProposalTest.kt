// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatProposalTest {
    /** A wide table in the middle of the image: long top and bottom sides. */
    private val wide = Polygon(listOf(Point(0.2, 0.4), Point(0.8, 0.4), Point(0.8, 0.6), Point(0.2, 0.6)))

    private fun List<Polygon>.noOverlaps() = indices.all { i -> indices.all { j -> i == j || !this[i].overlaps(this[j]) } }

    @Test
    fun peopleGoToTheLongSidesFirstAndEveryRegionIsValid() {
        for (people in 1..4) {
            val seats = SeatProposal.propose(wide, people)
            assertEquals("people $people", people, seats.size)
            assertTrue(seats.noOverlaps())
        }
        // Two people: one above the far side, one below the near side, never at the ends.
        val two = SeatProposal.propose(wide, 2)
        assertTrue(two.any { it.center().y < 0.4 } && two.any { it.center().y > 0.6 })
        assertTrue(two.all { it.center().x in 0.3..0.7 })
        // Four people on a 3:1 table: two per long side, none at the short ends.
        val four = SeatProposal.propose(wide, 4)
        assertEquals(2, four.count { it.center().y < 0.4 })
        assertEquals(2, four.count { it.center().y > 0.6 })
    }

    @Test
    fun aSquareTableSeatsOnePersonPerSideAndShouldersNearTheEdgeAreInside() {
        val square = Polygon(listOf(Point(0.35, 0.35), Point(0.65, 0.35), Point(0.65, 0.65), Point(0.35, 0.65)))
        val seats = SeatProposal.propose(square, 4)
        assertEquals(4, seats.size)
        // A person sitting at the far side has shoulders just above the edge, and one leaning
        // in overlaps the tabletop slightly: both fall in the far seat, not in the table centre.
        val far = seats.single { it.center().y < 0.35 }
        assertTrue(far.contains(Point(0.5, 0.3)))
        assertTrue(far.contains(Point(0.5, 0.36)))
        assertFalse(far.contains(Point(0.5, 0.5)))
    }

    @Test
    fun regionsAreClippedToTheImageOrLeftOut() {
        // The near edge sits on the image bottom: its band has no room outside and is dropped
        // or clipped, but whatever is proposed stays inside the image and valid.
        val low = Polygon(listOf(Point(0.1, 0.5), Point(0.9, 0.5), Point(0.9, 1.0), Point(0.1, 1.0)))
        val seats = SeatProposal.propose(low, 2)
        assertTrue(seats.isNotEmpty())
        assertTrue(seats.all { seat -> seat.points.all { it.inImage() } })
        assertTrue(seats.noOverlaps())
        assertTrue(seats.size <= 2)
    }
}
