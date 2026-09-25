// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import it.marcelpetrick.fork.camera.FrameInfo
import it.marcelpetrick.fork.camera.FrameSource
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.pose
import it.marcelpetrick.fork.monitoring.LocalStore
import it.marcelpetrick.fork.monitoring.Replay
import it.marcelpetrick.fork.monitoring.SessionLog
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
import java.util.zip.GZIPInputStream

/** A source whose camera image exactly fills [view] (no letterbox). */
class FakeSource(
    private val view: View? = null,
) : FrameSource {
    override val mapping: Matrix?
        get() = view?.let { Matrix().apply { setScale(it.width.toFloat(), it.height.toFloat()) } }
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
            assertTrue(activity.texts().contains("Chime once"))
            val saved = LocalStore(activity).settings()
            assertEquals(3, saved.people)
            assertEquals(activity.settings, saved)
            activity.click("Test sound") // explicit preview only; no alarm logic involved
            assertEquals(listOf(Sound.BEEP), speaker.sounds)
            activity.click("Back")
            activity.click("About")
            assertEquals(MainActivity.Screen.ABOUT, activity.screen)
            assertTrue(activity.texts().contains("Version ${BuildConfig.VERSION_NAME}"))
            assertTrue(activity.texts().contains("GNU General Public License v3 or later"))
            assertTrue(activity.texts().contains("MediaPipe Tasks Vision"))
            assertTrue(activity.texts().contains("github.com/marcelpetrick/ForkAroundAndFindOut"))
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
            lateinit var frame: (List<Pose>, Long, FrameInfo) -> Unit
            lateinit var error: (String) -> Unit
            activity.speaker = speaker
            activity.sourceFactory = { view, _, f, e ->
                frame = f
                error = e
                FakeSource(view).also { sources += it }
            }
            activity.click("Settings")
            activity.click("Increase Sound")
            activity.click("Back")

            activity.click("Set up camera")
            assertEquals(MainActivity.Screen.POSITION, activity.screen)
            assertEquals(1, sources.single().started)
            frame(listOf(pose()), SystemClock.uptimeMillis(), FrameInfo(1.0, 90, 5))
            assertTrue(activity.texts().contains("People: 1 of 4"))
            activity.click("Mark table") // disabled: the visibility check has not passed
            assertEquals(MainActivity.Screen.POSITION, activity.screen)

            activity.click("Mark table without the check")
            assertEquals(1, sources.size) // camera stays open between setup steps
            val stage = activity.stage!!
            assertTrue(stage.width > 0 && stage.height > 0)
            // The camera image fills only the middle 60 % of the view (letterboxed left and right).
            stage.mapping =
                Matrix().apply {
                    setScale(stage.width * 0.6f, stage.height.toFloat())
                    postTranslate(stage.width * 0.2f, 0f)
                }
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                stage.dispatchTouchEvent(MotionEvent.obtain(0, 0, action, 1f, 1f, 0))
            }
            assertTrue(activity.texts().contains("Tap inside the camera image"))
            frame(listOf(pose()), SystemClock.uptimeMillis(), FrameInfo(1.0, 90, 5)) // restores the full-view mapping
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
                frame(listOf(pose()), SystemClock.uptimeMillis(), FrameInfo(aspect, 90, 5))
                idle(100)
            }
            assertEquals(VisualMode.BORDER, activity.stage!!.warning)
            assertEquals(listOf(Sound.BEEP), speaker.sounds)
            assertTrue(activity.texts().contains("Elbows off the table, please"))
            assertTrue(activity.texts().contains("Seat 1 · Left: Elbow on table"))
            activity.click("Adult diagnostics")
            assertTrue(activity.texts().contains("FPS"))
            assertTrue(activity.texts().contains("Seat 1 left: score 0.95"))
            // An adult marks it as a false alarm: silence now, and a 30 s rest for the table.
            activity.click("False alarm")
            assertEquals(Sound.STOP, speaker.sounds.last())
            assertEquals(VisualMode.OFF, activity.stage!!.warning)
            repeat(20) {
                frame(listOf(pose()), SystemClock.uptimeMillis(), FrameInfo(aspect, 90, 5))
                idle(100)
            }
            val rest = Regex("""Reminders rest for (\d+) s""").find(activity.texts())!!.groupValues[1].toInt()
            assertTrue("rest $rest s", rest in 27..29)
            assertEquals(VisualMode.OFF, activity.stage!!.warning)
            assertEquals(1, speaker.sounds.count { it == Sound.BEEP })
            activity.click("Hide diagnostics")

            activity.click("Pause")
            assertEquals(Sound.STOP, speaker.sounds.last())
            assertEquals(VisualMode.OFF, activity.stage!!.warning)
            assertTrue(activity.texts().contains("Paused"))
            frame(listOf(pose()), SystemClock.uptimeMillis(), FrameInfo(aspect, 90, 5))
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

            frame(listOf(pose()), SystemClock.uptimeMillis(), FrameInfo(aspect + 0.5, 90, 5))
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
            lateinit var frame: (List<Pose>, Long, FrameInfo) -> Unit
            activity.speaker = RecordingSpeaker()
            activity.sourceFactory = { view, _, f, _ ->
                frame = f
                FakeSource(view)
            }
            activity.click("Settings")
            activity.click("Increase Save session statistics")
            activity.click("Back")
            activity.click("Set up camera")
            activity.click("Mark table without the check")
            activity.tap(corners.first())
            activity.click("Save table")
            // Before the first frame the image geometry is unknown: taps and saving are refused.
            assertTrue(activity.texts().contains("Waiting for the camera image"))
            assertEquals(MainActivity.Screen.TABLE, activity.screen)
            frame(listOf(pose()), SystemClock.uptimeMillis(), FrameInfo(0.75, 90, 5))
            corners.forEach { activity.tap(it) }
            activity.click("Save table")
            assertEquals(0.75, activity.settings.calibrationAspect, 0.0)
            assertEquals(90, activity.settings.calibrationRotation)
            activity.click("Finish setup")
            activity.click("Start monitoring")
            val aspect = activity.settings.calibrationAspect

            fun feed(ms: Long) =
                repeat((ms / 100).toInt()) {
                    frame(listOf(pose()), SystemClock.uptimeMillis(), FrameInfo(aspect, 90, 5))
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
            assertTrue(activity.texts().contains("Labelled LEFT for seat 1."))
            activity.click("False alarm") // logged as an event while training is on
            feed(3000)
            val log = activity.recorder!!.file
            activity.click("Training mode: on") // turning training off closes the log
            assertNull(activity.recorder)
            assertFalse(activity.texts().contains("LEFT ELBOW"))
            feed(1000) // not logged any more
            activity.click("Stop")

            // The log holds landmarks and labels only, and replays to the live decisions.
            val recording = SessionLog.read(GZIPInputStream(log.inputStream()).bufferedReader().readLines().asSequence())
            assertEquals(listOf("LEFT", "FALSE_ALARM"), recording.labels.map { it.label })
            assertEquals(listOf(1, 0), recording.labels.map { it.seat })
            assertTrue(recording.frames.size in 25..35)
            assertEquals(1.0, Replay.run(recording).agreement, 0.0)
            assertFalse(log.readBytes().isEmpty())

            val records = LocalStore(activity).records()
            val types = (0 until records.length()).map { records.getJSONObject(it).getString("type") }
            val labels = (0 until records.length()).mapNotNull { records.getJSONObject(it).optString("label").ifEmpty { null } }
            assertEquals("session", types.last())
            assertEquals(listOf("FALSE_ALARM", "MISSED_VIOLATION", "FALSE_ALARM"), labels)
            val stats = records.getJSONObject(records.length() - 1)
            assertEquals(2, stats.getInt("falseAlarms"))
            assertEquals(1, stats.getInt("missedViolations"))
            assertTrue(stats.getLong("durationMs") > 5_000)

            activity.click("Local data")
            assertEquals(MainActivity.Screen.DATA, activity.screen)
            assertTrue(activity.texts().contains("Stored records: ${records.length()}"))
            assertTrue(activity.texts().contains("Session logs: 1"))
            assertTrue(activity.texts().contains(log.name))
            val logTarget = Uri.parse("content://test/session.jsonl.gz")
            val logCopy = ByteArrayOutputStream()
            shadowOf(activity.contentResolver).registerOutputStream(logTarget, logCopy)
            activity.click("Export log")
            val logRequest = shadowOf(activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, logRequest.intent.action)
            shadowOf(activity).receiveResult(logRequest.intent, Activity.RESULT_OK, Intent().setData(logTarget))
            idle()
            assertTrue(log.readBytes().contentEquals(logCopy.toByteArray()))
            activity.click("Export log")
            shadowOf(activity).receiveResult(shadowOf(activity).nextStartedActivityForResult.intent, Activity.RESULT_CANCELED, null)
            idle()
            activity.click("Delete log")
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            idle()
            assertTrue(log.exists())
            activity.click("Delete log")
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertFalse(log.exists())
            assertTrue(activity.texts().contains("Session log deleted."))
            assertTrue(activity.texts().contains("Session logs: 0"))

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

    @Test
    fun visibilityCheckGatesTableMarkingAndCornersCanBeDragged() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        launch().use { controller ->
            val activity = controller.get()
            lateinit var frame: (List<Pose>, Long, FrameInfo) -> Unit
            activity.sourceFactory = { view, _, f, _ ->
                frame = f
                FakeSource(view)
            }
            activity.click("Settings")
            repeat(3) { activity.click("Decrease People") }
            activity.click("Back")
            activity.click("Set up camera")
            assertTrue(activity.texts().contains("Checking for 10 seconds"))
            repeat(95) {
                frame(listOf(pose()), SystemClock.uptimeMillis(), FrameInfo(1.0, 90, 5))
                idle(100)
            }
            assertTrue(activity.texts().contains("People: 1 of 1 · all arms visible in 100 % of frames"))
            assertTrue(activity.texts().contains("Everyone is visible."))
            activity.click("Mark table")
            assertEquals(MainActivity.Screen.TABLE, activity.screen)

            val stage = activity.stage!!
            listOf(Point(0.1, 0.5), Point(0.9, 0.5), Point(0.9, 0.9), Point(0.1, 0.9)).forEach { activity.tap(it) }

            fun touch(
                action: Int,
                x: Double,
                y: Double,
            ) = stage.dispatchTouchEvent(MotionEvent.obtain(0, 0, action, (x * stage.width).toFloat(), (y * stage.height).toFloat(), 0))
            // Drag corner 1 from (0.1, 0.5) to (0.2, 0.45); dragging never adds a corner.
            touch(MotionEvent.ACTION_DOWN, 0.1, 0.5)
            touch(MotionEvent.ACTION_MOVE, 0.2, 0.45)
            touch(MotionEvent.ACTION_MOVE, 2.0, 2.0) // outside the image: ignored
            touch(MotionEvent.ACTION_UP, 0.2, 0.45)
            assertEquals(0.2, stage.taps[0].x, 0.01)
            assertEquals(0.45, stage.taps[0].y, 0.01)
            assertEquals(4, stage.taps.size)
            activity.click("Save table")
            assertEquals(
                0.2,
                activity.settings.table!!
                    .points[0]
                    .x,
                0.01,
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 35]) // Robolectric 4.17 cannot run API 36 on this JDK (FileDescriptor internals)
class EdgeToEdgeTest {
    @Test
    fun contentStaysClearOfSystemBarsOnEveryScreen() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val bars = android.graphics.Insets.of(0, 63, 0, 48)
            val insets =
                android.view.WindowInsets
                    .Builder()
                    .setInsets(
                        android.view.WindowInsets.Type
                            .systemBars(),
                        bars,
                    ).build()
            for (screen in listOf(
                MainActivity.Screen.WELCOME,
                MainActivity.Screen.DEMO,
                MainActivity.Screen.SETTINGS,
                MainActivity.Screen.DATA,
            )) {
                activity.show(screen)
                val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                root.dispatchApplyWindowInsets(insets)
                assertEquals(screen.name, 63, root.paddingTop)
                assertEquals(screen.name, 48, root.paddingBottom)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de")
class GermanLocaleTest {
    @Test
    fun germanUserSeesGermanScreensAndDemo() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            assertTrue(activity.texts().contains("Kamera einrichten"))
            assertTrue(activity.texts().contains("Es werden keine Bilder oder Videos gespeichert"))
            activity.click("Demo ausprobieren (synthetisch)")
            idle(6000)
            assertTrue(activity.texts().contains("Platz 2 · Links: Ellbogen auf dem Tisch"))
            activity.click("Zurück")
            activity.click("Einstellungen")
            assertTrue(activity.texts().contains("Weitwinkel") || activity.texts().contains("Automatisch"))
        }
    }
}
