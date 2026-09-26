// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.demo.SyntheticDemo
import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.detection.Landmark
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.Timing
import it.marcelpetrick.fork.detection.pose
import it.marcelpetrick.fork.detection.table
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogTest {
    @Test
    fun jsonReaderHandlesTheFormatAndRejectsGarbage() {
        val parsed =
            Json.parse(
                """ { "a" : [1, -2.5e1, true, false, null], "s": "q\"\\\n\t\r\b\f\u0041/" , "o": {} , "e": [] } """,
            ) as Map<*, *>
        assertEquals(listOf(1.0, -25.0, true, false, null), parsed["a"])
        assertEquals("q\"\\\n\t\r\b\u000cA/", parsed["s"])
        assertEquals(emptyMap<String, Any>(), parsed["o"])
        assertEquals(emptyList<Any>(), parsed["e"])
        assertEquals("\"a\\\"b\\\\\\u0001\"", Json.quote("a\"b\\\u0001"))
        assertEquals("x\"y", Json.parse(Json.quote("x\"y")))
        for (bad in listOf("", "[1,", "{\"a\" 1}", "tru", "\"open", "[1] x", "{1:2}", "-")) {
            assertThrows(bad, IllegalArgumentException::class.java) { Json.parse(bad) }
        }
    }

    @Test
    fun replayOfTheLabelledSyntheticSessionReproducesLiveDecisions() {
        val lines = SyntheticDemo.sessionLog(loops = 2)
        assertTrue(lines.none { it.contains("image", ignoreCase = true) && !it.contains("\"imageRecorded\":false") })
        val recording = SessionLog.read(lines.asSequence())
        assertEquals("synthetic-demo", recording.session)
        assertEquals(SyntheticDemo.settings.people, recording.settings.people)
        assertEquals(4, recording.labels.size)
        val report = Replay.run(recording)
        assertEquals(2, report.reminders) // seat 2 rests the left elbow once per loop
        assertEquals(0, report.remindersNearNegativeLabels)
        assertEquals(2, report.positiveLabels)
        assertEquals(2, report.positiveDetected)
        assertEquals(1.0, report.agreement, 0.0)
        assertTrue(report.unknownFraction in 0.0..0.2)
        assertTrue(report.describe().contains("2 reminders"))
        // A tuning experiment: a 10 s dwell suppresses every reminder in this scenario.
        val strict = Replay.run(recording, Timing(triggerMs = 10_000))
        assertEquals(0, strict.reminders)
        assertEquals(0, strict.positiveDetected)
        assertTrue(strict.agreement < 1.0)
    }

    @Test
    fun formatRoundTripsGeometryAndToleratesATruncatedTail() {
        val settings =
            Settings(
                people = 1,
                table = table,
                seats = listOf(table),
                calibrationAspect = 0.5625,
                calibrationRotation = 90,
                timing = Timing(maxGapMs = 700),
            )
        val detector =
            it.marcelpetrick.fork.detection
                .Detector(table, 1)
        val results = detector.process(listOf(pose()), 0)
        val lines =
            listOf(
                SessionLog.header("s1", "0.8.0", settings),
                SessionLog.frame(0, 0.5625, listOf(pose()), results),
                SessionLog.label(10, "FALSE_ALARM", 1),
                "{\"type\":\"frame\",\"t\":1",
            )
        val recording = SessionLog.read(lines.asSequence())
        val restored = recording.settings
        assertEquals(table.points, restored.table!!.points)
        assertEquals(listOf(table.points), restored.seats.map { it.points })
        assertEquals(0.5625, restored.calibrationAspect, 0.0)
        assertEquals(90, restored.calibrationRotation)
        assertEquals(1, restored.people)
        assertEquals(700, recording.settings.timing.maxGapMs)
        assertEquals(1, recording.frames.size)
        assertEquals(
            pose().landmarks[13].point,
            recording.frames
                .single()
                .poses
                .single()
                .landmarks[13]
                .point,
        )
        assertEquals(ElbowState.UNKNOWN, recording.frames.single().live[1 to true])
        assertEquals(Label(10, "FALSE_ALARM", 1), recording.labels.single())
        assertThrows(IllegalArgumentException::class.java) { SessionLog.label(0, "SMILE", 1) }
        assertThrows(IllegalArgumentException::class.java) { SessionLog.read(sequenceOf(lines[1])) }
        assertThrows(IllegalArgumentException::class.java) { SessionLog.read(sequenceOf(lines[0].replace("\"schema\":1", "\"schema\":9"))) }
        assertThrows(IllegalArgumentException::class.java) { SessionLog.read(sequenceOf(lines[0], "garbage", lines[1])) }
        val wrongShape = assertThrows(IllegalArgumentException::class.java) { SessionLog.read(sequenceOf(lines[0], "[]", lines[1])) }
        assertTrue(wrongShape.message!!.contains("line 2"))
        // A non-finite landmark never produces invalid JSON; it is stored as unseen.
        val broken = Pose(pose().landmarks.toMutableList().apply { this[13] = Landmark(Point(Double.NaN, 0.5), 0.9) })
        val nanLine = SessionLog.frame(5, Double.POSITIVE_INFINITY, listOf(broken), results)
        val parsed = SessionLog.read(sequenceOf(lines[0], nanLine, lines[1]))
        assertEquals(
            0.0,
            parsed.frames
                .first()
                .poses
                .single()
                .landmarks[13]
                .confidence,
            0.0,
        )
        assertEquals(0.0, parsed.frames.first().aspect, 0.0)
        val untabled = SessionLog.read(sequenceOf(SessionLog.header("s2", "x", Settings())))
        assertThrows(IllegalArgumentException::class.java) { Replay.run(untabled) }
        val empty = Replay.run(SessionLog.read(sequenceOf(SessionLog.header("s3", "x", Settings(table = table)))))
        assertEquals(0, empty.frames)
        assertEquals(0.0, empty.unknownFraction, 0.0)
        assertEquals(1.0, empty.agreement, 0.0)
        assertFalse(empty.describe().isEmpty())
    }

    @Test
    fun replayMirrorsTheMonitorOnASlowPhone() {
        // 2.5 FPS: the live monitor widens its gap budget to 1.2 s; a bare detector would not.
        val settings = Settings(people = 1, table = table, graceMs = 0, calibrationAspect = 1.0, calibrationRotation = 90)
        val live = Monitor(settings)
        live.start(0)
        val lines = mutableListOf(SessionLog.header("slow", "test", settings))
        for (time in 0L..8000L step 400) {
            if (live.frame(listOf(pose()), time, time + 300, 1.0, 90)) lines += SessionLog.frame(time, 1.0, listOf(pose()), live.results)
            live.tick(time + 300)
        }
        assertTrue(
            live.results
                .single()
                .left.state == ElbowState.VIOLATION,
        )
        val report = Replay.run(SessionLog.read(lines.asSequence()), warmupMs = 0)
        assertEquals(1.0, report.agreement, 0.0)
        assertEquals(live.violations, report.reminders)
        // A long silence (camera covered) expires evidence exactly as the phone's watchdog does.
        val gap = lines + SessionLog.frame(20_000, 1.0, listOf(pose()), emptyList())
        assertEquals(live.violations, Replay.run(SessionLog.read(gap.asSequence())).reminders)
    }
}
