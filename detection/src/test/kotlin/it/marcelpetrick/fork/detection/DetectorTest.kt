// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
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
    fun occlusionHoldsBrieflyThenLeavesUnknownAndRecoveryNeedsNewEvidence() {
        val detector = Detector(table, 1)
        for (time in 0L..2000L step 100) detector.process(listOf(pose()), time)
        val hidden = Pose(pose().landmarks.toMutableList().apply { this[13] = this[13].copy(confidence = 0.2) })
        // A dish passed in front of the left elbow: its reminder holds for a moment.
        val brief = detector.process(listOf(hidden), 2100).single()
        assertEquals(ElbowState.VIOLATION, brief.left.state)
        assertEquals(ElbowState.VIOLATION, brief.right.state)
        for (time in 2200L..2700L step 100) detector.process(listOf(hidden), time)
        assertEquals(
            ElbowState.UNKNOWN,
            detector
                .process(listOf(hidden), 2800)
                .single()
                .left.state,
        )
        // Visible again: new evidence is needed before anything is reminded.
        assertEquals(
            ElbowState.UNKNOWN,
            detector
                .process(listOf(pose()), 2900)
                .single()
                .left.state,
        )
        // The person leaves: nothing is held for a seat nobody occupies.
        assertEquals(
            ElbowState.UNKNOWN,
            detector
                .process(emptyList(), 3000)
                .single()
                .right.state,
        )
        assertEquals(
            ElbowState.UNKNOWN,
            detector
                .process(listOf(pose()), 3100)
                .single()
                .right.state,
        )
    }

    @Test
    fun aHandHiddenBehindAGlassKeepsAFullySeenRestButIsNeverGuessed() {
        val handHidden = Pose(pose().landmarks.toMutableList().apply { this[15] = this[15].copy(confidence = 0.1) })
        // Seen resting first, then the hand disappears behind a glass: the reminder continues.
        val seen = Detector(table, 1)
        for (time in 0L..2000L step 100) seen.process(listOf(pose()), time)
        for (time in 2100L..9000L step 100) {
            assertEquals(
                "t=$time",
                ElbowState.VIOLATION,
                seen
                    .process(listOf(handHidden), time)
                    .single()
                    .left.state,
            )
        }
        // At most ten seconds after the rest was last fully seen (then the short hold runs out).
        for (time in 9100L..12_600L step 100) seen.process(listOf(handHidden), time)
        assertEquals(
            ElbowState.UNKNOWN,
            seen
                .process(listOf(handHidden), 12_700)
                .single()
                .left.state,
        )
        // The elbow moves away while the hand is hidden: the bridge ends at once.
        val moved = Detector(table, 1)
        for (time in 0L..2000L step 100) moved.process(listOf(pose()), time)
        val lifted = Pose(handHidden.landmarks.toMutableList().apply { this[13] = this[13].copy(point = Point(0.3, 0.45)) })
        moved.process(listOf(lifted), 2100)
        for (time in 2200L..2800L step 100) moved.process(listOf(lifted), time)
        assertEquals(
            ElbowState.UNKNOWN,
            moved
                .process(listOf(lifted), 2900)
                .single()
                .left.state,
        )
        // Hidden from the start: never a reminder, however long the elbow rests.
        val never = Detector(table, 1)
        for (time in 0L..20_000L step 100) {
            assertEquals(
                ElbowState.UNKNOWN,
                never
                    .process(listOf(handHidden), time)
                    .single()
                    .left.state,
            )
        }
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
