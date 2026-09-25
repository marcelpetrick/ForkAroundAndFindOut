// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Looper
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraState
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.OutputTransform
import androidx.lifecycle.MutableLiveData
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.detection.pose
import it.marcelpetrick.fork.monitoring.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.nio.ByteBuffer
import java.util.Optional
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * An 80×40 RGBA frame whose top-left pixel is magenta; [padding] adds row-stride bytes.
 * Magenta is symmetric under R/B swaps, so the check holds for Robolectric's host BGRA too.
 */
fun image(
    degrees: Int = 0,
    padding: Int = 0,
): ImageProxy {
    val image = mock(ImageProxy::class.java)
    val info = mock(ImageInfo::class.java)
    val plane = mock(ImageProxy.PlaneProxy::class.java)
    val stride = 80 * 4 + padding
    val pixels = ByteBuffer.allocateDirect(stride * 40)
    pixels.put(0, 0xFF.toByte()).put(2, 0xFF.toByte()).put(3, 0xFF.toByte()) // R, B, A of pixel (0, 0)
    `when`(plane.buffer).thenReturn(pixels)
    `when`(plane.rowStride).thenReturn(stride)
    `when`(plane.pixelStride).thenReturn(4)
    `when`(image.planes).thenReturn(arrayOf(plane))
    `when`(image.width).thenReturn(80)
    `when`(image.height).thenReturn(40)
    `when`(image.cropRect).thenReturn(Rect(0, 0, 80, 40))
    `when`(image.imageInfo).thenReturn(info)
    `when`(info.rotationDegrees).thenReturn(degrees)
    `when`(info.sensorToBufferTransformMatrix).thenReturn(Matrix())
    return image
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowLandmarker::class])
class CameraTest {
    @Test
    fun analyzerClosesEveryFrameDropsBacklogAndRecoversFromErrors() {
        val engine = mock(PoseEngine::class.java)
        val inputs = mutableListOf<Pair<Int, Int>>()
        var errors = 0
        val analyzer = FrameAnalyzer(engine, { w, h, _, _ -> inputs += w to h }, { errors++ }, { 100 })
        val first = image()
        analyzer.analyze(first)
        verify(first).close()
        analyzer.analyze(image()) // busy, discard
        assertEquals(1, analyzer.dropped)
        assertEquals(1, inputs.size)
        analyzer.completed()
        analyzer.analyze(image(90))
        assertEquals(listOf(80 to 40, 40 to 80), inputs)
        analyzer.enabled = false
        analyzer.completed()
        analyzer.analyze(image())
        assertEquals(2, inputs.size)
        analyzer.enabled = true
        doThrow(
            IllegalStateException("native failure"),
        ).`when`(engine).submit(any(Bitmap::class.java) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), anyLong())
        val failed = image()
        analyzer.analyze(failed)
        verify(failed).close()
        assertEquals(1, errors)
        analyzer.analyze(image())
        assertEquals(2, errors)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real pixel copies and canvas rotation
    fun analyzerRotatesIntoReusedBitmapsAndRemovesRowPadding() {
        val marker = mutableListOf<Pair<Int, Int>>()
        val sizes = mutableListOf<Pair<Int, Int>>()
        val frames = mutableListOf<Int>()
        lateinit var analyzer: FrameAnalyzer
        val engine =
            object : PoseEngine {
                override fun submit(
                    bitmap: Bitmap,
                    timeMs: Long,
                ) {
                    frames += System.identityHashCode(bitmap)
                    sizes += bitmap.width to bitmap.height
                    val hit =
                        (0 until bitmap.width).flatMap { x -> (0 until bitmap.height).map { y -> x to y } }.single { (x, y) ->
                            bitmap.getPixel(x, y) ==
                                Color.MAGENTA
                        }
                    marker += hit
                    analyzer.completed()
                }

                override fun close() = Unit
            }
        analyzer = FrameAnalyzer(engine, { _, _, _, _ -> }, { throw AssertionError(it) }, { 1 })
        for ((degrees, padding) in listOf(0 to 0, 90 to 16, 180 to 0, 270 to 16, 90 to 16)) analyzer.analyze(image(degrees, padding))
        assertEquals(listOf(80 to 40, 40 to 80, 80 to 40, 40 to 80, 40 to 80), sizes)
        // Sensor pixel (0, 0) lands where a clockwise rotation puts the top-left corner.
        assertEquals(listOf(0 to 0, 39 to 0, 79 to 39, 0 to 79, 39 to 0), marker)
        assertEquals("a steady stream reuses the same bitmap", frames[3], frames[4])
        assertEquals(0, analyzer.dropped)
    }

