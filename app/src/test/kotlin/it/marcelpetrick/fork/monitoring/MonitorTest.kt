// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.pose
import it.marcelpetrick.fork.detection.table
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorTest {
    @Test
    fun pauseStaleFramesAndGraceProtectAlarm() {
        assertFalse(Monitor(Settings()).start(0))
        val monitor = Monitor(Settings(table = table, people = 1, graceMs = 3000))
        monitor.frame(listOf(pose()), 0, 0, 1.0)
        assertFalse(monitor.tick(0))
        assertTrue(monitor.start(0))
        assertFalse(monitor.tick(100))
        for (time in 0L..2400L step 100) monitor.frame(listOf(pose()), time, time, 1.0)
        assertFalse(monitor.tick(2400))
        for (time in 2500L..3200L step 100) monitor.frame(listOf(pose()), time, time, 1.0)
        assertTrue(monitor.tick(3200))
        assertEquals(2, monitor.violations)
        assertTrue(monitor.confidenceCount > 0)
        assertEquals(0.99, monitor.confidenceTotal / monitor.confidenceCount, 0.001)
        monitor.frame(emptyList(), 3200, 3200, 1.0) // duplicate ignored
        monitor.frame(emptyList(), 4000, 3200, 1.0) // future ignored
        monitor.frame(emptyList(), 2000, 3200, 1.0) // stale ignored
        assertTrue(monitor.tick(3200))
        assertFalse(monitor.tick(3800))
        assertTrue(monitor.results.isEmpty())
        monitor.frame(listOf(pose()), 3900, 3900, 1.0)
        assertFalse(monitor.tick(3900))
        monitor.pause(4000)
        assertFalse(monitor.active)
        assertEquals(4000, monitor.elapsedMs)
        monitor.pause(4100)
        assertEquals(4000, monitor.elapsedMs)
        assertFalse(monitor.tick(4100))
    }

    @Test
    fun geometryChangeRequiresRecalibration() {
        for (aspect in listOf(2.0, Double.NaN, 0.0)) {
            val monitor = Monitor(Settings(table = table, calibrationAspect = 1.0))
            monitor.start(0)
            monitor.frame(listOf(pose()), 100, 100, aspect)
            assertTrue(monitor.calibrationInvalid)
            assertFalse(monitor.active)
        }
    }

    @Test
    fun audioIsConfigurableAndAlwaysStops() {
        val alarm = AlarmPolicy()
        assertEquals(Sound.NONE, alarm.update(false, AudioMode.OFF, 0, 1000))
        assertEquals(Sound.BEEP, alarm.update(true, AudioMode.ONCE, 0, 1000))
        assertEquals(Sound.NONE, alarm.update(true, AudioMode.ONCE, 2000, 1000))
        assertEquals(Sound.STOP, alarm.update(false, AudioMode.ONCE, 2100, 1000))
        assertEquals(Sound.BEEP, alarm.update(true, AudioMode.REPEAT, 2200, 1000))
        assertEquals(Sound.NONE, alarm.update(true, AudioMode.REPEAT, 2300, 1000))
        assertEquals(Sound.BEEP, alarm.update(true, AudioMode.REPEAT, 3200, 1000))
        assertEquals(Sound.START, alarm.update(true, AudioMode.CONTINUOUS, 3300, 1000))
        assertEquals(Sound.NONE, alarm.update(true, AudioMode.CONTINUOUS, 3400, 1000))
        assertEquals(Sound.STOP, alarm.update(true, AudioMode.ONCE, 3500, 1000))
        assertEquals(Sound.BEEP, alarm.update(true, AudioMode.ONCE, 3600, 1000))
        assertEquals(Sound.STOP, alarm.update(true, AudioMode.OFF, 3700, 1000))
        assertEquals(Sound.START, alarm.update(true, AudioMode.CONTINUOUS, 3800, 1000))
        assertEquals(Sound.STOP, alarm.update(false, AudioMode.CONTINUOUS, 3900, 1000))
    }
}
