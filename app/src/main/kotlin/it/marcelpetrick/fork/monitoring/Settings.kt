// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Polygon
import it.marcelpetrick.fork.detection.Timing
import org.json.JSONArray
import org.json.JSONObject

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
    val calibrationAspect: Double = 0.0,
) {
    init {
        require(people in 1..4 && seats.size <= people)
        require(volume in 0..100 && repeatMs >= 1000 && graceMs >= 0)
        require(calibrationAspect.isFinite() && calibrationAspect >= 0)
    }

    fun encode(): String =
        JSONObject()
            .apply {
                put("schema", 1)
                put("people", people)
                put("camera", camera)
                put("model", model.name)
                put("table", table?.json())
                put("seats", JSONArray(seats.map { it.json() }))
                put("trigger", timing.trigger)
                put("clear", timing.clear)
                put("triggerMs", timing.triggerMs)
                put("clearMs", timing.clearMs)
                put("cooldownMs", timing.cooldownMs)
                put("visual", visual.name)
                put("audio", audio.name)
                put("volume", volume)
                put("repeatMs", repeatMs)
                put("graceMs", graceMs)
                put("debug", debug)
                put("statistics", statistics)
                put("aspect", calibrationAspect)
            }.toString()

    companion object {
        fun decode(text: String): Settings {
            val j = JSONObject(text)
            require(j.getInt("schema") == 1)
            return Settings(
                people = j.getInt("people"),
                camera = j.getString("camera"),
                model = PoseModel.valueOf(j.getString("model")),
                table = j.optJSONArray("table")?.polygon(),
                seats = j.getJSONArray("seats").let { a -> (0 until a.length()).map { a.getJSONArray(it).polygon() } },
                timing =
                    Timing(
                        j.getDouble("trigger"),
                        j.getDouble("clear"),
                        j.getLong("triggerMs"),
                        j.getLong("clearMs"),
                        j.getLong("cooldownMs"),
                    ),
                visual = VisualMode.valueOf(j.getString("visual")),
                audio = AudioMode.valueOf(j.getString("audio")),
                volume = j.getInt("volume"),
                repeatMs = j.getLong("repeatMs"),
                graceMs = j.getLong("graceMs"),
                debug = j.getBoolean("debug"),
                statistics = j.getBoolean("statistics"),
                calibrationAspect = j.getDouble("aspect"),
            )
        }
    }
}

private fun Polygon.json(): JSONArray = JSONArray(points.map { JSONArray(listOf(it.x, it.y)) })

private fun JSONArray.polygon(): Polygon =
    Polygon(
        (0 until length()).map {
            getJSONArray(it).let { p -> Point(p.getDouble(0), p.getDouble(1)) }
        },
    )