    @Test
    fun engineMapsConfidenceAndReleasesInputOnResultErrorAndClose() {
        val context = RuntimeEnvironment.getApplication()
        val native = mock(PoseLandmarker::class.java)
        lateinit var options: PoseLandmarker.PoseLandmarkerOptions
        var poses = emptyList<Pose>()
        var errors = 0
        val engine =
            MediaPipeEngine(context, Settings(), { p, t ->
                poses = p
                assertEquals(100, t)
            }, { errors++ }, { _, o ->
                options = o
                native
            })
        assertNotNull(options) // built through the factory seam; no reflection into MediaPipe
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        engine.submit(bitmap, 100)
        assertThrows(IllegalStateException::class.java) { engine.submit(bitmap, 101) }
        val result = mock(PoseLandmarkerResult::class.java)
        `when`(result.timestampMs()).thenReturn(100)
        `when`(
            result.landmarks(),
        ).thenReturn(
            listOf(
                listOf(
                    NormalizedLandmark.create(0.2f, 0.3f, 0f, Optional.of(0.9f), Optional.of(0.8f)),
                    NormalizedLandmark.create(0.1f, 0.2f, 0f),
                ),
            ),
        )
        engine.handle(result)
        assertFalse("the analyzer reuses its bitmap; the engine must not recycle it", bitmap.isRecycled)
        assertEquals(0.8, poses.single().landmarks[0].confidence, 0.001)
        assertEquals(
            0.2,
            poses
                .single()
                .landmarks[0]
                .point.x,
            0.001,
        )
        assertEquals(0.0, poses.single().landmarks[1].confidence, 0.001)
        engine.submit(bitmap, 200)
        engine.fail(IllegalStateException("inference failed"))
        assertEquals(1, errors)
        doThrow(IllegalStateException("bad frame")).`when`(native).detectAsync(any(MPImage::class.java), anyLong())
        assertThrows(IllegalStateException::class.java) { engine.submit(bitmap, 300) }
        engine.close()
        verify(native).close()
        assertFalse(bitmap.isRecycled)
    }

