# Pose framework comparison and selection

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

Decision date: 2026-09-25. Product stack: native Kotlin Android throughout, as
explicitly selected by the user. This supersedes the Flutter recommendation in
`vision.md` without changing the product requirements.

## Requirements and market comparison

We need offline processing, 1–4 people, visible shoulder/elbow/wrist landmarks,
confidence estimates, manageable mobile latency, and a maintainable Android API.
No vendor benchmark establishes accuracy for our occluded dining-table setting.

| Option | Capabilities and integration | Fit for this product |
| --- | --- | --- |
| MediaPipe Pose Landmarker | Native Android Tasks API; 33 landmarks, image/world coordinates, configurable pose count, asynchronous live stream, Lite/Full/Heavy bundles | Selected: direct Kotlin integration, bundled offline models, confidence and tracking controls. Seated upper-body feasibility still needs measurement. |
| Google ML Kit Pose Detection | Android SDK with 33 landmarks and tracking; bundled model; beta; only the most confident person | Reject for v1: fails the 1–4-person requirement without a separate detection/cropping pipeline. |
| MoveNet MultiPose Lightning | Up to six people with 17 keypoints; shoulder/elbow/wrist are included. TensorFlow model/inference ecosystem | Viable fallback. Fewer landmarks alone is not disqualifying. Android tensor preprocessing, decoding and identity association require more work than the Tasks API. |
| RTMPose / MMPose | Multi-person pipeline, multiple keypoint sets; Android/ncnn example and MMDeploy/ONNX options | Strong fallback if occlusion is poor. Extra detector/export/native runtime maintenance; published model and pipeline timings must be distinguished. |
| Ultralytics YOLO Pose | Multi-person COCO keypoints, exportable models; model family supports training | Possible alternative, but export/runtime/postprocessing and AGPL/enterprise terms add integration and distribution decisions. No reason to start here without comparative scene evidence. |
| OpenPose | Multi-person body/hand/face estimation; C++ oriented; noncommercial license with separate commercial licensing | Poor initial fit for an ordinary Android phone and our GPL distribution plan. |

## Selected configuration

Use MediaPipe Tasks Vision, with exact Maven versions and model SHA-256 checksums.
Start with the Full bundle; offer Lite in settings. Bundle both models at build time
so monitoring has no network dependency. CPU is the compatibility baseline; GPU
is a later optimization only after measured benefit and lifecycle testing.

CameraX owns camera selection, preview and 720p-target analysis. Use
`STRATEGY_KEEP_ONLY_LATEST` and a single analysis executor. MediaPipe runs in
`LIVE_STREAM`; accept at most one in-flight inference, close every image, and discard
stale callbacks after pause/reconfiguration. The UI receives compact poses and
metrics, never copied full frames. Native preview and overlays must share a
well-defined normalized coordinate system including rotation and aspect ratio.

## Pose handling after inference

1. Validate finite coordinates and landmark visibility/presence; absent or uncertain
   joints produce UNKNOWN. A plausible model guess is not proof of contact.
2. Assign poses to configured seat regions using visible torso landmarks. When hips
   are hidden, use shoulder center consistently; do not let unreliable hip estimates
   drag the assignment. Without seats, gated nearest-center tracking is temporary
   identity only. Reject ambiguous associations and reset temporal evidence after gaps.
3. For each arm, compute signed table distance, arm lengths/angles, wrist relation,
   torso orientation and a short motion window normalized by body scale.
4. Use conservative rules for supported-contact evidence. Projected polygon overlap
   alone is insufficient. Scores are heuristic evidence, not calibrated probabilities.
5. Apply independent per-elbow dwell/hysteresis, unknown handling and cooldown.
   The alarm layer handles display/audio separately and stops on pause or uncertainty.
6. Collect labelled feature windows only after explicit opt-in. Keep session IDs for
   future train/validation/test splits; never random-split neighboring frames.

MediaPipe's world coordinates are body-relative estimates, not a calibrated table
plane. They cannot establish physical elbow/table contact by themselves.

## Evaluation and fallback triggers

First measure elbow visibility for 1, 2 and 4 seated people with the intended camera
position, sleeves, bowls, reaching and crossings. Record processed FPS, latency,
UNKNOWN fraction, false alarms per meal, and recall of contacts lasting over two
seconds. Aim for >=10 processed FPS; do not infer four-person performance from
single-person fitness demos. Warm up and run long enough to expose thermal throttling.

Change camera placement before adding model complexity. Compare Full and Lite on
identical consented sessions. If visible elbows remain unreliable, evaluate RTMPose
or MoveNet on held-out sessions through the pose adapter. Train a small contact
classifier only after labelled sessions exist. Crop classification, depth and a Pi
appliance remain conditional as specified in the vision.

## Primary sources

- [MediaPipe task and bundled models](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker)
- [Android API, live stream and pose-count options](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android)
- [MediaPipe Apache-2.0 source license](https://github.com/google-ai-edge/mediapipe/blob/master/LICENSE)
- [Maven artifact license metadata](https://dl.google.com/dl/android/maven2/com/google/mediapipe/tasks-vision/1.0.0/tasks-vision-1.0.0.pom)
- [ML Kit limitations and beta status](https://developers.google.com/ml-kit/vision/pose-detection)
- [MoveNet single/multi-pose configuration](https://github.com/tensorflow/tfjs-models/blob/master/pose-detection/src/movenet/README.md)
- [RTMPose Android deployment and benchmarks](https://github.com/open-mmlab/mmpose/blob/main/projects/rtmpose/README.md)
- [Ultralytics pose models](https://docs.ultralytics.com/tasks/pose/)
- [Ultralytics licensing](https://www.ultralytics.com/license)
- [OpenPose capabilities and license](https://github.com/CMU-Perceptual-Computing-Lab/openpose)
- [CameraX analysis and backpressure](https://developer.android.com/media/camera/camerax/analyze)

The source library's license and each downloaded model's terms must both be
preserved in distribution notices. This table is an engineering selection, not a
claim of comparative accuracy or a measured commercial-market share analysis.
