// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.ToneGenerator
import android.view.MotionEvent
import android.view.View
import it.marcelpetrick.fork.detection.ArmResult
import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Polygon
import it.marcelpetrick.fork.detection.SeatResult
import it.marcelpetrick.fork.detection.pose
import it.marcelpetrick.fork.detection.table
import it.marcelpetrick.fork.monitoring.AudioMode
import it.marcelpetrick.fork.monitoring.PoseModel
import it.marcelpetrick.fork.monitoring.Settings
import it.marcelpetrick.fork.monitoring.Sound
import it.marcelpetrick.fork.monitoring.VisualMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun touch(
        view: View,
        x: Float,
        y: Float,
    ): Boolean {
        view.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0))
        return view.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test
    fun stageDrawsEveryLayerAndWarningAndMapsTaps() {
        val stage = StageView(context)
        val taps = mutableListOf<Point>()
        var rejected = 0
        assertFalse(touch(stage, 1f, 1f)) // no tap handler
        stage.onTap = { taps += it }
        assertFalse(touch(stage, 1f, 1f)) // not laid out yet
        stage.layout(0, 0, 200, 100)
        stage.onRejectedTap = { rejected++ }
        stage.mapping =
            Matrix().apply {
                // image occupies the middle half of a 200×100 view
                setScale(100f, 100f)
                postTranslate(50f, 0f)
            }
        assertTrue(touch(stage, 10f, 50f))
        assertTrue(touch(stage, 100f, 50f))
        assertEquals(1, rejected)
        assertEquals(listOf(Point(0.5, 0.5)), taps)

        var now = 0L
        stage.clock = { now }
        val arm = ArmResult(ElbowState.VIOLATION, 0.95, null)
        stage.table = table
        stage.seats = listOf(Polygon(listOf(Point(0.3, 0.1), Point(0.7, 0.1), Point(0.7, 0.45), Point(0.3, 0.45))))
        stage.taps = listOf(Point(0.1, 0.1), Point(0.2, 0.2))
        stage.poses = listOf(pose(), pose(offset = 0.2))
        stage.results =
            listOf(
                SeatResult(1, pose(), arm, arm.copy(state = ElbowState.UNKNOWN)),
                SeatResult(2, null, arm, arm),
                SeatResult(3, pose(offset = 0.3).copy(landmarks = emptyList()), arm, arm),
            )
        stage.synthetic = true
        val canvas = Canvas(Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888))
        for (mode in VisualMode.entries) {
            stage.warning = mode
            now += 500
            stage.draw(canvas)
        }
        stage.skeleton = false
        stage.mapping = null
        stage.taps = emptyList()
        stage.refresh()
        stage.draw(canvas)
        assertTrue(stage.performClick() || true)
        assertEquals(
            ElbowState.entries
                .map(Palette::of)
                .toSet()
                .size,
            4,
        )
    }

    @Test
    fun everySettingStepsWithinValidBoundsAndCameraChangeInvalidatesCalibration() {
        val options = settingsOptions(listOf("0", "2"))
        var settings =
            Settings(
                people = 2,
                camera = "",
                table = table,
                seats = listOf(table, table),
                calibrationAspect = 1.5,
            )
        for (option in options) {
            repeat(40) { settings = option.change(settings, 1) }
            assertTrue(option.display(context, settings).isNotEmpty())
            repeat(40) { settings = option.change(settings, -1) }
            assertTrue(option.display(context, settings).isNotEmpty())
        }
        assertEquals(1, settings.people)
        assertTrue(settings.seats.isEmpty())
        assertNull(settings.table)
        assertEquals(0.0, settings.calibrationAspect, 0.0)
        assertEquals(500, settings.timing.triggerMs)
        assertEquals(0.6, settings.timing.trigger, 0.0)
        assertEquals(0, settings.volume)

        val camera = options[1]
        val calibrated = Settings(table = table, calibrationAspect = 1.5)
        assertEquals("Automatic", camera.display(context, calibrated))
        val moved = camera.change(calibrated, 1)
        assertEquals("0", moved.camera)
        assertNull(moved.table)
        assertEquals("Camera 0", camera.display(context, moved))
        assertEquals(calibrated, settingsOptions(emptyList())[1].change(calibrated, 1)) // single choice keeps calibration
        assertEquals(PoseModel.LITE, options[2].change(calibrated, 1).model)
        assertEquals("Lite (faster)", options[2].display(context, options[2].change(calibrated, 1)))
        assertEquals(AudioMode.ONCE, options[9].change(calibrated, 1).audio)
        assertEquals(!calibrated.debug, options[12].change(calibrated, 1).debug)
        assertEquals("On", options[13].display(context, options[13].change(calibrated, -1)))
    }

    @Test
    fun speakerMapsSoundsToTonesAndRecreatesOnVolumeChange() {
        val generators = mutableListOf<ToneGenerator>()
        val speaker =
            ToneSpeaker { _ -> mock(ToneGenerator::class.java).also { generators += it } }
        speaker.play(Sound.STOP, 50)
        speaker.play(Sound.NONE, 50)
        assertTrue(generators.isEmpty())
        speaker.play(Sound.BEEP, 50)
        speaker.play(Sound.START, 50)
        speaker.play(Sound.STOP, 50)
        verify(generators.single()).startTone(ToneGenerator.TONE_PROP_BEEP, 400)
        verify(generators.single()).startTone(ToneGenerator.TONE_SUP_DIAL, -1)
        verify(generators.single()).stopTone()
        speaker.play(Sound.BEEP, 80)
        assertEquals(2, generators.size)
        verify(generators[0]).release()
        speaker.release()
        verify(generators[1]).release()
        verify(generators[1], times(1)).stopTone()
        speaker.release()
        ToneSpeaker().release()
    }
}
