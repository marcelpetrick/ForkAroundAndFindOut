// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.monitoring

import android.content.Context
import it.marcelpetrick.fork.detection.Detector
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.pose
import it.marcelpetrick.fork.detection.table
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageTest {
    @Test
    fun persistsEverySettingAndRejectsInvalidConfiguration() {
        val context = RuntimeEnvironment.getApplication()
        val store = LocalStore(context)
        assertEquals(Settings(), store.settings())
        val settings =
            Settings(
                table = table,
                seats = listOf(table),
                calibrationAspect = 1.5,
                model = PoseModel.LITE,
                camera = "rear-2",
                visual = VisualMode.PULSE,
                audio = AudioMode.REPEAT,
                statistics = true,
            )
        store.save(settings)
        assertEquals(settings.encode(), LocalStore(context).settings().encode())
        assertEquals("pose_landmarker_lite.task", settings.model.asset)
        assertEquals(Settings().encode(), Settings.decode(Settings().encode()).encode())
        assertThrows(IllegalArgumentException::class.java) { Settings(people = 0) }
        assertThrows(IllegalArgumentException::class.java) { Settings(volume = 101) }
        assertThrows(IllegalArgumentException::class.java) { Settings(calibrationAspect = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Settings.decode("{\"schema\":2}") }
        context
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("config", "bad")
            .commit()
        assertEquals(Settings(), store.settings())
        assertNotNull(store.notice)
    }

    @Test
    fun explicitSamplesExportDeleteAndCorruptionRecovery() {
        val context = RuntimeEnvironment.getApplication()
        val store = LocalStore(context)
        assertEquals(0, store.records().length())
        assertEquals("[]", store.export())
        val detector = Detector(table, 1)
        var seats = detector.process(listOf(pose()), 0)
        for (time in 100L..1500L step 100) seats = detector.process(listOf(pose()), time)
        val record = sampleRecord("meal-a", 1500, "LEFT", seats, 1)
        assertFalse(record.getBoolean("imageRecorded"))
        assertEquals(2, record.getJSONArray("arms").length())
        assertTrue(
            record
                .getJSONArray("arms")
                .getJSONObject(0)
                .getJSONObject("features")
                .getDouble("elbowDistance") > 0,
        )
        store.add(record)
        val restored = LocalStore(context)
        assertEquals("meal-a", restored.records().getJSONObject(0).getString("session"))
        assertEquals(1, JSONArray(restored.export()).length())
        val hiddenHip = pose().landmarks.toMutableList().apply { this[23] = this[23].copy(confidence = 0.0) }
        val uncertain = detector.process(listOf(Pose(hiddenHip)), 1800)
        store.add(sampleRecord("meal-a", 1800, "MISSED", uncertain, 1))
        store.add(sampleRecord("meal-a", 1900, "FALSE_ALARM", detector.process(emptyList(), 1900), 1))
        assertEquals(3, store.records().length())
        val file = File(context.filesDir, "samples.json")
        file.writeText("corrupt")
        assertEquals(0, store.records().length())
        assertNotNull(store.notice)
        assertThrows(IllegalStateException::class.java) { store.add(JSONObject()) }
        assertEquals("corrupt", store.export()) // allow salvage, do not silently overwrite
        store.delete()
        assertEquals(0, store.records().length())
        val full = JSONArray()
        repeat(5000) { full.put(JSONObject()) }
        file.writeText(full.toString())
        assertThrows(IllegalStateException::class.java) { store.add(JSONObject()) }
        store.delete()
        assertEquals("[]", store.export())
    }

    @Test
    fun batchesAreAtomicAndFeedbackCoversEverySeat() {
        val store = LocalStore(RuntimeEnvironment.getApplication())
        val detector = Detector(table, 2)
        var seats = detector.process(listOf(pose(), pose(offset = 0.3)), 0)
        for (time in 100L..1500L step 100) seats = detector.process(listOf(pose(), pose(offset = 0.3)), time)
        val all = sampleRecord("meal-b", 1500, "FALSE_ALARM", seats, 0)
        assertEquals("sample", all.getString("type"))
        assertEquals(4, all.getJSONArray("arms").length())
        assertEquals(setOf(1, 2), (0 until 4).map { all.getJSONArray("arms").getJSONObject(it).getInt("seat") }.toSet())
        store.addAll(List(4999) { JSONObject() })
        assertThrows(IllegalStateException::class.java) { store.addAll(listOf(all, all)) }
        assertEquals(4999, store.records().length()) // nothing partially written
        store.add(all)
        assertEquals(LocalStore.LIMIT, store.records().length())
        store.delete()
        val stats = sessionRecord("meal-b", 60_000, 3, 1, 2, 0.9)
        val unknown = sessionRecord("meal-c", 1, 0, 0, 0, null)
        assertEquals("session", stats.getString("type"))
        assertEquals(0.9, stats.getDouble("meanConfidence"), 0.0)
        assertTrue(unknown.isNull("meanConfidence"))
        assertFalse(stats.getBoolean("imageRecorded"))
    }
}
