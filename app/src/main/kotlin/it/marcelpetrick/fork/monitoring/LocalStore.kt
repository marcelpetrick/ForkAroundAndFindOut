// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.monitoring

import android.annotation.SuppressLint
import android.content.Context
import android.util.AtomicFile
import it.marcelpetrick.fork.detection.SeatResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Only settings and explicitly requested feature/statistic records are persisted. */
class LocalStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val recordsFile = AtomicFile(File(context.filesDir, "samples.json"))
    var notice: String? = null
        private set

    fun settings(): Settings =
        try {
            preferences.getString("config", null)?.let(Settings::decode) ?: Settings()
        } catch (_: Exception) {
            notice = "Saved settings could not be read. Please calibrate again."
            Settings()
        }

    // The KTX edit helper discards commit()'s result; a failed save must be reported.
    @SuppressLint("UseKtx")
    fun save(settings: Settings) {
        check(preferences.edit().putString("config", settings.encode()).commit()) { "Settings could not be saved" }
    }

    fun records(): JSONArray =
        try {
            if (recordsFile.baseFile.exists()) JSONArray(recordsFile.readFully().toString(Charsets.UTF_8)) else JSONArray()
        } catch (_: Exception) {
            notice = "Local samples could not be read. Export the file before deleting it."
            JSONArray()
        }

    fun add(record: JSONObject) = addAll(listOf(record))

    /** Writes a batch atomically: either every record is stored or none is. */
    @Suppress("TooGenericExceptionCaught") // rolls back the atomic write, then rethrows unchanged
    fun addAll(batch: List<JSONObject>) {
        val existing = records()
        check(notice == null) { notice!! }
        check(existing.length() + batch.size <= LIMIT) { "Sample limit reached. Export and delete samples before recording more." }
        batch.forEach(existing::put)
        val stream = recordsFile.startWrite()
        try {
            stream.write(existing.toString().toByteArray())
            recordsFile.finishWrite(stream)
        } catch (error: Exception) {
            recordsFile.failWrite(stream)
            throw error
        }
    }

    fun export(): String = if (recordsFile.baseFile.exists()) recordsFile.readFully().toString(Charsets.UTF_8) else "[]"

    fun delete() {
        recordsFile.delete()
        notice = null
    }

    companion object {
        const val LIMIT = 5000
    }
}

/**
 * Session boundaries are retained so future model evaluation cannot leak adjacent frames.
 * [selectedSeat] 0 records every seat (used for false-alarm/missed-violation feedback).
 */
fun sampleRecord(
    session: String,
    time: Long,
    label: String,
    seats: List<SeatResult>,
    selectedSeat: Int,
): JSONObject =
    JSONObject().apply {
        put("schema", 1)
        put("type", "sample")
        put("session", session)
        put("timeMs", time)
        put("label", label)
        put("seat", selectedSeat)
        put("imageRecorded", false)
        put(
            "arms",
            JSONArray(
                seats.filter { selectedSeat == 0 || it.seat == selectedSeat }.flatMap { seat ->
                    listOf("left" to seat.left, "right" to seat.right).map { (name, arm) ->
                        JSONObject().apply {
                            put("seat", seat.seat)
                            put("side", name)
                            put("state", arm.state.name)
                            put("score", arm.score ?: JSONObject.NULL)
                            put(
                                "features",
                                arm.features?.let { f ->
                                    JSONObject().apply {
                                        put("elbowDistance", f.elbowDistance)
                                        put("wristDistance", f.wristDistance)
                                        put("upperLength", f.upperLength)
                                        put("foreLength", f.foreLength)
                                        put("elbowAngle", f.elbowAngle)
                                        put("upperAngle", f.upperAngle)
                                        put("foreAngle", f.foreAngle)
                                        put("wristHeight", f.wristHeight)
                                        put("shoulderHeight", f.shoulderHeight)
                                        put("shoulderTilt", f.shoulderTilt)
                                        put("torsoTilt", f.torsoTilt ?: JSONObject.NULL)
                                        put("speed", f.speed)
                                        put("maxSpeed", f.maxSpeed)
                                        put("variance", f.variance)
                                        put("confidence", f.confidence)
                                    }
                                } ?: JSONObject.NULL,
                            )
                        }
                    }
                },
            ),
        )
    }

/** Optional non-image statistics for one monitoring session (FR-16). */
fun sessionRecord(
    session: String,
    durationMs: Long,
    violations: Int,
    falseAlarms: Int,
    missedViolations: Int,
    meanConfidence: Double?,
): JSONObject =
    JSONObject().apply {
        put("schema", 1)
        put("type", "session")
        put("session", session)
        put("durationMs", durationMs)
        put("violations", violations)
        put("falseAlarms", falseAlarms)
        put("missedViolations", missedViolations)
        put("meanConfidence", meanConfidence ?: JSONObject.NULL)
        put("imageRecorded", false)
    }
