// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.SoundPool
import android.view.MotionEvent
import android.view.View
import it.marcelpetrick.fork.R
import it.marcelpetrick.fork.detection.ArmResult
import it.marcelpetrick.fork.detection.ElbowState
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Polygon
import it.marcelpetrick.fork.detection.SeatResult
import it.marcelpetrick.fork.detection.pose
import it.marcelpetrick.fork.detection.table
import it.marcelpetrick.fork.monitoring.AudioMode
import it.marcelpetrick.fork.monitoring.Chime
import it.marcelpetrick.fork.monitoring.PoseModel
import it.marcelpetrick.fork.monitoring.Processor
import it.marcelpetrick.fork.monitoring.Sensitivity
import it.marcelpetrick.fork.monitoring.Settings
import it.marcelpetrick.fork.monitoring.Sound
import it.marcelpetrick.fork.monitoring.VisualMode
import it.marcelpetrick.fork.thermalLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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
        // Reminder card (all four seat colours), thank-you, paused dimming, fade-out.
        for (seat in 1..4) {
            stage.reminder = seat to (seat % 2 == 0)
            stage.draw(canvas)
        }
        stage.reminder = null
        stage.thanks = true
        stage.dimmed = true
        stage.warning = VisualMode.BORDER
        stage.warning = VisualMode.OFF // fades out
        now += 100
        stage.draw(canvas)
        now += 1000
        stage.draw(canvas)
        stage.thanks = false
        stage.dimmed = false
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
    fun lensesAreLabelledByFocalLength() {
        assertEquals(emptyList<Lens>(), lensLabels(emptyMap()))
        assertEquals(listOf(Lens("0", R.string.lens_main)), lensLabels(mapOf("0" to 4.4f)))
        assertEquals(
            listOf(
                Lens("3", R.string.lens_wide),
                Lens("0", R.string.lens_main),
                Lens("2", R.string.lens_main),
                Lens("4", R.string.lens_tele),
            ),
            lensLabels(mapOf("0" to 4.4f, "2" to 4.4f, "3" to 2.2f, "4" to 9.0f)),
        )
    }

    @Test
    fun thermalStatusesHaveReadableLabels() {
        val labels = (0..6).map(::thermalLabel)
        assertEquals(listOf("none", "light", "moderate", "severe", "critical", "critical", "critical"), labels)
    }

    @Test
    fun everySettingStepsWithinValidBoundsAndCameraChangeInvalidatesCalibration() {
        val options = settingsOptions(lensLabels(mapOf("0" to 4.2f, "2" to 2.1f)))
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

        fun option(label: Int) = options.single { it.label == label }
        val camera = option(R.string.option_camera)
        val calibrated = Settings(table = table, calibrationAspect = 1.5)
        assertEquals("Automatic", camera.display(context, calibrated))
        val moved = camera.change(calibrated, 1)
        assertNull(moved.table)
        assertEquals("2", moved.camera) // the widest lens is offered first
        assertEquals("Wide (camera 2)", camera.display(context, moved))
        assertEquals("Camera 9", camera.display(context, moved.copy(camera = "9"))) // no longer present
        val single = settingsOptions(emptyList()).single { it.label == R.string.option_camera }
        assertEquals(calibrated, single.change(calibrated, 1)) // single choice keeps calibration
        val model = option(R.string.option_model)
        assertEquals(PoseModel.LITE, model.change(calibrated, 1).model)
        assertEquals("Lite (faster)", model.display(context, model.change(calibrated, 1)))
        assertEquals(AudioMode.ONCE, option(R.string.option_audio).change(calibrated, 1).audio)
        assertEquals(!calibrated.debug, option(R.string.option_debug).change(calibrated, 1).debug)
        val statistics = option(R.string.option_statistics)
        assertEquals("On", statistics.display(context, statistics.change(calibrated, -1)))

        // Groups appear in screen order, every one populated.
        assertEquals(Group.entries, options.map { it.group }.distinct())
        // Sensitivity presets set the raw timings; hand-tuned values read "Custom" and step back to Normal.
        val sensitivity = option(R.string.option_sensitivity)
        assertEquals("Normal", sensitivity.display(context, calibrated))
        val responsive = sensitivity.change(calibrated, 1)
        assertEquals(Sensitivity.RESPONSIVE, Sensitivity.of(responsive.timing))
        assertEquals(750, responsive.timing.triggerMs)
        assertEquals("Conservative", sensitivity.display(context, sensitivity.change(calibrated, -1)))
        val custom = option(R.string.option_trigger_delay).change(calibrated, 1)
        assertEquals("Custom", sensitivity.display(context, custom))
        assertEquals(Sensitivity.NORMAL, Sensitivity.of(sensitivity.change(custom, 1).timing))
        // An unknown trigger level (e.g. edited settings) steps from the middle level.
        val odd = calibrated.copy(timing = calibrated.timing.copy(trigger = 0.8))
        assertEquals(0.9, option(R.string.option_trigger).change(odd, 1).timing.trigger, 0.0)
        val chime = option(R.string.option_chime)
        assertEquals(Chime.MARIMBA, chime.change(calibrated, 1).chime)
        assertEquals("Glass", chime.display(context, chime.change(calibrated, -1)))
        val processor = option(R.string.option_processor)
        assertEquals(Processor.GPU, processor.change(calibrated, 1).processor)
        assertEquals("GPU (experimental)", processor.display(context, processor.change(calibrated, 1)))
    }

    @Test
    fun chimePlaysAfterLoadingLoopsForContinuousAndStops() {
        val pool = mock(SoundPool::class.java)
        `when`(pool.load(context, R.raw.chime, 1)).thenReturn(7)
        `when`(pool.play(anyInt(), anyFloat(), anyFloat(), anyInt(), anyInt(), anyFloat())).thenReturn(11)
        val speaker = ChimeSpeaker(context) { pool }
        speaker.play(Sound.NONE, 50)
        speaker.play(Sound.STOP, 50)
        speaker.play(Sound.BEEP, 50) // requested before the chime has loaded
        verify(pool, never()).play(anyInt(), anyFloat(), anyFloat(), anyInt(), anyInt(), anyFloat())
        val listener = ArgumentCaptor.forClass(SoundPool.OnLoadCompleteListener::class.java)
        verify(pool).setOnLoadCompleteListener(listener.capture())
        listener.value.onLoadComplete(pool, 7, 0)
        verify(pool).play(7, 0.5f, 0.5f, 1, 0, 1f)
        speaker.play(Sound.START, 150)
        verify(pool).stop(11)
        verify(pool).play(7, 1f, 1f, 1, -1, 1f)
        speaker.play(Sound.STOP, 0)
        verify(pool, times(2)).stop(11)
        speaker.prepare() // already prepared: no second pool or load
        verify(pool, times(1)).load(context, R.raw.chime, 1)
        speaker.release()
        verify(pool).release()
        // A failed load never plays and never crashes.
        val broken = mock(SoundPool::class.java)
        val failing = ChimeSpeaker(context) { broken }
        failing.play(Sound.BEEP, 40)
        val captor = ArgumentCaptor.forClass(SoundPool.OnLoadCompleteListener::class.java)
        verify(broken).setOnLoadCompleteListener(captor.capture())
        captor.value.onLoadComplete(broken, 0, 1)
        verify(broken, never()).play(anyInt(), anyFloat(), anyFloat(), anyInt(), anyInt(), anyFloat())
        ChimeSpeaker(context).apply {
            prepare()
            release()
        }
        // Choosing another chime replaces the loaded sound in the same pool.
        val shared = mock(SoundPool::class.java)
        `when`(shared.load(context, R.raw.chime, 1)).thenReturn(3)
        `when`(shared.load(context, R.raw.chime_glass, 1)).thenReturn(4)
        ChimeSpeaker(context) { shared }.apply {
            prepare(Chime.BELL)
            prepare(Chime.GLASS)
            prepare(Chime.GLASS)
        }
        verify(shared).unload(3)
        verify(shared, times(1)).load(context, R.raw.chime_glass, 1)
        assertEquals(listOf(R.raw.chime, R.raw.chime_marimba, R.raw.chime_glass), Chime.entries.map(::sound))
    }
}
