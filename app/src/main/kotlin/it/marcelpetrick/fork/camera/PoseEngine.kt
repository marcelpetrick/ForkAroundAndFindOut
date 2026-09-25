// Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
package it.marcelpetrick.fork.camera

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
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

/**
 * Native inference with one frame in flight. The caller owns and reuses the bitmap, so the
 * `MPImage` wrapper is only tracked, never closed: closing it would recycle that bitmap.
 */
class MediaPipeEngine(
    context: Context,
    settings: Settings,
    private val onResult: (List<Pose>, Long) -> Unit,
    private val onError: (Exception) -> Unit,
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
                .setResultListener { result, _ -> handle(result) }
                .setErrorListener { error -> fail(error) }
                .build(),
        )

    /** MediaPipe result callback: joint confidence is min(visibility, presence). */
    internal fun handle(result: PoseLandmarkerResult) {
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
        pending.set(null)
        onResult(poses, result.timestampMs())
    }

    /** MediaPipe error callback. */
    internal fun fail(error: RuntimeException) {
        pending.set(null)
        onError(error)
    }

    override fun submit(
        bitmap: Bitmap,
        timeMs: Long,
    ) {
        val image = BitmapImageBuilder(bitmap).build()
        if (!pending.compareAndSet(null, image)) {
            error("Only one inference may be in flight")
        }
        try {
            landmarker.detectAsync(image, timeMs)
        } catch (error: Exception) {
            pending.set(null)
            throw error
        }
    }

    override fun close() {
        landmarker.close()
        pending.set(null)
    }
}
