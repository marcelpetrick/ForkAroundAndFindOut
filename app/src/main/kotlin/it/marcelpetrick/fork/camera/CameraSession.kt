// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.camera

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
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
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.monitoring.Settings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Preview and inference remain native. Start/close are called on the main thread. */
@androidx.annotation.OptIn(markerClass = [TransformExperimental::class, ExperimentalCamera2Interop::class])
class CameraSession(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val previewView: PreviewView,
    private val settings: Settings,
    private val onFrame: (List<Pose>, Long, Double, Long) -> Unit,
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
) : AutoCloseable {
    private val main = ContextCompat.getMainExecutor(context)

    @Volatile private var closed = false
    private var provider: ProcessCameraProvider? = null
    private var engine: PoseEngine? = null
    internal var analyzer: FrameAnalyzer? = null
        private set

    private data class Input(
        val width: Int,
        val height: Int,
        val transform: OutputTransform,
    )

    @Volatile private var input: Input? = null

    fun start() {
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        executor.execute {
            try {
                engine = engineFactory(context, settings, ::result, ::failed)
                if (closed) return@execute
                main.execute { if (!closed) bind() }
            } catch (error: Exception) {
                failed(error)
            }
        }
    }

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
                                ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                            ).build()
                    val preview = Preview.Builder().setResolutionSelector(resolution).build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val analysis =
                        ImageAnalysis
                            .Builder()
                            .setResolutionSelector(resolution)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                    analyzer =
                        FrameAnalyzer(engine!!, { width, height, transform, _ ->
                            input = Input(width, height, transform)
                        }, ::failed)
                    analysis.setAnalyzer(executor, analyzer!!)
                    provider!!.bindToLifecycle(owner, selector, preview, analysis)
                } catch (error: Exception) {
                    failed(error)
                }
            }
        }, main)
    }

    private fun result(
        poses: List<Pose>,
        time: Long,
    ) {
        val snapshot = input
        val inputTransform = snapshot?.transform
        val width = snapshot?.width ?: 0
        val height = snapshot?.height ?: 0
        main.execute {
            try {
                val target = previewView.outputTransform
                if (!closed && target != null && inputTransform != null && previewView.width > 0 && previewView.height > 0) {
                    val transform = CoordinateTransform(inputTransform, target)
                    val mapped =
                        poses.map { pose ->
                            Pose(
                                pose.landmarks.map { joint ->
                                    val xy = floatArrayOf((joint.point.x * width).toFloat(), (joint.point.y * height).toFloat())
                                    transform.mapPoints(xy)
                                    joint.copy(point = Point(xy[0].toDouble() / previewView.width, xy[1].toDouble() / previewView.height))
                                },
                            )
                        }
                    onFrame(mapped, time, previewView.width.toDouble() / previewView.height, SystemClock.uptimeMillis() - time)
                }
            } catch (error: Exception) {
                failed(error)
            } finally {
                analyzer?.completed()
            }
        }
    }

    private fun failed(error: Exception) {
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
