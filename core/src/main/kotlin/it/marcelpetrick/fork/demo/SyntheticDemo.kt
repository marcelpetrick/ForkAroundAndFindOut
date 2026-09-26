// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.demo

import it.marcelpetrick.fork.detection.Detector
import it.marcelpetrick.fork.detection.Landmark
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Polygon
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.monitoring.SessionLog
import it.marcelpetrick.fork.monitoring.Settings
import kotlin.math.PI
import kotlin.math.sin

/**
 * Deterministic, clearly synthetic two-seat meal. It is generated from time alone so
 * the demo, UI tests and screenshots replay identically without any camera footage.
 * Seat 1 eats, reaches across quickly and briefly hides one arm; seat 2 rests the left
 * elbow for six seconds and then corrects it.
 */
object SyntheticDemo {
    const val LOOP_MS = 14_000L

    val table = Polygon(listOf(Point(0.15, 0.55), Point(0.85, 0.55), Point(0.9, 0.92), Point(0.1, 0.92)))

    val settings = Settings(people = 2, table = table, graceMs = 0)

    private data class Arm(
        val elbow: Point,
        val wrist: Point,
        val confidence: Double = 0.95,
    )

    /**
     * A labelled synthetic session log (the format phones record in training mode), for
     * testing the replay harness without any family data: NORMAL on seat 1 and LEFT on
     * seat 2 each loop, live states computed exactly as the app would.
     */
    fun sessionLog(
        loops: Int = 2,
        frameMs: Long = 66,
        aspect: Double = 1.0,
    ): List<String> {
        val detector = Detector(table, settings.people, settings.seats, settings.timing)
        val lines = mutableListOf(SessionLog.header("synthetic-demo", "demo", settings.copy(calibrationAspect = aspect)))
        for (time in 0L until LOOP_MS * loops step frameMs) {
            val poses = poses(time)
            lines += SessionLog.frame(time, aspect, poses, detector.process(poses, time, aspect))
            val phase = time % LOOP_MS
            if (phase in 1000L until 1000L + frameMs) lines += SessionLog.label(time, "NORMAL", 1)
            if (phase in 6000L until 6000L + frameMs) lines += SessionLog.label(time, "LEFT", 2)
        }
        return lines
    }

    fun poses(timeMs: Long): List<Pose> {
        val t = Math.floorMod(timeMs, LOOP_MS)
        val reach = if (t in 5000L..6500L) sin(PI * (t - 5000) / 1500.0) else 0.0
        val seat1Left = eating(0.3, true, t).let { if (reach > 0) it.copy(elbow = Point(0.42 + 0.08 * reach, 0.5 + 0.2 * reach)) else it }
        val seat1Right = eating(0.3, false, t).let { if (t in 10_000L..12_000L) it.copy(confidence = 0.3) else it }
        val seat2Left = if (t in 3000L..9000L) Arm(Point(0.76, 0.62), Point(0.66, 0.57)) else eating(0.7, true, t)
        return listOf(person(0.3, seat1Left, seat1Right), person(0.7, seat2Left, eating(0.7, false, t)))
    }

    private fun eating(
        center: Double,
        left: Boolean,
        t: Long,
    ): Arm {
        val side = if (left) 1 else -1
        val lift = 0.03 * sin(2 * PI * (t + if (left) 0 else 700) / 1500.0)
        return Arm(Point(center + side * 0.12, 0.5), Point(center + side * 0.05, 0.6 + lift))
    }

    private fun person(
        center: Double,
        left: Arm,
        right: Arm,
    ): Pose {
        val joints = MutableList(33) { Landmark(Point(center, 0.2), 0.95) }
        joints[11] = Landmark(Point(center + 0.08, 0.35), 0.95)
        joints[12] = Landmark(Point(center - 0.08, 0.35), 0.95)
        joints[13] = Landmark(left.elbow, left.confidence)
        joints[15] = Landmark(left.wrist, left.confidence)
        joints[14] = Landmark(right.elbow, right.confidence)
        joints[16] = Landmark(right.wrist, right.confidence)
        // Hips and legs are hidden by the table, as in real dining scenes.
        for (index in 17..32) joints[index] = Landmark(Point(center, 0.8), 0.1)
        return Pose(joints)
    }
}
