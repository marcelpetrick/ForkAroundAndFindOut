// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import java.util.concurrent.atomic.AtomicBoolean

/** Drops inference work while busy; CameraX independently retains only its latest frame. */
@androidx.annotation.OptIn(markerClass = [TransformExperimental::class])
class FrameAnalyzer(
    private val engine: PoseEngine,
    private val onInput: (Int, Int, Int, OutputTransform) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) : ImageAnalysis.Analyzer {
    private val busy = AtomicBoolean(false)
    private var timestamp = -1L

    @Volatile var enabled = true

    fun completed() {
        busy.set(false)
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (!enabled || !busy.compareAndSet(false, true)) return
            val bitmap = image.toBitmap()
            val degrees = image.imageInfo.rotationDegrees
            val rotated =
                if (degrees ==
                    0
                ) {
                    bitmap
                } else {
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
                }
            val transform = ImageProxyTransformFactory().apply { isUsingRotationDegrees = true }.getOutputTransform(image)
            timestamp = maxOf(clock(), timestamp + 1)
            onInput(rotated.width, rotated.height, degrees, transform)
            engine.submit(rotated, timestamp)
        } catch (error: Throwable) {
            completed()
            onError(recoverable(error))
        } finally {
            image.close()
        }
    }
}
