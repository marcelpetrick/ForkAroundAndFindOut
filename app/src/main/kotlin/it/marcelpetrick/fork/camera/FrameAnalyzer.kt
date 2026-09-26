// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.core.graphics.createBitmap
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drops inference work while busy; CameraX independently retains only its latest frame.
 * Pixels are copied into two reused bitmaps (sensor-oriented and upright), so the steady
 * state allocates nothing per frame. Reuse is safe: exactly one inference is in flight
 * and [completed] is only called after MediaPipe has released its input.
 */
@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
class FrameAnalyzer(
    private val engine: PoseEngine,
    private val onInput: (Int, Int, Int, OutputTransform) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) : ImageAnalysis.Analyzer {
    private val busy = AtomicBoolean(false)
    private var timestamp = -1L
    private var sensor: Bitmap? = null
    private var upright: Bitmap? = null
    private var packed: ByteBuffer? = null

    // SRC replaces every target pixel, so a reused bitmap never keeps stale content.
    private val copyPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }

    @Volatile var enabled = true

    /** Frames discarded because the previous inference was still running. */
    @Volatile var dropped = 0L
        private set

    fun completed() {
        busy.set(false)
    }

    @Suppress("TooGenericExceptionCaught") // any native frame failure must release the frame and be reported
    override fun analyze(image: ImageProxy) {
        try {
            if (!enabled) return
            if (!busy.compareAndSet(false, true)) {
                dropped++
                return
            }
            val degrees = image.imageInfo.rotationDegrees
            val frame = rotate(copy(image), degrees)
            val transform = ImageProxyTransformFactory().apply { isUsingRotationDegrees = true }.getOutputTransform(image)
            timestamp = maxOf(clock(), timestamp + 1)
            onInput(frame.width, frame.height, degrees, transform)
            engine.submit(frame, timestamp)
        } catch (error: Throwable) {
            completed()
            onError(recoverable(error))
        } finally {
            image.close()
        }
    }

    private fun reuse(
        current: Bitmap?,
        width: Int,
        height: Int,
    ): Bitmap =
        current?.takeIf { it.width == width && it.height == height }
            ?: createBitmap(width, height)

    /** RGBA_8888 plane into the reused sensor-oriented bitmap, removing row padding. */
    private fun copy(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val width = image.width
        val height = image.height
        val target = reuse(sensor, width, height).also { sensor = it }
        val source = plane.buffer.apply { rewind() }
        val row = width * RGBA_BYTES
        if (plane.rowStride == row) {
            target.copyPixelsFromBuffer(source)
        } else {
            val compact = packed?.takeIf { it.capacity() == row * height } ?: ByteBuffer.allocateDirect(row * height).also { packed = it }
            compact.clear()
            for (y in 0 until height) {
                source.limit(y * plane.rowStride + row).position(y * plane.rowStride)
                compact.put(source)
            }
            source.clear()
            target.copyPixelsFromBuffer(compact.apply { rewind() })
        }
        return target
    }

    private fun rotate(
        source: Bitmap,
        degrees: Int,
    ): Bitmap {
        if (degrees == 0) return source
        val quarter = degrees == QUARTER || degrees == THREE_QUARTERS
        val width = if (quarter) source.height else source.width
        val height = if (quarter) source.width else source.height
        val target = reuse(upright, width, height).also { upright = it }
        val matrix =
            Matrix().apply {
                postRotate(degrees.toFloat())
                when (degrees) {
                    QUARTER -> postTranslate(source.height.toFloat(), 0f)
                    HALF -> postTranslate(source.width.toFloat(), source.height.toFloat())
                    else -> postTranslate(0f, source.width.toFloat())
                }
            }
        Canvas(target).drawBitmap(source, matrix, copyPaint)
        return target
    }
}

private const val RGBA_BYTES = 4
private const val QUARTER = 90
private const val HALF = 180
private const val THREE_QUARTERS = 270
