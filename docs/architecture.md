# Architecture

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

> MediaPipe determines where the body is. Our own lightweight temporal classifier
> determines whether an elbow is resting on the table. (vision §34)

## Modules

| Module | Kind | Content |
| --- | --- | --- |
| `:detection` | Kotlin/JVM, no Android | `Geometry` (points, convex polygons, signed distance, overlap), `Pose`/`ArmClassifier` (features and the conservative rule), `SeatTracker` (seat association without identity), `SeatProposal` (seat regions suggested from the table edges), `TemporalFilter` (UNKNOWN/CLEAR/SUSPECT/VIOLATION with hysteresis), `Detector`. Shared synthetic fixtures in `testFixtures`. |
| `:core` | Kotlin/JVM, no Android | `Settings` model (with `Sensitivity` presets, `Chime`, `Processor`), `MonitorSession` (one meal: grace, reminders, false-alarm rest, thank-you, status, summary; emits an immutable `MonitorUiState` the activity only renders), `Monitor` (freshness/continuity budgets, health, violation counting), `AlarmPolicy`, `VisibilityCheck` (with the reason it fails), `SessionLog` + `Replay`, `Json`, `SyntheticDemo`. |
| `:tools` | JVM application | `replay` / `demo-log` command line over session logs (`scripts/replay.sh`). |
| `:app` | Android (API 34+, target 37) | `CameraSession` (CameraX preview + analysis), `FrameAnalyzer` (allocation-free frame copy/rotation), `MediaPipeEngine` (CPU, or GPU with CPU fallback), `StageView` (overlay, taps, drags, loupe, warnings), `ChimeSpeaker`, `LocalStore`, `SessionRecorder`, `SettingsCodec`, `MainActivity` (screens). |

Kover merges all four modules into one report; the 95 % line gate applies to the total.

## Runtime pipeline

```
 CameraX Preview (720p) ─────────────────────────────► PreviewView (FIT_CENTER)
 CameraX ImageAnalysis (640×360 RGBA, KEEP_ONLY_LATEST)
   │ analysis thread
   ▼
 FrameAnalyzer ── busy? drop (counted) ── copy plane → reused bitmap → rotate upright
   │
   ▼ one frame in flight
 MediaPipe PoseLandmarker (LIVE_STREAM, Full/Lite, ≤4 poses, 0.6 gates)
   │ MediaPipe thread: result → analyzer.completed()
   ▼ main thread
 MainActivity.onFrame(poses, capture time, FrameInfo(aspect, rotation, latency))
   ├─ Position: VisibilityCheck (10 s: people, arms visible, FPS)
   ├─ MonitorSession.frame → Monitor.frame → Detector.process → per-seat, per-arm states
   │     └─ SessionRecorder (training mode only): landmarks + live states
   └─ StageView (image→view matrix) draws skeletons, table, seats, warnings
 100 ms watchdog tick: MonitorSession.tick → MonitorUiState → StageView, ChimeSpeaker
```

## One coordinate system

Landmarks, table polygon, seat regions, taps, session logs and replay all use
**normalized coordinates of the upright analysis image**. `CameraSession` provides the
image→view matrix (CameraX `CoordinateTransform`); the overlay uses it for drawing and,
inverted, for taps (letterbox taps are rejected). Calibration stores the image aspect
and sensor rotation; a change of either invalidates it. Metric computations correct for
the image aspect and normalize by shoulder width.

## Time and failure policy

- Freshness: a result older than max(1.5 s, 3 × median frame period) is discarded.
- Continuity: gaps longer than max(500 ms floor, 3 × median period) reset evidence to
  UNKNOWN. Median period > 200 ms → "processing is slow"; > 700 ms → "too slow", no warnings.
- No single frame can warn: SUSPECT must persist for the trigger delay (default 1 s after
  a 400 ms/3-sample motion window). Clearing needs 0.5 s below the clear threshold, then a
  1.5 s cooldown.
- Pause, backgrounding, camera loss (including "in use by another app"), stale data,
  geometry change and model/native failures all silence immediately and explain the
  recovery. Uncertainty is shown as "Not visible", never as good posture.

## Privacy by construction

No image is stored anywhere; frames live in two reused bitmaps. The merged manifest has no
`INTERNET` permission (MediaPipe's telemetry transport is therefore blocked; see
`plan.md` 0.9.26), backup is disabled, and `PrivacyGuardTest` fails if that changes.
Local data is limited to settings, explicit feedback, opt-in statistics and opt-in
training logs of landmarks (see [data.md](data.md)).

## Why these choices

See [pose framework selection](pose-frameworks.md) and `plan_v2/plan_v2.md` (twelve
decisions with alternatives). In short: native Kotlin Views (small, testable with
Robolectric), MediaPipe (multi-person, 33 landmarks, offline), rules before learning
(no real data yet), landmark logs + replay so later tuning is measured on whole sessions.
