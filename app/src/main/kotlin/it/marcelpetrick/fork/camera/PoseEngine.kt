// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.camera

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import it.marcelpetrick.fork.detection.Landmark
import it.marcelpetrick.fork.detection.Point
import it.marcelpetrick.fork.detection.Pose
import it.marcelpetrick.fork.monitoring.Settings
import java.util.concurrent.atomic.AtomicReference

interface PoseEngine : AutoCloseable {
    fun submit(
        bitmap: Bitmap,
        timeMs: Long,
    )
}

/** Native inference with explicit input ownership until its asynchronous completion. */
class MediaPipeEngine(
    context: Context,
    settings: Settings,
    onResult: (List<Pose>, Long) -> Unit,
    onError: (Exception) -> Unit,
    factory: (Context, PoseLandmarker.PoseLandmarkerOptions) -> PoseLandmarker = PoseLandmarker::createFromOptions,
) : PoseEngine {
    private val pending = AtomicReference<MPImage?>()
    private val landmarker =
        factory(
            context,
            PoseLandmarker.PoseLandmarkerOptions
                .builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(settings.model.asset).build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(settings.people)
                .setMinPoseDetectionConfidence(0.6f)
                .setMinPosePresenceConfidence(0.6f)
                .setMinTrackingConfidence(0.6f)
                .setResultListener { result, _ ->
                    val poses =
                        result.landmarks().map { joints ->
                            Pose(
                                joints.map { joint ->
                                    Landmark(
                                        Point(joint.x().toDouble(), joint.y().toDouble()),
                                        minOf(joint.visibility().orElse(0f), joint.presence().orElse(0f)).toDouble(),
                                    )
                                },
                            )
                        }
                    pending.getAndSet(null)?.close()
                    onResult(poses, result.timestampMs())
                }.setErrorListener { error ->
                    pending.getAndSet(null)?.close()
                    onError(error)
                }.build(),
        )

    override fun submit(
        bitmap: Bitmap,
        timeMs: Long,
    ) {
        val image = BitmapImageBuilder(bitmap).build()
        if (!pending.compareAndSet(null, image)) {
            image.close()
            error("Only one inference may be in flight")
        }
        try {
            landmarker.detectAsync(image, timeMs)
        } catch (error: Exception) {
            pending.getAndSet(null)?.close()
            throw error
        }
    }

    override fun close() {
        landmarker.close()
        pending.getAndSet(null)?.close()
    }
}
