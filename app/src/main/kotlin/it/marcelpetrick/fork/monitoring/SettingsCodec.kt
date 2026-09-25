// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.monitoring

import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Polygon
import it.marcelpetrick.fork.detection.Timing
import org.json.JSONArray
import org.json.JSONObject

/** Versioned JSON persistence for [Settings]; lives in the app because org.json is Android's. */
fun Settings.encode(): String =
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
            put("maxGapMs", timing.maxGapMs)
            put("visual", visual.name)
            put("audio", audio.name)
            put("volume", volume)
            put("repeatMs", repeatMs)
            put("graceMs", graceMs)
            put("debug", debug)
            put("statistics", statistics)
            put("aspect", calibrationAspect)
            put("rotation", calibrationRotation)
        }.toString()

fun Settings.Companion.decode(text: String): Settings {
    val j = JSONObject(text)
    require(j.getInt("schema") == 1)
    // Before 0.5.15 calibration was stored in preview-view space; it cannot be converted.
    val imageSpace = j.has("rotation")
    return Settings(
        people = j.getInt("people"),
        camera = j.getString("camera"),
        model = PoseModel.valueOf(j.getString("model")),
        table = if (imageSpace) j.optJSONArray("table")?.polygon() else null,
        seats =
            if (imageSpace) {
                j.getJSONArray("seats").let { a ->
                    (0 until a.length()).map { a.getJSONArray(it).polygon() }
                }
            } else {
                emptyList()
            },
        timing =
            Timing(
                j.getDouble("trigger"),
                j.getDouble("clear"),
                j.getLong("triggerMs"),
                j.getLong("clearMs"),
                j.getLong("cooldownMs"),
                j.optLong("maxGapMs", Timing().maxGapMs),
            ),
        visual = VisualMode.valueOf(j.getString("visual")),
        audio = AudioMode.valueOf(j.getString("audio")),
        volume = j.getInt("volume"),
        repeatMs = j.getLong("repeatMs"),
        graceMs = j.getLong("graceMs"),
        debug = j.getBoolean("debug"),
        statistics = j.getBoolean("statistics"),
        calibrationAspect = if (imageSpace) j.getDouble("aspect") else 0.0,
        calibrationRotation = j.optInt("rotation", -1),
    )
}

private fun Polygon.json(): JSONArray = JSONArray(points.map { JSONArray(listOf(it.x, it.y)) })

private fun JSONArray.polygon(): Polygon =
    Polygon(
        (0 until length()).map {
            getJSONArray(it).let { p -> Point(p.getDouble(0), p.getDouble(1)) }
        },
    )
