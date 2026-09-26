// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.detection

import kotlin.math.atan2
import kotlin.math.max

/** MediaPipe Pose Landmarker indices of the joints this project uses. */
object Joint {
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
}

data class Landmark(
    val point: Point,
    val confidence: Double,
) {
    fun visible(): Boolean = point.inImage() && confidence.isFinite() && confidence >= VISIBLE

    companion object {
        /** Minimum min(visibility, presence) for a joint to count as seen. */
        const val VISIBLE = 0.7
    }
}

data class Pose(
    val landmarks: List<Landmark>,
) {
    fun joint(index: Int): Landmark? = landmarks.getOrNull(index)?.takeIf { it.visible() }

    // Shoulder center stays consistent even when hips become visible mid-session.
    fun center(): Point? {
        val left = joint(Joint.LEFT_SHOULDER)?.point ?: return null
        val right = joint(Joint.RIGHT_SHOULDER)?.point ?: return null
        return Point((left.x + right.x) / 2, (left.y + right.y) / 2)
    }
}

data class Features(
    val elbowDistance: Double,
    val wristDistance: Double,
    val upperLength: Double,
    val foreLength: Double,
    val elbowAngle: Double,
    val upperAngle: Double,
    val foreAngle: Double,
    val wristHeight: Double,
    val shoulderHeight: Double,
    val shoulderTilt: Double,
    val torsoTilt: Double?,
    val speed: Double,
    val maxSpeed: Double,
    val variance: Double,
    val confidence: Double,
)

data class Evidence(
    val score: Double?,
    val features: Features?,
    /** True when the score is missing because arm joints are hidden (not for lack of history). */
    val hidden: Boolean = false,
)

/**
 * A rolling second of normalized motion distinguishes a reach from supported posture.
 *
 * A set table hides hands: when an arm was *fully* seen resting (shoulder, elbow and wrist)
 * and then only the wrist disappears behind a glass, pot or plate, the evidence is bridged —
 * as long as the elbow stays still, near where it rested, for at most [BRIDGE_MS]. A hand
 * hidden from the start is never guessed.
 */
class ArmClassifier {
    private data class Sample(
        val time: Long,
        val point: Point,
    )

    private val history = ArrayDeque<Sample>()

    /** Where the elbow rested at the last fully supported frame, and when. */
    private var restedAt: Point? = null
    private var restedTime = 0L

    /** Motion of the elbow over the rolling window, in shoulder widths (per second). */
    private data class Motion(
        val speed: Double,
        val maxSpeed: Double,
        val variance: Double,
    )

    fun evaluate(
        pose: Pose,
        left: Boolean,
        table: Polygon,
        timeMs: Long,
        aspect: Double,
        maxGapMs: Long = 500,
    ): Evidence {
        val upper = upperArm(pose, left)
        if (upper == null || !(aspect > 0 && aspect.isFinite())) {
            forget()
            return Evidence(null, null, hidden = true)
        }
        val (shoulder, elbow, opposite) = upper
        val s = shoulder.point.metric(aspect)
        val e = elbow.point.metric(aspect)
        val scale = max(s.distance(opposite.point.metric(aspect)), MIN_SCALE)
        val motion = track(e, timeMs, maxGapMs, scale)
        // Only the hand is hidden: bridge a rest that was fully seen, never start one.
        val wrist = pose.joint(if (left) Joint.LEFT_WRIST else Joint.RIGHT_WRIST) ?: return bridge(e, timeMs, scale, motion)
        val hip = pose.joint(if (left) Joint.LEFT_HIP else Joint.RIGHT_HIP)?.point?.metric(aspect)
        val features = features(Arm(shoulder, elbow, wrist, opposite), hip, table, aspect, scale, motion)
        val enough = history.size >= MIN_SAMPLES && timeMs - history.first().time >= MIN_SPAN_MS
        val supported = enough && supported(features)
        if (enough) restedAt = if (supported) e else null
        if (supported) restedTime = timeMs
        // Deliberately conservative heuristic, not a calibrated probability of physical contact.
        val score = if (supported) SUPPORTED else UNSUPPORTED
        return Evidence(score.takeIf { enough }, features)
    }

    /** Shoulder, elbow and the opposite shoulder (for the scale), or null if any is hidden. */
    private fun upperArm(
        pose: Pose,
        left: Boolean,
    ): Triple<Landmark, Landmark, Landmark>? {
        val shoulder = pose.joint(if (left) Joint.LEFT_SHOULDER else Joint.RIGHT_SHOULDER) ?: return null
        val elbow = pose.joint(if (left) Joint.LEFT_ELBOW else Joint.RIGHT_ELBOW) ?: return null
        val opposite = pose.joint(if (left) Joint.RIGHT_SHOULDER else Joint.LEFT_SHOULDER) ?: return null
        return Triple(shoulder, elbow, opposite)
    }

