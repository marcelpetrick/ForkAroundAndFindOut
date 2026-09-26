// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.detection

import kotlin.math.atan2
import kotlin.math.max

data class Landmark(
    val point: Point,
    val confidence: Double,
) {
    fun visible(): Boolean = point.inImage() && confidence.isFinite() && confidence >= 0.7
}

data class Pose(
    val landmarks: List<Landmark>,
) {
    fun joint(index: Int): Landmark? = landmarks.getOrNull(index)?.takeIf { it.visible() }

    // Shoulder center stays consistent even when hips become visible mid-session.
    fun center(): Point? {
        val left = joint(11)?.point ?: return null
        val right = joint(12)?.point ?: return null
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

    fun evaluate(
        pose: Pose,
        left: Boolean,
        table: Polygon,
        timeMs: Long,
        aspect: Double,
        maxGapMs: Long = 500,
    ): Evidence {
        val shoulder = pose.joint(if (left) 11 else 12)
        val elbow = pose.joint(if (left) 13 else 14)
        val wrist = pose.joint(if (left) 15 else 16)
        val opposite = pose.joint(if (left) 12 else 11)
        if (shoulder == null || elbow == null || opposite == null || aspect <= 0 || !aspect.isFinite()) {
            forget()
            return Evidence(null, null, hidden = true)
        }
        val s = shoulder.point.metric(aspect)
        val e = elbow.point.metric(aspect)
        val scale = max(s.distance(opposite.point.metric(aspect)), 0.05)
        if (history.isNotEmpty() && (timeMs <= history.last().time || timeMs - history.last().time > maxGapMs)) forget()
        history.addLast(Sample(timeMs, e))
        while (history.size > 1 && timeMs - history.first().time > 1000) history.removeFirst()
        val mean = Point(history.sumOf { it.point.x } / history.size, history.sumOf { it.point.y } / history.size)
        val variance =
            history.sumOf {
                val d = it.point.distance(mean) / scale
                d * d
            } / history.size
        val speeds = history.zipWithNext { a, b -> a.point.distance(b.point) / scale * 1000 / (b.time - a.time) }
        if (wrist == null) {
            // Only the hand is hidden: bridge a rest that was fully seen, never start one.
            val rested = restedAt
            val bridged =
                rested != null && timeMs - restedTime <= BRIDGE_MS && rested.distance(e) / scale <= BRIDGE_RADIUS &&
                    (speeds.maxOrNull() ?: 0.0) < 0.4 && variance < 0.015
            if (!bridged) restedAt = null
            return Evidence(if (bridged) 0.95 else null, null, hidden = !bridged)
        }
        val w = wrist.point.metric(aspect)
        val hip = pose.joint(if (left) 23 else 24)?.point?.metric(aspect)
        val features =
            Features(
                table.signedDistance(elbow.point, aspect) / scale,
                table.signedDistance(wrist.point, aspect) / scale,
                s.distance(e) / scale,
                e.distance(w) / scale,
                angle(s, e, w),
                atan2(e.y - s.y, e.x - s.x),
                atan2(w.y - e.y, w.x - e.x),
                (w.y - e.y) / scale,
                (e.y - s.y) / scale,
                (opposite.point.y - shoulder.point.y) / scale,
                hip?.let { atan2(s.x - it.x, it.y - s.y) },
                if (speeds.isEmpty()) 0.0 else speeds.average(),
                speeds.maxOrNull() ?: 0.0,
                variance,
                minOf(shoulder.confidence, elbow.confidence, wrist.confidence),
            )
        if (history.size < 3 || timeMs - history.first().time < 400) return Evidence(null, features)
        val supported =
            features.elbowDistance >= -0.03 && features.elbowAngle in 25.0..155.0 &&
                features.shoulderHeight > 0.15 && features.upperLength > 0.15 && features.foreLength > 0.1 &&
                features.wristHeight < 0.5 && features.speed < 0.18 && features.maxSpeed < 0.4 && features.variance < 0.015
        if (supported) {
            restedAt = e
            restedTime = timeMs
        } else {
            restedAt = null
        }
        // Deliberately conservative heuristic, not a calibrated probability of physical contact.
        return Evidence(if (supported) 0.95 else 0.05, features)
    }

    private fun forget() {
        history.clear()
        restedAt = null
    }

    companion object {
        /** Longest time a hidden hand keeps a fully seen rest alive. */
        const val BRIDGE_MS = 10_000L

        /** How far (in shoulder widths) the elbow may drift while the hand is hidden. */
        const val BRIDGE_RADIUS = 0.15
    }
}
