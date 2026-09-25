// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import it.marcelpetrick.fork.camera.FrameSource
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.pose
import it.marcelpetrick.fork.monitoring.LocalStore
import it.marcelpetrick.fork.monitoring.Sound
import it.marcelpetrick.fork.monitoring.VisualMode
import it.marcelpetrick.fork.ui.Speaker
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.ByteArrayOutputStream
import java.time.Duration

class FakeSource : FrameSource {
    override var bounds: RectF? = null
    var started = 0
    var closed = 0

    override fun start() {
        started++
    }

    override fun close() {
        closed++
    }
}

class RecordingSpeaker : Speaker {
    val sounds = mutableListOf<Sound>()
    var released = false

    override fun play(
        sound: Sound,
        volume: Int,
    ) {
        if (sound != Sound.NONE) sounds += sound
    }

    override fun release() {
        released = true
    }
}

fun idle(ms: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

/** Visible views only, as a user would see them. */
fun View.all(): List<View> =
    if (visibility != View.VISIBLE) {
        emptyList()
    } else {
        listOf(this) + if (this is ViewGroup) (0 until childCount).flatMap { getChildAt(it).all() } else emptyList()
    }

fun Activity.root(): View = window.decorView

fun Activity.texts(): String = root().all().filterIsInstance<TextView>().joinToString("\n") { it.text }

fun Activity.click(text: String) {
    val button = root().all().filterIsInstance<Button>().firstOrNull { it.text == text || it.contentDescription == text }
    assertNotNull("No button '$text' in:\n${texts()}", button)
    button!!.performClick()
    idle()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {
    private val corners = listOf(Point(0.1, 0.5), Point(0.9, 0.5), Point(0.9, 0.9), Point(0.1, 0.9))
    private val seat = listOf(Point(0.3, 0.1), Point(0.7, 0.1), Point(0.7, 0.45), Point(0.3, 0.45))

    private fun launch(): ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java).setup()

    private fun MainActivity.tap(point: Point) {
        stage!!.onTap!!(point)
    }

    @Test
    fun welcomeDemoAndSettingsNeverSoundAndPersist() {
        launch().use { controller ->
            val activity = controller.get()
            val speaker = RecordingSpeaker()
            activity.speaker = speaker
            assertTrue(activity.texts().contains("Set up camera"))
            assertFalse(activity.texts().contains("Start monitoring"))

            activity.click("Try demo (synthetic)")
            assertEquals(MainActivity.Screen.DEMO, activity.screen)
            assertTrue(activity.texts().contains("SYNTHETIC DEMO"))
            idle(6000)
            assertEquals(VisualMode.BORDER, activity.stage!!.warning)
            assertTrue(activity.texts().contains("Elbow on table"))
            idle(4500)
            assertEquals(VisualMode.OFF, activity.stage!!.warning)
            activity.click("Restart")
            idle(500)
            assertTrue(speaker.sounds.isEmpty())
            activity.onBackPressedDispatcher.onBackPressed()
            idle()
            assertEquals(MainActivity.Screen.WELCOME, activity.screen)

            activity.click("Settings")
            assertEquals(MainActivity.Screen.SETTINGS, activity.screen)
            activity.click("Decrease People")
            activity.click("Increase Sound")
            assertTrue(activity.texts().contains("Beep once"))
            val saved = LocalStore(activity).settings()
            assertEquals(3, saved.people)
            assertEquals(activity.settings, saved)
            activity.click("Back")
            activity.onBackPressedDispatcher.onBackPressed()
            assertTrue(activity.isFinishing)
        }
    }

    @Test
    fun cameraSetupCalibratesSeatsAndMonitorsWithFailSafes() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        launch().use { controller ->
            val activity = controller.get()
            val speaker = RecordingSpeaker()
            val sources = mutableListOf<FakeSource>()
            lateinit var frame: (List<Pose>, Long, Double, Long) -> Unit
            lateinit var error: (String) -> Unit
            activity.speaker = speaker
            activity.sourceFactory = { _, _, f, e ->
                frame = f
                error = e
                FakeSource().also { sources += it }
            }
            activity.click("Settings")
            activity.click("Increase Sound")
            activity.click("Back")

            activity.click("Set up camera")
            assertEquals(MainActivity.Screen.POSITION, activity.screen)
            assertEquals(1, sources.single().started)
            frame(listOf(pose()), SystemClock.uptimeMillis(), 1.0, 5)
            assertTrue(activity.texts().contains("People detected: 1"))

            activity.click("Mark table")
            assertEquals(1, sources.size) // camera stays open between setup steps
            val stage = activity.stage!!
            assertTrue(stage.width > 0 && stage.height > 0)
            stage.bounds = RectF(0.2f, 0f, 0.8f, 1f)
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                stage.dispatchTouchEvent(MotionEvent.obtain(0, 0, action, 1f, 1f, 0))
            }
            assertTrue(activity.texts().contains("Tap inside the camera image"))
            stage.bounds = null
            corners.take(3).forEach { activity.tap(it) }
            activity.click("Save table")
            assertTrue(activity.texts().contains("Tap four corners"))
            activity.click("Undo")
            activity.click("Reset")
            assertTrue(activity.texts().contains("Corners marked: 0 of 4"))
            corners.forEach { activity.tap(it) }
            activity.tap(Point(0.5, 0.5)) // a fifth tap is ignored
            activity.click("Save table")
            assertEquals(MainActivity.Screen.SEATS, activity.screen)
            val aspect = activity.settings.calibrationAspect
            assertTrue(aspect > 0)

            activity.click("Add seat")
            assertTrue(activity.texts().contains("Tap four corners"))
            seat.forEach { activity.tap(it) }
            activity.click("Add seat")
            seat.forEach { activity.tap(it) }
            activity.click("Add seat")
            assertTrue(activity.texts().contains("Seats must not overlap"))
            activity.click("Clear seats")
            seat.forEach { activity.tap(it) }
            activity.click("Undo")
            activity.tap(seat.last())
            activity.click("Add seat")
            assertTrue(activity.texts().contains("Seats: 1 of 4"))
            activity.click("Finish setup")
            assertEquals(MainActivity.Screen.WELCOME, activity.screen)
            assertEquals(1, activity.settings.seats.size)
            assertEquals(1, sources.single().closed)

            activity.click("Start monitoring")
            assertEquals(MainActivity.Screen.MONITOR, activity.screen)
            assertTrue(activity.texts().contains("Warnings begin in 3 s"))
            repeat(50) {
                frame(listOf(pose()), SystemClock.uptimeMillis(), aspect, 5)
                idle(100)
            }
            assertEquals(VisualMode.BORDER, activity.stage!!.warning)
            assertEquals(listOf(Sound.BEEP), speaker.sounds)
            assertTrue(activity.texts().contains("Elbows off the table, please"))
            assertTrue(activity.texts().contains("Seat 1 · Left: Elbow on table"))
            activity.click("Adult diagnostics")
            assertTrue(activity.texts().contains("FPS"))
            assertTrue(activity.texts().contains("Seat 1 left: score 0.95"))
            activity.click("Hide diagnostics")

            activity.click("Pause")
            assertEquals(Sound.STOP, speaker.sounds.last())
            assertEquals(VisualMode.OFF, activity.stage!!.warning)
            assertTrue(activity.texts().contains("Paused"))
            frame(listOf(pose()), SystemClock.uptimeMillis(), aspect, 5)
            idle(2000)
            assertEquals(VisualMode.OFF, activity.stage!!.warning)
            activity.click("Resume")
            idle(4000) // no frames: stale evidence never alarms
            assertTrue(activity.texts().contains("Waiting for a clear camera view"))

            controller.pause().stop()
            assertEquals(1, sources[1].closed)
            assertFalse(activity.monitor!!.active)
            controller.start().resume()
            idle()
            assertEquals(1, sources.last().started)
            assertEquals(3, sources.size)
            activity.click("Resume")

            frame(listOf(pose()), SystemClock.uptimeMillis(), aspect + 0.5, 5)
            idle(100)
            assertEquals(MainActivity.Screen.WELCOME, activity.screen)
            assertTrue(activity.texts().contains("Recalibrate the table"))
            assertNull(activity.monitor)

            activity.click("Start monitoring")
            error("Camera or model unavailable: gone. Retry setup or choose Lite.")
            idle()
            assertEquals(MainActivity.Screen.WELCOME, activity.screen)
            assertTrue(activity.texts().contains("Retry setup or choose Lite"))
            activity.click("Recalibrate camera")
            assertEquals(MainActivity.Screen.POSITION, activity.screen)
            activity.click("Back")
            activity.show(MainActivity.Screen.MONITOR) // no session: falls back safely
            assertEquals(MainActivity.Screen.WELCOME, activity.screen)
            controller.pause().stop().destroy()
            assertTrue(speaker.released)
        }
    }

    @Suppress("DEPRECATION")
    private fun MainActivity.answerPermission(grant: Int) {
        val request = shadowOf(this).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.CAMERA), request.requestedPermissions.toList())
        onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(grant))
        idle()
    }

    @Test
    fun cameraPermissionIsRequestedOnlyForCameraAndDenialExplains() {
        launch().use { controller ->
            val activity = controller.get()
            activity.sourceFactory = { _, _, _, _ -> FakeSource() }
            activity.click("Try demo (synthetic)")
            assertNull(shadowOf(activity).lastRequestedPermission)
            activity.click("Back")
            activity.click("Set up camera")
            assertEquals(MainActivity.Screen.WELCOME, activity.screen)
            activity.answerPermission(PackageManager.PERMISSION_DENIED)
            assertEquals(MainActivity.Screen.WELCOME, activity.screen)
            assertTrue(activity.texts().contains("Camera permission is needed"))
            // Granted later in Android settings: no further prompt is necessary.
            shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
            activity.click("Set up camera")
            assertEquals(MainActivity.Screen.POSITION, activity.screen)
        }
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA)
        launch().use { controller ->
            val activity = controller.get()
            activity.sourceFactory = { _, _, _, _ -> FakeSource() }
            activity.click("Set up camera")
            shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
            activity.answerPermission(PackageManager.PERMISSION_GRANTED)
            assertEquals(MainActivity.Screen.POSITION, activity.screen)
        }
    }

    @Test
    fun feedbackTrainingStatisticsExportAndDeleteAreExplicitAndLocal() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        launch().use { controller ->
            val activity = controller.get()
            lateinit var frame: (List<Pose>, Long, Double, Long) -> Unit
            activity.speaker = RecordingSpeaker()
            activity.sourceFactory = { _, _, f, _ ->
                frame = f
                FakeSource()
            }
            activity.click("Settings")
            activity.click("Increase Save session statistics")
            activity.click("Back")
            activity.click("Set up camera")
            activity.click("Mark table")
            corners.forEach { activity.tap(it) }
            activity.click("Save table")
            activity.click("Finish setup")
            activity.click("Start monitoring")
            val aspect = activity.settings.calibrationAspect

            fun feed(ms: Long) =
                repeat((ms / 100).toInt()) {
                    frame(listOf(pose()), SystemClock.uptimeMillis(), aspect, 5)
                    idle(100)
                }
            feed(1000)
            activity.click("Adult diagnostics")
            activity.click("False alarm")
            activity.click("Missed violation")
            assertTrue(activity.texts().contains("Only feature numbers were stored"))
            assertFalse(activity.texts().contains("LEFT ELBOW")) // hidden until training is enabled
            activity.click("Training mode: off")
            assertTrue(activity.texts().contains("LEFT ELBOW"))
            activity.click("Seat to record: 1")
            assertTrue(activity.texts().contains("Seat to record: 2"))
            repeat(3) {
                activity.click(
                    "Seat to record: ${if (it == 0) {
                        2
                    } else if (it == 1) {
                        3
                    } else {
                        4
                    }}",
                )
            }
            activity.click("LEFT ELBOW")
            assertTrue(activity.texts().contains("Recording LEFT for seat 1"))
            feed(5200)
            assertTrue(activity.texts().contains("Saved: LEFT ×"))
            activity.click("RIGHT ELBOW")
            activity.click("Training mode: on") // turning training off discards the capture
            feed(5200)
            activity.click("Stop")

            val records = LocalStore(activity).records()
            val types = (0 until records.length()).map { records.getJSONObject(it).getString("type") }
            val labels = (0 until records.length()).mapNotNull { records.getJSONObject(it).optString("label").ifEmpty { null } }
            assertEquals("session", types.last())
            assertTrue(labels.containsAll(listOf("FALSE_ALARM", "MISSED_VIOLATION", "LEFT")))
            assertFalse(labels.contains("RIGHT"))
            assertTrue(labels.count { it == "LEFT" } in 30..60)
            val stats = records.getJSONObject(records.length() - 1)
            assertEquals(1, stats.getInt("falseAlarms"))
            assertEquals(1, stats.getInt("missedViolations"))
            assertTrue(stats.getLong("durationMs") > 10_000)

            activity.click("Local data")
            assertEquals(MainActivity.Screen.DATA, activity.screen)
            assertTrue(activity.texts().contains("Stored records: ${records.length()}"))
            val target = Uri.parse("content://test/export.json")
            val exported = ByteArrayOutputStream()
            shadowOf(activity.contentResolver).registerOutputStream(target, exported)
            activity.click("Export JSON")
            val request = shadowOf(activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
            shadowOf(activity).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(target))
            idle()
            assertEquals(records.length(), JSONArray(exported.toString()).length())
            assertTrue(activity.texts().contains("Exported."))
            activity.click("Export JSON")
            val failing = shadowOf(activity).nextStartedActivityForResult
            shadowOf(activity).receiveResult(failing.intent, Activity.RESULT_OK, Intent().setData(Uri.parse("content://missing/x")))
            idle()
            assertTrue(activity.texts().contains("Export failed"))
            activity.click("Export JSON")
            shadowOf(activity).receiveResult(shadowOf(activity).nextStartedActivityForResult.intent, Activity.RESULT_CANCELED, null)
            idle()

            activity.click("Delete all")
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            idle()
            assertEquals(records.length(), LocalStore(activity).records().length())
            activity.click("Delete all")
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertEquals(0, LocalStore(activity).records().length())
            assertTrue(activity.texts().contains("All local records deleted."))
            assertTrue(activity.texts().contains("Stored records: 0"))

            // A full store reports the problem instead of pretending to save.
            LocalStore(activity).addAll(List(LocalStore.LIMIT) { JSONObject() })
            activity.click("Back")
            activity.click("Start monitoring")
            activity.click("Adult diagnostics")
            activity.click("False alarm")
            assertTrue(activity.texts().contains("Not saved: Sample limit reached"))
        }
    }
}
