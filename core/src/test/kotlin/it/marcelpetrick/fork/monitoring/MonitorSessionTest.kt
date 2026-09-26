// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.pose
import it.marcelpetrick.fork.detection.table
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorSessionTest {
    private val settings =
        Settings(people = 1, table = table, graceMs = 3000, audio = AudioMode.ONCE, calibrationAspect = 1.0, calibrationRotation = 90)
    private val resting = pose()
    private val clear = pose(Point(0.04, 0.6), Point(0.96, 0.6))

    /** Feeds frames every 100 ms from [from] for [ms] and returns the last state. */
    private fun MonitorSession.run(
        from: Long,
        ms: Long,
        person: Pose?,
        sounds: MutableList<Sound> = mutableListOf(),
    ): MonitorUiState {
        var state: MonitorUiState? = null
        for (t in from until from + ms step 100) {
            if (person != null) frame(listOf(person), t, t, 1.0, 90)
            state = tick(t)
            if (state.sound != Sound.NONE) sounds += state.sound
        }
        return state!!
    }

    @Test
    fun aMealFromGraceThroughReminderThankYouRestAndSummary() {
        val session = MonitorSession(settings, "meal", 0)
        assertEquals("meal", session.id)
        val start = session.tick(0)
        assertEquals(Status.GRACE, start.status)
        assertEquals(3, start.countdown)
        assertEquals(Status.WATCHING, session.run(0, 3100, clear).status)
        val sounds = mutableListOf<Sound>()
        val reminding = session.run(3100, 3000, resting, sounds)
        assertEquals(Status.REMINDING, reminding.status)
        assertTrue(reminding.warning)
        assertEquals(1 to true, reminding.reminder)
        assertEquals(listOf(Sound.BEEP), sounds)
        val thanks = session.run(6000, 800, clear, sounds)
        assertTrue("a real correction earns a thank-you", thanks.thanks)
        assertNull(thanks.reminder)
        assertFalse(session.run(6800, 2500, clear).thanks)
        // False alarm: silence and a 30 s rest even though the elbow rests again.
        session.run(9300, 3000, resting)
        assertEquals(Sound.STOP, session.falseAlarm(12_300))
        val rest = session.run(12_300, 2000, resting)
        assertEquals(Status.RESTING, rest.status)
        assertEquals(29, rest.countdown) // 28.1 s left, rounded up
        assertFalse(rest.warning)
        session.missedViolation()
        // Pause silences; resume restarts grace and ends the rest.
        assertEquals(Sound.STOP, session.togglePause(14_300))
        val paused = session.tick(15_000)
        assertEquals(Status.PAUSED, paused.status)
        assertTrue(paused.paused)
        assertEquals(Sound.NONE, session.togglePause(20_000))
        assertEquals(Status.GRACE, session.tick(20_000).status)
        val summary = session.summary(20_000)
        assertEquals(2, summary.reminders)
        assertEquals(mapOf(1 to 2), summary.remindersBySeat)
        assertEquals(1, summary.falseAlarms)
        assertEquals(1, summary.missedViolations)
        assertEquals(14, summary.activeSeconds)
        assertTrue("the calm stretch before the first reminder counts", summary.longestCalmSeconds >= 4)
        assertTrue(summary.meanConfidence!! > 0.9)
    }

    @Test
    fun nobodyInViewForAWhileSuggestsThePhoneMoved() {
        val session = MonitorSession(settings.copy(graceMs = 0), "empty", 0)
        assertEquals(Status.WAITING, session.run(0, 5000, null).status)
        assertEquals(Status.NOBODY_FOR_A_WHILE, session.run(5000, 16_000, null).status)
        assertEquals(Status.WATCHING, session.run(21_000, 1000, clear).status)
        // Only frames with a person count as "seen": an empty table also triggers the hint.
        assertEquals(Status.NOBODY_FOR_A_WHILE, session.run(22_000, 21_000, Pose(emptyList())).status)
        val summary = MonitorSession(settings, "fresh", 0).summary(0)
        assertEquals(0, summary.reminders)
        assertNull(summary.meanConfidence)
    }

    @Test
    fun slowAndTooSlowPhonesAreExplainedAndSuspendSilences() {
        val session = MonitorSession(settings.copy(graceMs = 0), "slow", 0)
        for (t in 0L..4000L step 300) {
            session.frame(listOf(clear), t, t, 1.0, 90)
            session.tick(t)
        }
        assertEquals(Status.SLOW, session.tick(4000).status)
        for (t in 4800L..12_000L step 800) {
            session.frame(listOf(clear), t, t, 1.0, 90)
            session.tick(t)
        }
        assertEquals(Status.TOO_SLOW, session.tick(12_000).status)
        assertEquals(Sound.STOP, session.suspend(12_100))
        assertFalse(session.active)
        assertEquals(Sound.STOP, session.suspend(12_200))
        assertFalse(session.frame(listOf(clear), 12_300, 12_300, 1.0, 90))
    }

    @Test
    fun elbowsDuringGraceAreNotCountedAsReminders() {
        val session = MonitorSession(settings, "grace", 0)
        session.run(0, 1800, resting) // violation reached and cleared inside the grace period
        session.run(1800, 3000, clear)
        val summary = session.summary(4800)
        assertEquals(0, summary.reminders)
        assertEquals(emptyMap<Int, Int>(), summary.remindersBySeat)
        assertTrue("the detector itself did see it", session.monitor.violations > 0)
    }

    @Test
    fun pausingDuringAReminderEndsItForTheCalmRecord() {
        val session = MonitorSession(settings.copy(graceMs = 0), "pause", 0)
        session.run(0, 5000, clear)
        assertEquals(Status.REMINDING, session.run(5000, 3000, resting).status)
        session.togglePause(8000)
        session.togglePause(9000)
        session.run(9000, 2000, clear)
        val summary = session.summary(11_000)
        assertEquals(1, summary.reminders)
        assertEquals(mapOf(1 to 1), summary.remindersBySeat)
        // The calm before the reminder (6–7 s) is the record, not the 10 s that include it.
        assertTrue("calm ${summary.longestCalmSeconds}", summary.longestCalmSeconds in 5..7)
    }

    @Test
    fun anArmHiddenMostOfTheTimeIsNamedSoThePotCanBeMoved() {
        val session = MonitorSession(settings.copy(graceMs = 0), "pot", 0)
        val potInFront = Pose(clear.landmarks.toMutableList().apply { this[13] = this[13].copy(confidence = 0.1) })
        assertNull("not before the person was in view long enough", session.run(0, 15_000, potInFront).hiddenArm)
        assertEquals(1 to true, session.run(15_000, 10_000, potInFront).hiddenArm)
        // The pot is moved: the hint fades once the arm is seen again.
        assertNull(session.run(25_000, 15_000, clear).hiddenArm)
        // Paused: no hints.
        session.run(40_000, 25_000, potInFront)
        session.togglePause(65_000)
        assertNull(session.tick(65_100).hiddenArm)
    }
}
