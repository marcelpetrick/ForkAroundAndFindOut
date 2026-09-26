// SPDX-FileCopyrightText: 2026 Marcel Petrick
// SPDX-License-Identifier: GPL-3.0-or-later
package it.marcelpetrick.fork.camera

import android.content.Context
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.OutputTransform
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.monitoring.Processor
import it.marcelpetrick.fork.monitoring.Settings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Geometry of one analysed frame: rotated-image aspect (w/h), sensor rotation, latency. */
data class FrameInfo(
    val aspect: Double,
    val rotation: Int,
    val latencyMs: Long,
)

/**
 * A running pose source. Poses are delivered in the canonical coordinate system:
 * normalized coordinates of the upright (rotated) analysis image. [mapping] converts
 * that space to preview-view pixels for drawing and, inverted, for calibration taps.
 */
interface FrameSource : AutoCloseable {
    val mapping: Matrix?

    /** Frames the camera delivered while an inference was still running (not analysed). */
    val dropped: Long
        get() = 0

    /** Where inference runs once started, or null before the engine exists. */
    val processor: Processor?
        get() = null

    fun start()
}

/** Only exceptions and recoverable native failures become a message; anything else is rethrown. */
internal fun recoverable(error: Throwable): Throwable =
    if (error is Exception || error is LinkageError || error is OutOfMemoryError) error else throw error

/** Preview and inference remain native. Start/close are called on the main thread. */
@androidx.annotation.OptIn(markerClass = [TransformExperimental::class, ExperimentalCamera2Interop::class])
class CameraSession(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val previewView: PreviewView,
    private val settings: Settings,
    private val onFrame: (List<Pose>, Long, FrameInfo) -> Unit,
    private val onError: (String) -> Unit,
    private val providerFactory: (
        Context,
    ) -> com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider::getInstance,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
    private val engineFactory: (
        Context,
        Settings,
        (List<Pose>, Long) -> Unit,
        (Exception) -> Unit,
    ) -> PoseEngine = { c, s, r, e -> MediaPipeEngine(c, s, r, e) },
) : FrameSource {
    private val main = ContextCompat.getMainExecutor(context)

    @Volatile private var closed = false
    private var provider: ProcessCameraProvider? = null
    private var engine: PoseEngine? = null
    internal var analyzer: FrameAnalyzer? = null
        private set

    private data class Input(
        val width: Int,
        val height: Int,
        val rotation: Int,
        val transform: OutputTransform,
    )

    @Volatile private var input: Input? = null

    override var mapping: Matrix? = null
        private set

    override val dropped: Long
        get() = analyzer?.dropped ?: 0

    override val processor: Processor?
        get() = engine?.processor

    @Suppress("TooGenericExceptionCaught") // native/camera failures become a recoverable message
    override fun start() {
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        executor.execute {
            try {
                engine = engineFactory(context, settings, ::result, ::failed)
                if (closed) return@execute
                main.execute { if (!closed) bind() }
            } catch (error: Throwable) {
                failed(recoverable(error))
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // native/camera failures become a recoverable message
    private fun bind() {
        val future = providerFactory(context)
        future.addListener({
            if (!closed) {
                try {
                    provider = future.get()
                    val selector =
                        CameraSelector
                            .Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .apply {
                                if (settings.camera.isNotEmpty()) {
                                    addCameraFilter { infos ->
                                        infos.filter {
                                            Camera2CameraInfo.from(it).cameraId ==
                                                settings.camera
                                        }
                                    }
                                }
                            }.build()
                    val resolution =
                        ResolutionSelector
                            .Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(PREVIEW_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                            ).build()
                    val preview = Preview.Builder().setResolutionSelector(resolution).build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    // Pose models run at 256 px input; a small analysis stream saves copies and power.
                    val analysisResolution =
                        ResolutionSelector
                            .Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                            ).build()
                    val analysis =
                        ImageAnalysis
                            .Builder()
                            .setResolutionSelector(analysisResolution)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                    analyzer =
                        FrameAnalyzer(engine!!, { width, height, rotation, transform ->
                            input = Input(width, height, rotation, transform)
                        }, ::failed)
                    analysis.setAnalyzer(executor, analyzer!!)
                    val camera = provider!!.bindToLifecycle(owner, selector, preview, analysis)
                    camera?.cameraInfo?.cameraState?.observe(owner) { state -> state.error?.let { cameraFailed(it.code) } }
                } catch (error: Exception) {
                    failed(error)
                }
            }
        }, main)
    }

    private fun cameraFailed(code: Int) {
        val inUse = code == CameraState.ERROR_CAMERA_IN_USE || code == CameraState.ERROR_MAX_CAMERAS_IN_USE
        failed(IllegalStateException(if (inUse) "the camera is in use by another app" else "camera error $code"))
    }

    @Suppress("TooGenericExceptionCaught") // native/camera failures become a recoverable message
    private fun result(
        poses: List<Pose>,
        time: Long,
    ) {
        // Take this frame's geometry before freeing the analyzer: the next frame overwrites it.
        val snapshot = input
        // Free the analyzer on the callback thread so UI work never lengthens the inference period.
        analyzer?.completed()
        if (snapshot == null) return
        main.execute {
            try {
                if (!closed) {
                    mapping = viewMapping(snapshot)
                    onFrame(
                        poses,
                        time,
                        FrameInfo(snapshot.width.toDouble() / snapshot.height, snapshot.rotation, SystemClock.uptimeMillis() - time),
                    )
                }
            } catch (error: Exception) {
                failed(error)
            }
        }
    }

    /** Normalized upright-image coordinates to preview-view pixels, or null before layout. */
    private fun viewMapping(snapshot: Input): Matrix? {
        val target = previewView.outputTransform ?: return null
        if (previewView.width <= 0 || previewView.height <= 0) return null
        return Matrix().also {
            CoordinateTransform(snapshot.transform, target).transform(it)
            it.preScale(snapshot.width.toFloat(), snapshot.height.toFloat())
        }
    }

    private fun failed(error: Throwable) {
        analyzer?.completed()
        main.execute { if (!closed) onError("Camera or model unavailable: ${error.message}. Retry setup or choose Lite.") }
    }

    override fun close() {
        if (closed) return
        closed = true
        analyzer?.enabled = false
        provider?.unbindAll()
        executor.execute { engine?.close() }
        executor.shutdown()
    }
}

private val PREVIEW_SIZE = Size(1280, 720)

/** Pose models run at 256 px input; 640×360 keeps copies small. */
private val ANALYSIS_SIZE = Size(640, 360)
