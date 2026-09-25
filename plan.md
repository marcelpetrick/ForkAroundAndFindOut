# Implementation plan and delivery ledger

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

## Goal and interpretation

Build the Android-first, offline dining-table elbow monitor in `vision.md` using
Flutter UI, a native Kotlin CameraX/MediaPipe pipeline, persistent calibration,
independent elbow classification, conservative temporal alarms, and local diagnostic
data. There is no existing Flutter application in this repository, so create one.

Docker will deliver the Flutter web diagnostic/demo UI and downloadable Android APK,
not pretend that an Android camera application executes inside a server container.
The web UI will clearly distinguish synthetic demonstrations from Android monitoring.

## Tasks

- [x] Step 01: preserve vision, working agreement, plan, and initial version on `main`.
- [ ] Step 02: scaffold Flutter/Android project, GPL license, reusable scripts,
  initial local quality pipeline and mirrored GitHub Actions.
- [ ] Step 03: implement and test geometry, feature extraction, seat assignment,
  per-arm state machine, configuration and deterministic acceptance fixtures.
- [ ] Step 04: implement native rear-camera selection, permission/lifecycle handling,
  720p latest-frame analysis, MediaPipe multi-pose inference, preview and event bridge.
- [ ] Step 05: implement monitoring UI, calibration and seat zones, persisted settings,
  debug overlay, pause, configurable visual/audio alarms and automatic clearing.
- [ ] Step 06: implement explicit labelled local training samples, deletion/export,
  false-alarm/missed-violation feedback and optional non-image session statistics.
- [ ] Step 07: validate local Android/web builds, unit/integration/e2e tests and >=95%
  coverage; capture an actual running-UI screenshot.
- [ ] Step 08: Dockerize the working diagnostic UI/APK distribution, add Docker smoke
  checks and GHCR build/publish Actions; verify the published image.
- [ ] Step 09: complete README, script documentation, architecture, hardware
  validation procedure and full vision traceability matrix.
- [ ] Step 10: run reviewBranch, resolve findings, run githubAbout, complete all
  local/remote gates, push and verify a clean working tree.

## Vision traceability

| Vision sections | Delivery / evidence | Status |
| --- | --- | --- |
| 1–6, 16–19, 32–35 | Android Flutter/Kotlin/CameraX/MediaPipe architecture and pose feasibility | Pending |
| 7, FR-05 | Four-corner table calibration and persistence | Pending |
| 8, FR-06 | Optional seat regions and stable association despite reordered detections | Pending |
| 9–10, 28 | Table-relative arm geometry, confidence and motion classifier | Pending |
| 11–12, 26 | Independent UNKNOWN/CLEAR/SUSPECT/VIOLATION states and hysteresis | Pending |
| 13, FR-09–11 | Configurable warnings, clearing, cooldown/grace, immediate pause | Pending |
| FR-01–04, 14–15 | Local multi-person camera analysis, no recording by default | Pending |
| FR-12–13 | Debug telemetry, settings and model/camera selection | Pending |
| FR-15–16, 21–23, 27 | Explicit local training/feedback, deletion, session statistics | Pending |
| 15, 19, 29 | Performance, real-camera feasibility, household acceptance | Hardware evidence pending |
| 24–26 | CI, APK artifact, tests and deterministic synthetic fixtures | Pending |
| 10.2, 21–22, phase 6 | Session-split learned classifier after sufficient real labelled sessions | Conditional; no dataset available |
| 10.3, phase 7 | Local image classifier only if landmarks prove insufficient | Conditional future work |
| 20, phase 8 | Optional depth evaluation | Conditional future work |
| 3.2, 30–31, phase 9 | Raspberry Pi/multi-camera appliance after smartphone validation | Explicitly deferred by vision |
| 36 | Verify relevant upstream APIs and record references | In progress |

## Validation boundaries

The host has Flutter 3.47.1/Dart 3.13.1, Android SDKs, Java, Docker and authenticated
GitHub access. No Android device is attached. Tests can prove deterministic software
behavior; real meal false-alarm rate, sustained-contact sensitivity and on-device FPS
require consented physical sessions and must not be fabricated.

## Commit ledger

### 0.0.1 — docs: record vision and implementation working agreement

- Done: read all vision sections; inspect empty repository and available tooling;
  preserve original `vision.md`; establish `agents.md`, task plan, and `VERSION`.
- Validation: initial documentation integrity and whitespace checks; no application
  exists yet. The executable pipeline is the next implementation step.
- Pending: steps 02–10 and physical validation listed above.
- Decisions: use Android as the primary product; use Docker for the companion web
  diagnostic/demo UI and APK distribution. Keep conditional research visible.
- New ideas: add deterministic scenario replay to make calibration/alarms testable
  without exposing family images; document camera movement/recalibration explicitly.

## New ideas backlog

- [ ] Deterministic synthetic scenario replay shared by demo and regression tests.
- [ ] Calibration reminder after camera/model changes and orientation handling.
- [ ] Session-separated export metadata for future classifier training without leakage.