    @Test
    fun sessionBindsMapsCoordinatesAndIgnoresLateCallbacks() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val preview = mock(PreviewView::class.java)
            val provider = mock(ProcessCameraProvider::class.java)
            val engine = mock(PoseEngine::class.java)
            `when`(preview.width).thenReturn(80)
            `when`(preview.height).thenReturn(40)
            `when`(preview.outputTransform).thenReturn(
                OutputTransform(
                    Matrix().apply {
                        setScale(40f, 20f)
                        postTranslate(40f, 20f)
                    },
                    Size(80, 40),
                ),
            )
            lateinit var callback: (List<Pose>, Long) -> Unit
            lateinit var error: (Exception) -> Unit
            var calls = 0
            var errors = 0
            val session =
                CameraSession(activity, activity, preview, Settings(), { p, _, info ->
                    assertEquals(FrameInfo(2.0, 0, info.latencyMs), info)
                    assertEquals(33, p.single().landmarks.size)
                    calls++
                }, { errors++ }, { Futures.immediateFuture(provider) }, MoreExecutors.newDirectExecutorService(), { _, _, r, e ->
                    callback =
                        r
                    ; error = e
                    engine
                })
            session.start()
            shadowOf(Looper.getMainLooper()).idle()
            assertNotNull(session.analyzer)
            session.analyzer!!.analyze(image())
            assertEquals(null, session.mapping)
            callback(listOf(pose()), 100)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, calls)
            // Landmarks stay in image space; the mapping carries image (0..1) into view pixels.
            val corner = floatArrayOf(1f, 1f).also { session.mapping!!.mapPoints(it) }
            assertTrue(corner[0] > 0f && corner[1] > 0f)
            error(IllegalStateException("lost camera"))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, errors)
            session.close()
            session.close()
            callback(listOf(pose()), 200)
            error(IllegalStateException("late"))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, calls)
            assertEquals(1, errors)
            verify(provider).unbindAll()
            verify(engine).close()
            assertFalse(session.analyzer!!.enabled)
        }
    }

    @Test
    fun startupAndProviderFailuresAreActionableAndCloseBeforeSetupIsSafe() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { c ->
            val activity = c.get()
            val preview = PreviewView(activity)
            var errors = 0
            val failure =
                CameraSession(activity, activity, preview, Settings(), { _, _, _ -> }, {
                    errors++
                }, executor = MoreExecutors.newDirectExecutorService(), engineFactory = {
                    _,
                    _,
                    _,
                    _,
                    ->
                    error("model absent")
                })
            failure.start()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, errors)
            failure.close()
            val engine = mock(PoseEngine::class.java)
            val failedProvider =
                CameraSession(activity, activity, preview, Settings(camera = "2"), { _, _, _ -> }, {
                    errors++
                }, {
                    Futures.immediateFailedFuture(IllegalStateException("camera absent"))
                }, MoreExecutors.newDirectExecutorService(), { _, _, _, _ -> engine })
            failedProvider.start()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, errors)
            failedProvider.close()
            val pending = QueuedExecutor()
            val closed =
                CameraSession(activity, activity, preview, Settings(), { _, _, _ ->
                }, { errors++ }, executor = pending, engineFactory = {
                    _,
                    _,
                    _,
                    _,
                    ->
                    engine
                })
            closed.start()
            closed.close()
            pending.tasks.forEach { it.run() }
            assertTrue(pending.isShutdown)
            verify(engine, times(2)).close()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraFailureTest {
    @Test
    fun nativeLinkFailuresBecomeMessagesButProgrammingErrorsPropagate() {
        val link = UnsatisfiedLinkError("no libmediapipe")
        val memory = OutOfMemoryError("frame")
        val exception = IllegalStateException("x")
        assertTrue(recoverable(link) === link)
        assertTrue(recoverable(memory) === memory)
        assertTrue(recoverable(exception) === exception)
        assertThrows(AssertionError::class.java) { recoverable(AssertionError("bug")) }
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { c ->
            val activity = c.get()
            val messages = mutableListOf<String>()
            CameraSession(activity, activity, PreviewView(activity), Settings(), { _, _, _ -> }, {
                messages += it
            }, executor = MoreExecutors.newDirectExecutorService(), engineFactory = {
                _,
                _,
                _,
                _,
                ->
                throw link
            }).start()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(messages.single().contains("no libmediapipe"))
        }
    }

    @Test
    fun cameraTakenByAnotherAppPausesWithMessage() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { c ->
            val activity = c.get()
            val camera = mock(Camera::class.java)
            val provider =
                mock(ProcessCameraProvider::class.java) { call ->
                    if (call.method.name == "bindToLifecycle") camera else RETURNS_DEFAULTS.answer(call)
                }
            val info = mock(CameraInfo::class.java)
            val state = MutableLiveData<CameraState>()
            `when`(camera.cameraInfo).thenReturn(info)
            `when`(info.cameraState).thenReturn(state)
            val messages = mutableListOf<String>()
            val session =
                CameraSession(activity, activity, PreviewView(activity), Settings(), { _, _, _ -> }, {
                    messages += it
                }, {
                    Futures.immediateFuture(
                        provider,
                    )
                }, MoreExecutors.newDirectExecutorService(), { _, _, _, _ -> mock(PoseEngine::class.java) })
            session.start()
            shadowOf(Looper.getMainLooper()).idle()
            state.value = CameraState.create(CameraState.Type.OPEN)
            state.value = CameraState.create(CameraState.Type.CLOSED, CameraState.StateError.create(CameraState.ERROR_CAMERA_IN_USE))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(messages.single().contains("in use by another app"))
            state.value = CameraState.create(CameraState.Type.CLOSED, CameraState.StateError.create(CameraState.ERROR_CAMERA_FATAL_ERROR))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(messages.last().contains("camera error"))
            session.close()
        }
    }
}

class QueuedExecutor : AbstractExecutorService() {
    val tasks = mutableListOf<Runnable>()
    private var stopped = false

    override fun execute(command: Runnable) {
        tasks += command
    }

    override fun shutdown() {
        stopped = true
    }

    override fun shutdownNow(): MutableList<Runnable> = tasks

    override fun isShutdown(): Boolean = stopped

    override fun isTerminated(): Boolean = stopped

    override fun awaitTermination(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = true
}

/** JVM cannot load Android JNI. Instrumentation separately runs the real native model. */
@Implements(value = PoseLandmarker::class, isInAndroidSdk = false)
class ShadowLandmarker {
    companion object {
        @JvmStatic
        @Implementation
        @Suppress("ktlint:standard:function-naming")
        fun __staticInitializer__() = Unit
    }
}
