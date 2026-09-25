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
)

/** A rolling second of normalized motion distinguishes a reach from supported posture. */
class ArmClassifier {
    private data class Sample(
        val time: Long,
        val point: Point,
    )

    private val history = ArrayDeque<Sample>()

    fun evaluate(
        pose: Pose,
        left: Boolean,
        table: Polygon,
        timeMs: Long,
        aspect: Double,
    ): Evidence {
        val shoulder = pose.joint(if (left) 11 else 12)
        val elbow = pose.joint(if (left) 13 else 14)
        val wrist = pose.joint(if (left) 15 else 16)
        val opposite = pose.joint(if (left) 12 else 11)
        if (shoulder == null || elbow == null || wrist == null || opposite == null || aspect <= 0 || !aspect.isFinite()) {
            history.clear()
            return Evidence(null, null)
        }
        val s = shoulder.point.metric(aspect)
        val e = elbow.point.metric(aspect)
        val w = wrist.point.metric(aspect)
        val scale = max(s.distance(opposite.point.metric(aspect)), 0.05)
        if (history.isNotEmpty() && (timeMs <= history.last().time || timeMs - history.last().time > 500)) history.clear()
        history.addLast(Sample(timeMs, e))
        while (history.size > 1 && timeMs - history.first().time > 1000) history.removeFirst()
        val mean = Point(history.sumOf { it.point.x } / history.size, history.sumOf { it.point.y } / history.size)
        val variance =
            history.sumOf {
                val d = it.point.distance(mean) / scale
                d * d
            } / history.size
        val speeds = history.zipWithNext { a, b -> a.point.distance(b.point) / scale * 1000 / (b.time - a.time) }
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
        // Deliberately conservative heuristic, not a calibrated probability of physical contact.
        return Evidence(if (supported) 0.95 else 0.05, features)
    }
}
