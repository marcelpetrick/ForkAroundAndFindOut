// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.demo

import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.monitoring.Monitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticDemoTest {
    @Test
    fun scenarioExercisesRestingReachingOcclusionAndClearingAtAnyAspect() {
        for (aspect in listOf(0.45, 1.0, 1.8)) {
            val monitor = Monitor(SyntheticDemo.settings)
            assertTrue(monitor.start(0))
            val seen = mutableMapOf<Long, List<ElbowState>>()
            for (time in 0L..SyntheticDemo.LOOP_MS * 2 step 66) {
                monitor.frame(SyntheticDemo.poses(time), time, time, aspect)
                assertEquals(time % SyntheticDemo.LOOP_MS in 5050L..9550L, monitor.tick(time)) // 1s motion window + 1s dwell; 0.5s clear
                val seats = monitor.results
                seen[time] = seats.flatMap { listOf(it.left.state, it.right.state) }
                assertFalse("reaching/eating seat never alarms", seats[0].left.state == ElbowState.VIOLATION)
                assertFalse(seats[0].right.state == ElbowState.VIOLATION)
                assertFalse(seats[1].right.state == ElbowState.VIOLATION)
            }
            assertTrue(seen.getValue(11_022)[1] == ElbowState.UNKNOWN) // hidden arm is not "good posture"
            assertEquals(2, monitor.violations)
        }
        assertEquals(SyntheticDemo.poses(1000), SyntheticDemo.poses(1000 + SyntheticDemo.LOOP_MS))
        assertEquals(SyntheticDemo.poses(1000), SyntheticDemo.poses(1000 - SyntheticDemo.LOOP_MS))
    }
}
