// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorTest {
    private fun replay(source: (Long) -> List<Pose>): List<SeatResult> {
        val detector = Detector(table, 1)
        var result = emptyList<SeatResult>()
        for (time in 0L..2400L step 100) result = detector.process(source(time), time)
        return result
    }

    @Test
    fun acceptanceCasesForEveryArm() {
        val outside = Point(0.04, 0.6)
        val rightOutside = Point(0.96, 0.6)
        val cases =
            listOf(
                Triple("knife and fork, elbows outside", pose(outside, rightOutside), ElbowState.CLEAR to ElbowState.CLEAR),
                Triple("left supported", pose(right = rightOutside), ElbowState.VIOLATION to ElbowState.CLEAR),
                Triple("right supported", pose(left = outside), ElbowState.CLEAR to ElbowState.VIOLATION),
                Triple("both supported", pose(), ElbowState.VIOLATION to ElbowState.VIOLATION),
            )
        for ((name, p, expected) in cases) {
            val seat = replay { listOf(p) }.single()
            assertEquals(name, expected, seat.left.state to seat.right.state)
            assertNotNull(seat.left.features)
            assertEquals(1, seat.seat)
            assertEquals(p, seat.pose)
        }
        assertEquals(ElbowState.UNKNOWN, replay { emptyList() }.single().left.state)
    }

    @Test
    fun movingReachingPassingAndBriefContactNeverAlarm() {
        for (scenario in listOf<(Long) -> List<Pose>>(
            { t -> listOf(pose(Point(0.3 + (t % 600) / 1200.0, 0.55))) },
            { t -> listOf(pose(offset = (t % 1000) / 4000.0)) },
            { t -> listOf(pose(left = if (t in 1000..1300) Point(0.4, 0.55) else Point(0.04, 0.6))) },
        )) {
            val detector = Detector(table, 1)
            for (time in 0L..3000L step 100) {
                assertNotEquals(
                    ElbowState.VIOLATION,
                    detector
                        .process(scenario(time), time)
                        .single()
                        .left.state,
                )
            }
        }
    }

    @Test
    fun occlusionLeavesUnknownAndRecoveryNeedsNewEvidence() {
        val detector = Detector(table, 1)
        for (time in 0L..2000L step 100) detector.process(listOf(pose()), time)
        val hidden = pose().landmarks.toMutableList().apply { this[13] = this[13].copy(confidence = 0.2) }
        val result = detector.process(listOf(Pose(hidden)), 2100).single()
        assertEquals(ElbowState.UNKNOWN, result.left.state)
        assertEquals(ElbowState.VIOLATION, result.right.state)
        assertEquals(
            ElbowState.UNKNOWN,
            detector
                .process(listOf(pose()), 2200)
                .single()
                .left.state,
        )
        assertEquals(
            ElbowState.UNKNOWN,
            detector
                .process(emptyList(), 2300)
                .single()
                .right.state,
        )
        assertEquals(
            ElbowState.UNKNOWN,
            detector
                .process(listOf(pose()), 2400)
                .single()
                .right.state,
        )
    }

    @Test
    fun clearAfterCorrectionAndMissingFrames() {
        val detector = Detector(table, 1)
        for (time in 0L..2000L step 100) detector.process(listOf(pose()), time)
        val corrected = pose(Point(0.02, 0.4), Point(0.98, 0.4))
        for (time in 2100L..3000L step 100) detector.process(listOf(corrected), time)
        assertEquals(
            ElbowState.CLEAR,
            detector
                .process(listOf(corrected), 3100)
                .single()
                .left.state,
        )
        assertEquals(
            ElbowState.UNKNOWN,
            detector
                .process(listOf(pose()), 4000)
                .single()
                .left.state,
        )
    }

    @Test
    fun featuresAreFiniteAndBodyRelative() {
        val classifier = ArmClassifier()
        var evidence = classifier.evaluate(pose(), true, table, 0, 1.5)
        assertNull(evidence.score)
        for (time in 100L..1600L step 100) evidence = classifier.evaluate(pose(), true, table, time, 1.5)
        assertEquals(0.95, evidence.score!!, 0.001)
        val f = evidence.features!!
        assertTrue(f.elbowDistance > 0)
        assertTrue(f.wristDistance < 0)
        assertTrue(f.upperLength > 0)
        assertTrue(f.foreLength > 0)
        assertTrue(f.elbowAngle in 25.0..155.0)
        assertTrue(f.upperAngle.isFinite())
        assertTrue(f.foreAngle.isFinite())
        assertTrue(f.wristHeight < 0)
        assertTrue(f.shoulderHeight > 0)
        assertEquals(0.0, f.shoulderTilt, 0.001)
        assertNotNull(f.torsoTilt)
        assertEquals(0.0, f.speed, 0.001)
        assertEquals(0.0, f.maxSpeed, 0.001)
        assertEquals(0.0, f.variance, 0.001)
        assertEquals(0.99, f.confidence, 0.001)
        assertNull(classifier.evaluate(pose(), true, table, 1600, 1.0).score)
        val hiddenHip = pose().landmarks.toMutableList().apply { this[23] = this[23].copy(confidence = 0.0) }
        assertNull(classifier.evaluate(Pose(hiddenHip), true, table, 1800, 1.0).features!!.torsoTilt)
        assertNull(classifier.evaluate(pose(), true, table, 2000, Double.NaN).features)
        assertNull(classifier.evaluate(pose(), true, table, 2100, -1.0).features)
        assertNull(classifier.evaluate(Pose(emptyList()), true, table, 2200, 1.0).features)
    }
}
