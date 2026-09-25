// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.Polygon
import it.marcelpetrick.fork.detection.Timing

enum class VisualMode { OFF, BORDER, ICON, FULL, PULSE }

enum class AudioMode { OFF, ONCE, REPEAT, CONTINUOUS }

enum class PoseModel(
    val asset: String,
) {
    FULL("pose_landmarker_full.task"),
    LITE("pose_landmarker_lite.task"),
}

data class Settings(
    val people: Int = 4,
    val camera: String = "",
    val model: PoseModel = PoseModel.FULL,
    val table: Polygon? = null,
    val seats: List<Polygon> = emptyList(),
    val timing: Timing = Timing(),
    val visual: VisualMode = VisualMode.BORDER,
    val audio: AudioMode = AudioMode.OFF,
    val volume: Int = 40,
    val repeatMs: Long = 3000,
    val graceMs: Long = 3000,
    val debug: Boolean = true,
    val statistics: Boolean = false,
    /** Upright analysis-image aspect (w/h) at calibration; 0 = not calibrated. */
    val calibrationAspect: Double = 0.0,
    /** Sensor rotation in degrees at calibration; -1 = unknown. */
    val calibrationRotation: Int = -1,
) {
    init {
        require(people in 1..4 && seats.size <= people)
        require(volume in 0..100 && repeatMs >= 1000 && graceMs >= 0)
        require(calibrationAspect.isFinite() && calibrationAspect >= 0)
        require(calibrationRotation in setOf(-1, 0, 90, 180, 270))
    }

    /** Extension point for platform codecs (the app adds `decode`). */
    companion object
}