    /** Adds the elbow to the rolling window (a gap or a step back in time restarts it). */
    private fun track(
        e: Point,
        timeMs: Long,
        maxGapMs: Long,
        scale: Double,
    ): Motion {
        val last = history.lastOrNull()
        if (last != null && (timeMs <= last.time || timeMs - last.time > maxGapMs)) forget()
        history.addLast(Sample(timeMs, e))
        while (history.size > 1 && timeMs - history.first().time > WINDOW_MS) history.removeFirst()
        val mean = Point(history.sumOf { it.point.x } / history.size, history.sumOf { it.point.y } / history.size)
        val variance =
            history.sumOf {
                val d = it.point.distance(mean) / scale
                d * d
            } / history.size
        val speeds = history.zipWithNext { a, b -> a.point.distance(b.point) / scale * 1000 / (b.time - a.time) }
        return Motion(if (speeds.isEmpty()) 0.0 else speeds.average(), speeds.maxOrNull() ?: 0.0, variance)
    }

    private fun bridge(
        e: Point,
        timeMs: Long,
        scale: Double,
        motion: Motion,
    ): Evidence {
        val rested = restedAt
        val bridged =
            rested != null && timeMs - restedTime <= BRIDGE_MS && rested.distance(e) / scale <= BRIDGE_RADIUS &&
                motion.maxSpeed < MAX_PEAK_SPEED && motion.variance < MAX_VARIANCE
        if (!bridged) restedAt = null
        return Evidence(if (bridged) SUPPORTED else null, null, hidden = !bridged)
    }

    /** The four joints of one arm (plus the opposite shoulder for the scale). */
    private data class Arm(
        val shoulder: Landmark,
        val elbow: Landmark,
        val wrist: Landmark,
        val opposite: Landmark,
    )

    private fun features(
        arm: Arm,
        hip: Point?,
        table: Polygon,
        aspect: Double,
        scale: Double,
        motion: Motion,
    ): Features {
        val s = arm.shoulder.point.metric(aspect)
        val e = arm.elbow.point.metric(aspect)
        val w = arm.wrist.point.metric(aspect)
        return Features(
            table.signedDistance(arm.elbow.point, aspect) / scale,
            table.signedDistance(arm.wrist.point, aspect) / scale,
            s.distance(e) / scale,
            e.distance(w) / scale,
            angle(s, e, w),
            atan2(e.y - s.y, e.x - s.x),
            atan2(w.y - e.y, w.x - e.x),
            (w.y - e.y) / scale,
            (e.y - s.y) / scale,
            (arm.opposite.point.y - arm.shoulder.point.y) / scale,
            hip?.let { atan2(s.x - it.x, it.y - s.y) },
            motion.speed,
            motion.maxSpeed,
            motion.variance,
            minOf(arm.shoulder.confidence, arm.elbow.confidence, arm.wrist.confidence),
        )
    }

    /** The conservative rule: near/inside the table, bent, upper arm down, and still. */
    private fun supported(f: Features): Boolean {
        val placed = f.elbowDistance >= MIN_ELBOW_DEPTH && f.elbowAngle in ELBOW_ANGLE && f.wristHeight < MAX_WRIST_RISE
        val shaped = f.shoulderHeight > MIN_UPPER_DROP && f.upperLength > MIN_UPPER_LENGTH && f.foreLength > MIN_FORE_LENGTH
        val still = f.speed < MAX_MEAN_SPEED && f.maxSpeed < MAX_PEAK_SPEED && f.variance < MAX_VARIANCE
        return placed && shaped && still
    }

    private fun forget() {
        history.clear()
        restedAt = null
    }

    /** The rule's thresholds; lengths are in shoulder widths, speeds in shoulder widths per second. */
    companion object {
        const val SUPPORTED = 0.95
        const val UNSUPPORTED = 0.05

        /** Floor for the shoulder-width scale, so a sideways person cannot divide by zero. */
        const val MIN_SCALE = 0.05
        const val WINDOW_MS = 1000L
        const val MIN_SAMPLES = 3
        const val MIN_SPAN_MS = 400L

        /** The elbow may be slightly outside the outline (negative = outside). */
        const val MIN_ELBOW_DEPTH = -0.03
        val ELBOW_ANGLE = 25.0..155.0
        const val MIN_UPPER_DROP = 0.15
        const val MIN_UPPER_LENGTH = 0.15
        const val MIN_FORE_LENGTH = 0.1
        const val MAX_WRIST_RISE = 0.5
        const val MAX_MEAN_SPEED = 0.18
        const val MAX_PEAK_SPEED = 0.4
        const val MAX_VARIANCE = 0.015

        /** Longest time a hidden hand keeps a fully seen rest alive. */
        const val BRIDGE_MS = 10_000L

        /** How far (in shoulder widths) the elbow may drift while the hand is hidden. */
        const val BRIDGE_RADIUS = 0.15
    }
}
