// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibilityCheckTest {
    private val hiddenElbow = Pose(pose().landmarks.toMutableList().apply { this[13] = this[13].copy(confidence = 0.2) })

    @Test
    fun passesOnlyAfterEnoughEvidenceWithEveryoneAndEveryElbowVisible() {
        val check = VisibilityCheck(people = 2)
        assertEquals(VisibilityCheck.Result(0, 0.0, 0.0, false), check.result())
        for (t in 0L..5000L step 100) check.add(listOf(pose(), pose(offset = 0.3)), t)
        assertFalse("five seconds are not enough evidence", check.result().passed)
        for (t in 5100L..9000L step 100) check.add(listOf(pose(), pose(offset = 0.3)), t)
        val result = check.result()
        assertTrue(result.passed)
        assertEquals(2, result.detected)
        assertEquals(1.0, result.armsVisible, 0.0)
        check.add(emptyList(), 9000) // duplicate timestamp ignored
        assertEquals(result, check.result())
        // A bowl hides one elbow most of the time: placement must change.
        for (t in 9100L..20_000L step 100) check.add(listOf(pose(), hiddenElbow), t)
        assertFalse(check.result().passed)
        assertEquals(2, check.result().detected)
        assertTrue(check.result().armsVisible < 0.2)
        assertEquals(10.0, check.result().seconds, 0.0)
        check.reset()
        assertFalse(check.result().passed)
    }

    @Test
    fun wrongHeadCountFails() {
        val check = VisibilityCheck(people = 3)
        for (t in 0L..10_000L step 100) check.add(listOf(pose(), pose(offset = 0.3)), t)
        assertEquals(2, check.result().detected)
        assertFalse(check.result().passed)
        val one = VisibilityCheck(people = 1)
        for (t in 0L..10_000L step 100) one.add(listOf(pose(), pose(offset = 0.3)), t)
        assertFalse("an extra person at the table also needs a new setup", one.result().passed)
    }
}
