// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TemporalFilterTest {
    @Test
    fun briefCrossingNeverAlarms() {
        val filter = TemporalFilter()
        listOf(0.20, 0.92, 0.85, 0.30).forEachIndexed { i, p ->
            assertNotEquals(ElbowState.VIOLATION, filter.update(i * 200L, p))
        }
        assertEquals(ElbowState.CLEAR, filter.state)
    }

    @Test
    fun sustainedContactHysteresisClearAndCooldown() {
        val filter = TemporalFilter(Timing(triggerMs = 800))
        assertEquals(ElbowState.SUSPECT, filter.update(0, 0.85))
        for (time in 200L..600L step 200) assertEquals(ElbowState.SUSPECT, filter.update(time, 0.92))
        assertEquals(ElbowState.VIOLATION, filter.update(800, 0.92))
        assertEquals(ElbowState.VIOLATION, filter.update(1000, 0.5))
        assertEquals(ElbowState.VIOLATION, filter.update(1200, 0.2))
        assertEquals(ElbowState.VIOLATION, filter.update(1400, 0.6)) // cancels clearing
        filter.update(1600, 0.2)
        filter.update(1800, 0.2)
        filter.update(2000, 0.2)
        assertEquals(ElbowState.CLEAR, filter.update(2200, 0.2))
        for (time in 2400L..3600L step 200) assertEquals(ElbowState.CLEAR, filter.update(time, 0.95))
        assertEquals(ElbowState.SUSPECT, filter.update(3800, 0.95))
    }

    @Test
    fun hiddenJointsHoldARunningReminderBrieflyButNeverBuildOne() {
        val filter = TemporalFilter(Timing(triggerMs = 0))
        filter.update(0, 0.99)
        assertEquals(ElbowState.VIOLATION, filter.update(100, 0.99))
        // A dish passes in front for half a second: the reminder stays on, without flicker.
        for (time in 200L..700L step 100) assertEquals(ElbowState.VIOLATION, filter.update(time, null))
        assertEquals(ElbowState.VIOLATION, filter.update(800, 0.99))
        // Hidden for longer than the hold: the reminder ends.
        for (time in 900L..1500L step 100) filter.update(time, null)
        assertEquals(ElbowState.UNKNOWN, filter.update(1600, null))
        // Hidden while only suspected: nothing is held, nothing builds up.
        val suspect = TemporalFilter()
        assertEquals(ElbowState.SUSPECT, suspect.update(0, 0.99))
        assertEquals(ElbowState.UNKNOWN, suspect.update(100, null))
        // Without a hold, hidden joints silence at once.
        val strict = TemporalFilter(Timing(triggerMs = 0, holdMs = 0))
        strict.update(0, 0.99)
        strict.update(100, 0.99)
        assertEquals(ElbowState.UNKNOWN, strict.update(200, null))
        // A frame gap is never bridged by the hold.
        val gap = TemporalFilter(Timing(triggerMs = 0))
        gap.update(0, 0.99)
        gap.update(100, 0.99)
        assertEquals(ElbowState.UNKNOWN, gap.update(700, null))
        // Too little evidence (sparse frames, not hidden joints) is never held either.
        val sparse = TemporalFilter(Timing(triggerMs = 0))
        sparse.update(0, 0.99)
        sparse.update(100, 0.99)
        assertEquals(ElbowState.UNKNOWN, sparse.update(200, null, hidden = false))
        assertThrows(IllegalArgumentException::class.java) { Timing(holdMs = -1) }
    }

    @Test
    fun malformedStaleOrBackwardsEvidenceSilencesImmediately() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1)) {
            val filter = TemporalFilter(Timing(triggerMs = 0))
            assertEquals(ElbowState.SUSPECT, filter.update(0, 0.99))
            assertEquals(ElbowState.VIOLATION, filter.update(100, 0.99))
            assertEquals(ElbowState.UNKNOWN, filter.update(200, bad))
            assertEquals(ElbowState.SUSPECT, filter.update(300, 0.99))
        }
        for (time in listOf(-1L, 100L, 601L)) {
            val filter = TemporalFilter()
            filter.update(100, 0.99)
            assertEquals(ElbowState.UNKNOWN, filter.update(time, 0.99))
        }
        assertThrows(IllegalArgumentException::class.java) { Timing(trigger = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Timing(clear = 0.9) }
        assertThrows(IllegalArgumentException::class.java) { Timing(triggerMs = -1) }
        assertThrows(IllegalArgumentException::class.java) { Timing(maxGapMs = 0) }
    }
}
