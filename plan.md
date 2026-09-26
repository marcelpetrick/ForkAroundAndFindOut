# Implementation plan and delivery ledger

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

## Goal and interpretation

Build the Android-first, offline dining-table elbow monitor in `vision.md` using
a native Kotlin UI and CameraX/MediaPipe pipeline, persistent calibration,
independent elbow classification, conservative temporal alarms, and local diagnostic
data. The user explicitly selected Kotlin for the entire Android app; this overrides the
vision’s Flutter recommendation.

Docker will serve a downloadable Android APK with source/license links. The Android
app includes an explicitly labelled synthetic demo for reproducible UI testing.

## Owner input log

Instructions from the owner, recorded so any agent can resume faithfully.

- Mandate: implement the complete `vision.md` and keep working until the product works;
  make routine decisions independently. Work on `main`; atomic conventional commits;
  semver (patch every commit, minor for major features); every commit green before
  commit and push; push continuously.
- Quality: `localPipeline.sh` early, growing to formatting, linting, static checks,
  unit/integration/e2e tests, >=95% coverage, builds and Docker checks; GitHub
  Actions mirror it and stay green. Small documented reusable scripts.
- Delivery: local product first, then Docker; build and publish the image to GHCR via
  Actions. GPLv3-or-later with `LICENSE` and headers. README with badges modelled on
  `~/repos/Cullendula` / myLastFmPlayer, setup, testing, pipeline, Docker, usage and at
  least one real UI screenshot. Do not skip requirements.
- Finish: `/reviewBranch`, fix findings, `/githubAbout`, audit every `vision.md` item,
  full local pipeline, green Actions, working Docker image, everything pushed, clean
  tree. Then make a **public GitHub release**.
- 2026-09-25 session: Kotlin throughout (not Flutter). Another agent works in the same
  checkout and writes the plan (`plan_v2/`); this agent implements. **Commit only files
  this agent changed** (explicit paths, never `git add -A`). Document everything in
  `plan.md` — update the plan first, then continue. Check remote CI and fix failures.
  `localPipeline.sh` and README badges must follow Cullendula / myLastFmPlayer.
  Work step by step and do not stop until the whole project is done.

## Tasks

- [x] Step 01: preserve vision, working agreement, plan, and initial version on `main`.
- [x] Step 02: scaffold Kotlin Android project, GPL license, reusable scripts,
  initial local quality pipeline and mirrored GitHub Actions.
- [x] Step 03: implement and test geometry, feature extraction, seat assignment,
  per-arm state machine, configuration and deterministic acceptance fixtures.
- [x] Step 04: implement native rear-camera selection, permission/lifecycle handling,
  720p latest-frame analysis, MediaPipe multi-pose inference, preview and event bridge.
- [x] Step 05: native monitoring UI — welcome, camera positioning with
  live skeleton, four-corner table calibration (letterbox taps rejected), optional
  non-overlapping seat regions, monitor with per-seat left/right status words,
  always-visible Pause, grace period, watchdog tick that expires stale evidence,
  configurable visual (border/icon/tint/slow pulse) and audio (once/repeat/continuous,
  volume) warnings, silence on pause/background/camera loss, recalibration notice on
  geometry change, adult diagnostics (FPS, latency, scores, confidence), persisted
  settings screen for every FR-13 item, and a clearly labelled synthetic demo that
  never sounds or stores data. Robolectric flow tests plus unit tests.
- [x] Step 05b: rebuild `localPipeline.sh` in the style of `~/repos/myLastFmPlayer` and
  `~/repos/Cullendula`: `--help` usage, numbered stages, per-stage logs, optional
  `--report-dir`, `--noRun`/`--skip-e2e` style flags, and a final stage-by-stage
  PASS/FAIL summary with details (tests, coverage %, lint counts, APK sizes). CI calls
  the same script. README gets the full badge set like those repos (pipeline, Docker
  publish, latest release, license, Android, Kotlin, MediaPipe, CameraX, coverage).
- [x] Step 06: explicit training mode (NORMAL / LEFT / RIGHT / BOTH labels, per seat,
  feature vectors only, session IDs), false-alarm / missed-violation feedback,
  opt-in session statistics (duration, violations, corrections, mean confidence),
  local data screen with record count, JSON export via system file picker, delete.
- [x] Step 07: instrumented end-to-end tests on the API 34 emulator (setup, demo,
  pause, settings, data management) wired into `localPipeline.sh` and CI via an
  emulator runner; capture genuine running-UI screenshots with `adb exec-out screencap`.
- [x] Step 08: Docker image that serves the release APK plus source/license links;
  Docker smoke test in the pipeline; GitHub Actions workflow builds and publishes
  to GHCR; verify by pulling and running the published image.
- [x] Step 09: README (badges, setup, usage, testing, pipeline, Docker, screenshot),
  script docs, architecture, hardware validation checklist, full vision traceability.
- [ ] Step 10: run `/reviewBranch`, fix confirmed findings, run `/githubAbout`, audit
  every `vision.md` section, full local pipeline, green Actions, verified GHCR image,
  clean working tree.
- [ ] Step 11: public GitHub release (tag `v<VERSION>`) with the APK attached and
  honest release notes separating software evidence from pending household evidence.

### Coordination

A second agent authored `plan_v2/` (review, re-sequenced plan, UI/UX design) in
parallel. Per `plan_v2/README.md`, once published `plan_v2/plan_v2.md` decides *what to
build* and this file remains the ledger of *what was built*. Commits stage explicit
paths only, so neither agent commits the other's uncommitted files.

## Vision traceability

Audited against every section of `vision.md` (final gate). "Done" means implemented and
covered by automated tests; physical-world outcomes are listed separately and honestly.

| Vision | Delivery (where) | Evidence | Status |
| --- | --- | --- | --- |
| §1 objective, §2 approach, §34 core decision | CameraX → MediaPipe → seat tracker → table-relative features → rule classifier → temporal filter → warning (`docs/architecture.md`) | unit + Robolectric + emulator e2e | Done |
| §3 smartphone first | Native Android app (API 34+); Kotlin throughout by owner decision instead of Flutter (§16–17 intent kept: native camera/inference, only results cross into UI) | APK builds, emulator | Done |
| §3.2, §30–31, phase 9 Raspberry Pi / multi-camera | — | — | Deferred by the vision until the phone version is validated |
| §4, §18 framework and configuration | MediaPipe Pose Landmarker Full (Lite selectable), LIVE_STREAM, ≤4 poses, 0.6 gates, pinned SHA-256 models; comparison in `docs/pose-frameworks.md` | `NativeModelTest` (both models, offline) | Done |
| §5 problem formulation (away / passing / supported) | Conservative supported-elbow rule: stationary, bent, downward upper arm, near/inside table | `DetectorTest` acceptance cases | Done |
| §6 camera position | Welcome placement copy, visibility-check tips, README/hardware protocol | UI text; real placement **not yet measured** | Done (guidance); physical validation pending |
| §7, FR-05 table calibration | Four-corner marking in image space, crossing/letterbox rejection, drag adjust, persisted with aspect+rotation, invalidated on change | Robolectric + emulator real-camera flow | Done |
| §7, §8, FR-06 seat zones and assignment | Optional non-overlapping regions (all or none), tracker without identity, ambiguity rejection | `SeatTrackerTest`, flow tests | Done |
| §9 features, §10.1 rules, §28 conservative rule | Signed distances, limb lengths, angles, heights, torso/shoulder tilt, speed/variance, confidence | `GeometryTest`, `DetectorTest` | Done |
| §10.2, §21–22, phase 6 learned classifier | Landmark session logs + replay harness + session-split guidance prepared | `SessionLogTest`, `ToolsTest` | Conditional: needs real consented sessions; none exist |
| §10.3, phase 7 image classifier | — | — | Conditional future work (only if landmarks insufficient) |
| §11–12, §26, FR-07/08 states and temporal filter | UNKNOWN/CLEAR/SUSPECT/VIOLATION per elbow, dwell, clear delay, cooldown, gap reset, no single-frame alarm | `TemporalFilterTest`, `MonitorTest` | Done |
| §13, FR-09/10/11 alarms | Border/icon/tint/slow pulse (no flashing), reminder card by seat colour, chime once/repeat/continuous with volume, auto clear, grace, pause always visible, false-alarm rest | Robolectric, `UiTest` | Done |
| §14 FR-01/02/03/04 | Rear camera selection (lens labels), on-device only (no INTERNET permission), 1–4 people, arm landmarks with confidence | `PrivacyGuardTest`, flow tests | Done |
| FR-12 debug overlay | Skeleton, confidence, table, seats, state, score, FPS, latency p50/p95, dropped, thermal, battery | flow tests, e2e readout | Done |
| FR-13 configuration | Every listed setting persisted (versioned, migrated) | `StorageTest`, `UiTest` | Done |
| FR-14 no recording, §23 privacy | Frames only in reused memory; no image storage; backup off; telemetry uploader blocked | `PrivacyGuardTest`, emulator job test | Done |
| FR-15, §21, §27 training and feedback | Explicit training mode → landmark logs with labels; False alarm / Missed violation | flow tests | Done |
| FR-16 statistics | Opt-in session statistics (duration, violations, corrections, confidence) | flow tests | Done |
| §15 non-functional (FPS, latency, backlog, background thread, failure policy) | 640×360 analysis, one frame in flight, latest-only, measured budgets, uncertainty = no alarm | emulator 27.9 FPS (not phone); CI emulator 5 FPS → "slow" state | Done in software; phone numbers **pending** |
| §15 false alarms per meal, §29 household acceptance | Protocol and result table in `docs/hardware-validation.md`; synthetic acceptance cases automated | synthetic only | **Pending real meals — cannot be fabricated** |
| §19 partial-body visibility | In-app 10 s visibility check gating table marking | Robolectric + emulator | Done (software); real placement pending |
| §20, phase 8 depth | — | — | Conditional future work |
| §24 CI/CD | `localPipeline.sh` = CI: lint, tests, coverage ≥95 % (98 %), APKs, emulator e2e, Docker; artifacts; GHCR; releases | GitHub Actions green | Done |
| §25 project structure | `:detection`, `:core`, `:tools`, `:app` (Kotlin equivalent of the proposed layout) | builds | Done |
| §32 phases 1–5 | 1 feasibility (camera+pose+overlay), 2 UI integration (native), 3 calibration, 4 rules, 5 data collection tooling | tests | Done in software; phase 4/5 household testing pending |
| §33 stack, §35 recommendation | Implemented except Flutter (owner chose Kotlin) and Heavy model (not bundled) | — | Done with documented deviations |
| §36 references | Upstream APIs used as documented; links in docs | link check | Done |

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

### 0.0.2 — docs: select native Kotlin and MediaPipe after framework comparison

- Done: researched MediaPipe, ML Kit, MoveNet MultiPose, RTMPose, YOLO Pose and
  OpenPose using upstream documentation; selected MediaPipe Full with Lite fallback.
- Done: record user decision for Kotlin throughout; discarded uncommitted Flutter
  scaffold. See `docs/pose-frameworks.md` for selection and integration strategy.
- Validation: documentation whitespace, source links and version check; no product
  code in this atomic decision commit.
- Pending: steps 02–10. Capture a real Android UI screenshot for README.
- New ideas: reject ambiguous seat assignments, prefer UNKNOWN on hidden landmarks,
  invalidate calibration when camera geometry changes, benchmark Full vs Lite on phone.

### 0.0.3 — chore: establish native Android build and quality gates

- Done: Kotlin-only Android foundation, min API 34 (Android 14), compile/target 37;
  GPL license, pinned Gradle/dependencies, native Activity test and Kover 95% gate;
  shared local/CI pipeline and documented scripts.
- Validation: full `localPipeline.sh` passed; all Kotlin lines covered (100%),
  Robolectric Android 14 launch test passed, Android lint/format passed, debug and
  release APK builds passed. Explicit backup exclusions and launcher icon added.
- Environment evidence: emulator sees Integrated Webcam HD as `webcam0`; KVM works;
  API 34 system image already installed. Webcam use and real UI screenshot planned.
- Pending: steps 03–10. Step 02 complete.
- Handoff: continue with geometry/temporal tests, then native camera and setup UI.
- New ideas: support a labelled synthetic demo within the same native UI for repeatable
  e2e tests without storing or committing webcam/family footage.

### 0.0.4 — fix(ci): avoid removed Android SDK tools package

- Done: fix the SDK setup action's default attempt to install the obsolete `tools`
  package; request `platform-tools` explicitly and suppress license-text log noise.
- Evidence: first remote run failed during SDK setup before executing application
  gates. The local native foundation was green. Re-run full local gate before push.
- Pending: verify new remote run; detector feature is independently in progress and
  will be committed separately after its regression suite and pipeline pass.
- Handoff: use `gh run list` and `gh run view <id> --log-failed` for remote evidence.

### 0.1.5 — feat: add conservative per-elbow detection and regression tests

- Done: aspect-corrected calibration geometry, visible-joint model, stable one-to-one
  seat matching, ambiguity rejection, motion/geometry features, conservative contact
  rules, independent elbow hysteresis/clear/cooldown and stale-evidence rejection.
- Validation: 13 deterministic unit/Robolectric tests; all 212 executable Kotlin lines
  covered (100%). Full local formatting, lint, tests, coverage and APK builds pass.
- Pending: persistent configuration from step 03 moves with step 05 UI/storage;
  camera and controller must expire stale callbacks even when no new frame arrives.
- Handoff: `docs/detection.md` defines input timestamps/coordinates and failure policy.
  Next implement persisted settings/data and a monitoring controller, then camera/UI.
- New ideas: expose all computed feature values in labelled session exports; never
  present heuristic scores as calibrated probabilities or measured meal accuracy.

### 0.2.6 — feat: persist settings and add fail-safe monitoring sessions

- Done: complete versioned settings serialization (calibration, seats, camera/model,
  people, thresholds, delays, visual/audio modes, volume, grace, diagnostics/statistics).
- Done: monitoring session controller, grace period, frame freshness watchdog,
  geometry-change invalidation, per-arm violation counts/confidence; independent
  once/repeat/continuous audio policy that stops on pause or missing evidence.
- Done: private atomic feature storage, bounded capacity, explicit sample/feedback
  records with session IDs, raw export, deletion and corrupt-file recovery notice.
  No image persistence. UI opt-in/controls remain pending.
- Validation: 18 unit/Robolectric tests pass, including persistence, malformed data,
  stale callbacks, calibration, alarms and exports. Full local pipeline passed;
  Kotlin coverage 416/417 lines.
- Pending: camera/UI integration, e2e, screenshot, Docker and final gates.
- Handoff: `Monitor` must be ticked even without callbacks; `LocalStore.add` is called
  only for explicit training/feedback or opted-in statistics. Keep demo data separate.

### 0.3.7 — feat: add native CameraX and MediaPipe pose pipeline

- Done: rear-camera selection by Camera2 ID, 720p preview plus RGBA latest-frame
  analysis, busy-drop analyzer, rotation handling, CameraX coordinate transform into
  preview space, MediaPipe LIVE_STREAM engine (Full/Lite, up to 4 poses, 0.6 gates),
  image ownership until async completion, late-callback suppression after close.
- Done: `scripts/models.py` downloads pinned model version 1 and verifies SHA-256;
  models are not committed. Lint opt-ins added for experimental CameraX transforms.
- Validation: full local pipeline passed, 552/560 Kotlin lines covered (98.6%).
  Instrumented `NativeModelTest` ran both bundled models through the real native
  runtime on the API 34 x86_64 emulator; a blank frame yields zero poses.
- Pending: UI integration (step 05), e2e in the pipeline, physical camera evidence.
- Handoff: `CameraSession` delivers poses already mapped to preview-normalized
  coordinates, so calibration taps and detection share one coordinate system.

### 0.3.8 — docs: record remaining delivery plan and parallel-agent coordination

- Done: re-audited repository state after 0.3.7. Steps 01–04 complete; remote Actions
  green for 0.3.7 (earlier red runs were the superseded SDK-setup failure and a
  cancelled run). Expanded steps 05–10 with concrete scope; added step 11, a public
  GitHub release; recorded coordination with the parallel `plan_v2/` work.
- Step 05 status: overlay, settings model, speaker, synthetic demo and activity flow
  are implemented locally but not committed; one Robolectric permission-flow test is
  still failing, so the feature is held back until the whole pipeline is green.
- Validation: documentation-only change (plan, version files); product code is
  identical to 0.3.7, which passed the full local pipeline, the instrumented
  emulator test and remote Actions. Pipeline re-run on a clean worktree of this commit.
- Pending: steps 05–11 and physical-camera/household evidence.
- New ideas: demo scenario doubles as README screenshot source (labelled synthetic);
  seat overlap uses a separating-axis test so diagonal regions are not falsely rejected.

### 0.4.9 — feat: add native monitoring, calibration, settings and synthetic demo UI

- Done: single-activity Kotlin UI (warm ivory/deep green, large text, status words):
  welcome with privacy/placement copy; camera positioning with live skeleton and
  people count; four-corner table marking with numbered taps, undo/reset, crossing
  rejection and letterbox-tap rejection (`CameraSession.bounds`); optional seats with
  separating-axis overlap rejection; monitor with per-seat left/right words ("Not
  visible" for UNKNOWN), always-visible Pause/Resume, grace countdown, 100 ms watchdog
  tick, border/icon/tint/0.5 Hz pulse warnings, once/repeat/continuous tones with
  volume; silence and camera release on pause, background, camera loss or stale data;
  recalibration notice when geometry changes; adult diagnostics (FPS, latency, model,
  violations, confidence, per-arm score/distance/angle); persisted settings for every
  FR-13 item (camera change clears calibration); synthetic two-seat demo that previews
  the visual warning but never sounds or stores data.
- Validation: full local pipeline green; 1198/1220 Kotlin lines covered (98.2%).
  Robolectric flow tests cover setup, calibration, seats, monitoring, alarm, pause,
  stale data, backgrounding, geometry change, camera error, permission denial/grant.
  Debug APK installed on the API 34 emulator: welcome and demo render; the demo shows
  seat 2's resting elbow in red with the border warning (screenshots captured).
- Pending: training/feedback/statistics/data screen (06), pipeline restyle and badges
  (05b), e2e in pipeline, Docker/GHCR, docs, review, release.
- Decision: the demo shows the configured *visual* warning so adults can preview it,
  but no sound or storage; `docs/ux.md` wording updated accordingly.

### 0.4.10 — docs: record collaboration rules and working order in agents.md

- Done: `agents.md` gains the owner's collaboration rules: explicit-path commits with
  a parallel agent, read-only `plan_v2/` checked at the end of each task batch, plan
  first, step-by-step until done, self-review only after implementation work runs out,
  reference-style pipeline/badges, public release at the end.
- Validation: documentation-only; product code identical to 0.4.9 (pipeline green).
- Next: step 05b (pipeline restyle and badges), then step 06.

### 0.4.11 — build: restyle pipeline with staged summary, emulator e2e and badges

- Done: `localPipeline.sh` rebuilt after myLastFmPlayer/Cullendula: `--help`, numbered
  stages, per-stage logs, `--report-dir`, `--noRun`, `--noOpen`, `--e2e`/`--docker`
  modes, environment metadata and a final PASS/FAIL/WARN/SKIP summary with details.
  New `scripts/report.py` (test/coverage summaries) and `scripts/emulator.sh`
  (headless AVD boot). CI runs the same script inside an API 34 KVM emulator with
  `--e2e required` and uploads stage logs. README badge set extended (pipeline,
  license, Android, Kotlin, CameraX, MediaPipe, coverage, gate, offline).
- Validation: local run — all mandatory stages PASS, E2E PASS on the API 34
  emulator (`NativeModelTest`), 29 unit tests, 98.2% line coverage; ShellCheck clean.
- Pending: Docker/release badges arrive with their workflows (steps 08, 11).

### 0.5.12 — feat: add explicit training, feedback, session statistics and data management

- Done: adult tools inside collapsed monitor diagnostics (reset every session):
  False alarm / Missed violation store feature vectors of all seats with session ID;
  explicit training mode (off by default, never persisted) with seat selector and
  NORMAL / LEFT / RIGHT / BOTH labels capturing 5 s of per-frame feature vectors for
  one seat, written as one atomic batch (turning training off discards a capture);
  opt-in session statistics on stop (duration, violations, false alarms, missed
  violations, mean joint confidence). Local data screen: record count, corruption
  notice, JSON export through the system file picker, confirmed delete-all.
  `LocalStore.addAll` is all-or-nothing within the 5000-record cap; records carry
  `type` and per-arm `seat`; `imageRecorded` is always false.
- Validation: full local pipeline green: 31 tests, 98.4% line coverage (1314/1336),
  E2E PASS on the API 34 emulator, lint 0 issues.
- Pending: e2e UI flows on device (07), Docker/GHCR (08), docs (09), review (10),
  release (11). Remote run for 0.4.11 (first emulator CI) was still running at commit.
- New ideas: export filename could carry the date; a training-session summary per
  label would help check class balance before any learned classifier.

### 0.5.13 — docs: add plan v2 review, decisions and UI/UX designs

- Done: `plan_v2/` (planning agent's workspace, read-only for the implementing agent):
  `plan_v2.md` reviews every aspect of the project at `c32d9a0` with an addendum for
  `49e660c`/`abcfbc9` — twelve decisions (image-space coordinates, frame-path performance,
  decoupled latency/gap budgets, session landmark logs + replay harness, in-app
  visibility check, plain Views, JVM module split, ABI split, CI signing, minimal
  Docker/GHCR, emulator e2e), fifteen ranked findings with `file:line`, milestones
  M1–M9 with evidence requirements, vision traceability v2, a hardware validation
  protocol and open owner questions. `design/ui-ux.md` plus eleven SVG mockups
  define tokens, flow, screens, copy (en/de) and accessibility.
- Validation: documentation only; product code identical to 0.5.12. SVGs validated
  with `xmllint` and rendered with `rsvg-convert`; line references re-checked with
  `git show c32d9a0:<file>`; MediaPipe API claims verified with `javap` on the 1.0.0
  AARs; upstream versions checked on Google Maven / Maven Central.
- Next for the implementing agent: read `plan_v2/plan_v2.md` §2.3 and §5; M1 (F1, F2,
  F5, F7, F8, F10) before any real-table calibration is stored.

### 0.5.14 — test: add UiAutomator end-to-end flows and screenshot capture

- Done: `UiFlowTest` drives the installed APK with real touches: synthetic demo shows
  the warning and status words; settings persist across an activity restart; with a
  rear camera, setup → position (live people count) → four real screen taps on the
  preview → save table → seats → start monitoring → grace → pause/resume → stop →
  local data → delete dialog cancel (without a rear camera it asserts the recovery
  message instead). `scripts/screenshots.sh` installs the APK and captures genuine
  welcome, synthetic-demo-warning and settings screenshots into `docs/screenshots/`.
  CI emulator gets an emulated rear camera; runs are no longer cancelled by newer
  pushes, so every commit keeps a visible remote result.
- Validation: `connectedDebugAndroidTest` on the API 34 emulator — 4 tests, 0 failed
  (NativeModelTest + 3 UiFlowTest flows using the emulator's emulated rear camera).
  Full local pipeline green.
- Plan of record: `plan_v2/plan_v2.md` (committed by the planning agent in 0.5.13)
  now decides what to build. Next work follows its milestones: M1 foundations
  (image-space coordinates, freshness vs gap budgets, Throwable handling, orientation/
  insets, persisted maxGapMs), then M2 frame path + ABI split, M3/M4 residuals,
  M5 landmark session logs + replay harness + module split, M6 guards, M7 docs,
  M8 signing + Docker/GHCR + release, M9 gate. Physical-phone evidence (§7) cannot be
  produced by this agent and stays recorded as pending, never fabricated.

### 0.6.15 — fix: move geometry to image space and separate latency budgets (plan_v2 M1)

- Done (plan_v2 F2): landmarks, table, seats and taps share one canonical space —
  normalized upright analysis-image coordinates. `CameraSession` no longer remaps
  landmarks into the view; it exposes an image→view `mapping` matrix used by the
  overlay and, inverted, by calibration taps (letterbox taps rejected in image space).
  Calibration stores the image aspect and sensor rotation; a change of either
  invalidates it. Pre-0.6.15 view-space calibrations are dropped on load (migration).
  Taps and saving are refused until the first frame fixes the geometry (found by the
  real-camera emulator e2e test: taps before the first frame were stored in view space).
- Done (F1): freshness bound max(1.5 s, 3× median period) and continuity bound
  max(floor, 3× median period) replace the single 500 ms constant; `Monitor.health`
  MEASURING/OK/SLOW/TOO_SLOW with UI messages; TOO_SLOW never warns. `maxGapMs`
  persisted (F7). Gap budget threaded through classifier, filter and detector.
- Done (F5, F8, F10): `Throwable` handling (LinkageError/OOM become the recovery
  message, programming errors still crash); camera-in-use / camera errors observed via
  `CameraInfo.cameraState`; `completed()` runs on the MediaPipe callback thread;
  orientation locked on camera screens; system-bar/cutout insets applied to every
  screen; `enableOnBackInvokedCallback` for predictive back.
- Validation: 36 unit tests (98.3% lines) incl. slow-phone (2.5 FPS, 600 ms latency → still warns),
  too-slow (1.25 FPS → never warns), rotation invalidation, migration, insets on API
  34/35; 4 instrumented tests PASS on the API 34 emulator including the real-camera
  calibration flow. Full local pipeline green.
- Not done from M1: `MonitorPresenter`/`UiState` extraction (Activity still owns screen
  state — tracked for the module-split work in M5); Robolectric API 36/37 (this
  Robolectric/JDK cannot run API 36; needs an API 36+ emulator image, planned for M6);
  NativeModelTest rotation assertion needs a consented or synthetic *person* image —
  none is committed, so it is recorded as pending rather than faked.

### 0.6.16 — fix(ci): make the remote emulator pipeline pass

- Evidence: runs for 0.5.13/0.5.14 failed remotely. Causes: (1) the runner's older
  ShellCheck reports indirectly invoked stage functions as SC2317 (local 0.11 uses
  SC2329); (2) the default CI emulator skin is 320×640 mdpi, so buttons were below the
  fold and UiAutomator never scrolled.
- Done: disable SC2317 alongside SC2329; CI emulator uses the `pixel_6` profile (a
  realistic Android 14 phone); UiFlowTest scrolls targets into view with swipes kept in
  the middle of the panel (edge swipes triggered the system home gesture/notification
  shade when reproduced locally at 320×640); pipeline stage details are printed only
  for successful Gradle stages so a failure never shows stale counts.
- Validation: local pipeline green incl. 4 instrumented tests on the API 34 emulator;
  the 320×640 reproduction confirmed the root cause (not kept as a target size).

### 0.7.17 — feat: sign the arm64 release and distribute it via Docker on GHCR (plan_v2 M8, decisions 9–11)

- Done: release APK is arm64-v8a only (plan_v2 decision 9; debug keeps x86_64 for the
  emulator): 74.2 MB → 38.7 MB. Release signing from a properties file
  (`$FORK_SIGNING` or `~/.android/…release.properties`); a dedicated RSA-4096 key was
  generated outside the repository (0600) and uploaded as `FORK_KEYSTORE_BASE64` /
  `FORK_KEYSTORE_PASSWORD` secrets; fingerprint published in `docs/docker.md`.
  **The owner must back up `~/.android/fork-around-and-find-out-release.{jks,properties}`**:
  future updates need the same key.
- Done: `Dockerfile` on digest-pinned `nginx:1.30.5-alpine` (non-default port 8080,
  healthcheck, APK MIME type, no server tokens, CSP) serving the APK, SHA-256, license and
  an install page (`docker/`). `scripts/docker-dist.sh` stages the context;
  `scripts/docker-smoke.sh` builds, runs and verifies it; pipeline Docker stage (CI:
  `--docker required`). Workflow: model cache (F14), signing from secrets, GHCR push
  (`<version>`, `sha-<commit>`, `latest`) after a green pipeline, GitHub release on
  `v*` tags with APK + checksum. Lint `ChromeOsAbiSupport` disabled (arm64-only by design).
- Validation: full local pipeline green with `--docker required`: 36 unit tests, 98.3%
  lines, 4 instrumented tests on the API 34 emulator, Docker stage PASS; `apksigner`
  confirms the release certificate SHA-256 `aaf85804…5ae0`.
- Pending: verify the published GHCR image by pulling it (after the remote run).

### 0.7.18 — perf: allocation-free frame path and processing diagnostics (plan_v2 M2, F4)

- Done: separate 640×360 analysis stream (preview stays 720p); RGBA plane copied into a
  reused bitmap (row padding removed) and rotated with a SRC-mode canvas into a reused
  upright bitmap — no per-frame allocation or `toBitmap()`. The engine no longer closes
  the `MPImage` (closing recycled the reused bitmap; caught by the real-camera e2e test
  as "Canvas: trying to use a recycled bitmap"). Dropped-frame counter; diagnostics now
  show FPS, latency p50/p95, dropped frames, model, thermal status and battery.
- Decision: rotation stays in software instead of `ImageProcessingOptions` because the
  output-coordinate convention of MediaPipe's rotation option can only be verified with a
  real person image, which this project does not commit; own rotation is correct by
  construction and pixel-exactly tested (Robolectric native graphics, all four angles,
  padded rows, magenta marker invariant to host BGRA order).
- Evidence (emulator, x86_64 host CPU — **not phone evidence**): Full model 27.9 FPS,
  latency p50 23 ms / p95 35 ms, thermal none (ForkPerformance log from UiFlowTest).
  Release APK 38.7 MB (arm64-only, from 0.7.17).
- Validation: unit tests incl. rotation/stride test and engine no-recycle assertion; 4
  instrumented tests PASS (camera flow asserts the diagnostics readout format); full
  pipeline green. Remote Actions: 0.6.16 green (8m39s) with required emulator e2e.
- Pending (needs a phone): first phone run of Position/diagnostics per plan_v2 §7 step 1.

### 0.7.19 — test(e2e): survive CI system dialogs and keep failure screenshots

- Evidence: remote run for 0.7.17 failed only in E2E: all three UI flows could not find
  welcome buttons, while the identical tests passed remotely at 0.6.16 and locally. The
  app code was unchanged between those runs, so an emulator system dialog is the likely
  cause; there was no screenshot to prove it.
- Done: UiFlowTest dismisses "isn't responding" system dialogs (Wait) before launching
  and before every tap; a JUnit rule screenshots every failing test into
  `/data/local/tmp/fork-e2e-<test>.png`; the pipeline E2E stage clears them before the
  run and pulls them into the report directory (uploaded by CI).
- Validation: full local pipeline green (E2E 4/4, Docker PASS). GHCR publish for 0.7.17
  was skipped because that run failed; the next green run publishes the image.

### 0.7.20 — refactor: split pure detection and core logic into JVM modules (plan_v2 decision 8)

- Done: `:detection` (geometry, features, rules, seat tracker, temporal filter; shared
  synthetic fixtures as `testFixtures`) and `:core` (settings model, monitor, alarm policy,
  synthetic demo) are plain Kotlin/JVM modules with no Android imports; `:app` keeps
  camera, engine, views, store and the org.json settings codec (`SettingsCodec.kt`,
  `Settings.Companion.decode` extension keeps call sites unchanged). Kover merges all
  modules into a custom `all` variant; the 95 % gate applies to the merged report.
  Spotless covers every module; the pipeline runs `:detection:test :core:test
  :app:testDebugUnitTest`; `scripts/report.py tests` accepts several result directories.
- Validation: full local pipeline green: 38 tests, merged line coverage 98.3 % (1436/1461;
  detection 228/228, demo 23/23), E2E 4/4 on the emulator, Docker PASS.
- Next (M5): session landmark logs + replay tool (`:tools`) on top of `:core`.

### 0.7.21 — fix(ci): hide emulator system error dialogs during e2e

- Evidence: the failure screenshots now uploaded by CI (0.7.19 run) show
  "Pixel Launcher isn't responding" over the screen in all three failing UI flows —
  the CI emulator's own launcher, not this app.
- Done: the emulator step sets `hide_error_dialogs=1` before running the pipeline; the
  in-test dismissal of "isn't responding" dialogs stays as a second defence.
- Validation: workflow change only; product code identical to 0.7.20 (local pipeline
  green). Remote verification is the next run.

### 0.8.22 — feat: record training sessions as landmark logs and replay them on the desktop (plan_v2 M5, F3)

- Done: training mode (adult diagnostics, off by default, per session) now writes a
  gzip JSON-Lines session log to `files/sessions/` — header with calibration, geometry,
  model and timing; every accepted frame with 33 landmarks per pose and the live per-arm
  states; label events (NORMAL/LEFT/RIGHT/BOTH, and FALSE_ALARM/MISSED_VIOLATION feedback
  while a log is open). Sync-flushed every 30 frames and on labels; 100 MB cap. The
  5-second feature snapshots are replaced by these logs (plan_v2 finding F3).
- Done: `:core` gains a dependency-free JSON reader, `SessionLog` (writer/reader, tolerant
  of a truncated last line), `Replay` (reminders, reminders near negative labels,
  detected positive labels, UNKNOWN fraction, live/replay agreement after warm-up) and a
  labelled synthetic session generator. New `:tools` JVM module and `scripts/replay.sh`
  (`replay` with timing overrides, `demo-log`); truncated gzip (killed app) is readable.
  Local data screen lists logs with size, exports a log (gzip via file picker), deletes
  one log with confirmation; delete-all also removes logs. `docs/data.md` documents
  stores, schema, privacy and session-split evaluation.
- Validation: 45 tests, merged coverage 98.1 % (1797/1832); replay of the synthetic
  session: 2 reminders, 2/2 labels, 100 % agreement; activity test replays a log recorded
  through the UI with 100 % agreement; E2E 4/4 on the emulator; Docker PASS.
- Remote: 0.7.20 run green (9m18s) → first GHCR publish expected from it.

### 0.9.23 — feat: visibility check, false-alarm snooze, lens labels and corner dragging (plan_v2 M3/M4 residuals)

- Done: Position screen runs the vision §19 check as a product step — over 10 s it shows
  people detected vs. configured, the share of frames with every shoulder/elbow/wrist
  visible, FPS and seconds checked; "Mark table" is enabled only when detected == people
  and ≥80 % of frames show all arms, otherwise placement tips are shown. Decision: a
  secondary "Mark table without the check" remains, because a table is often set up
  while nobody is seated; it is visually secondary and documented.
- Done: "False alarm" silences immediately and rests reminders for 30 s (status shows the
  countdown; an explicit Resume ends the rest). Settings list rear lenses as Wide/Main/
  Tele (by focal length) with camera id. Table/seat corners can be dragged after placing
  (28 dp grab radius); a drag never adds a corner.
- Validation: 49 tests, 98.2 % merged lines; Robolectric tests for the passing and
  failing visibility gate, dragging, snooze and lens labels; E2E 4/4 on the emulator
  (uses the secondary button: the emulator scene has no people); Docker PASS.
- Not done from plan_v2 M3: loupe while tapping and auto-proposed seat zones (optional
  polish; seats remain manual, overlap-checked).

### 0.9.24 — feat: gentle generated chime via SoundPool with a settings preview (plan_v2 §3.6)

- Done: the dial-tone `ToneGenerator` is replaced by a two-note chime generated
  deterministically by `scripts/chime.py` (committed WAV, 48 KB; the pipeline verifies it
  still matches the generator). `ChimeSpeaker` plays it through `SoundPool` with
  `USAGE_NOTIFICATION_EVENT`/`CONTENT_TYPE_SONIFICATION` (respects Do-Not-Disturb and the
  notification volume); once/repeat as single chimes, continuous as a loop; app volume
  0–100 %. Loading is started when monitoring or settings open; a reminder requested
  before loading completes plays from the load listener, a failed load stays silent.
  Settings gains "Test sound".
- Validation: 49 tests, 98.2 % merged lines; E2E 4/4; Docker PASS. On the emulator the
  audio service shows the app's SoundPool player with notification-event attributes and
  no errors (audible quality needs a phone and a listener).

### 0.9.25 — feat: German translation, About screen and third-party notices (plan_v2 §3.8.5, §3.14.3)

- Done: complete German UI (`values-de`, 138 strings; technical format strings marked
  non-translatable); chime wording ("Chime once", "Continuous chime"). About screen with
  version/build, GPL statement, privacy summary, third-party components and source link;
  `NOTICES.md` (MediaPipe Tasks Vision 1.0.0 and the Pose Landmarker Full/Lite models with
  URLs and SHA-256, model card, CameraX, AndroidX, Kotlin; all Apache-2.0) is also served
  by the Docker image as `NOTICES.txt` (smoke-tested).
- CI: concurrency per commit SHA, so no commit's run is replaced by a newer queued run
  (0.9.23's run showed "cancelled" that way); `latest` is only pushed when the commit is
  still the tip of main, so parallel runs cannot publish an older image as latest.
- Validation: 50 tests incl. a Robolectric German-locale flow; 98.2 % merged lines; E2E
  4/4; Docker PASS. Remote: 0.7.21 and 0.8.22 green.

### 0.9.26 — fix(privacy): block MediaPipe's telemetry upload and guard the permission set (plan_v2 M6, F6)

- **Finding:** a new manifest guard test showed the merged manifest requested
  `INTERNET` and `ACCESS_NETWORK_STATE`, added by `com.google.android.datatransport`
  (transport-backend-cct), a dependency of MediaPipe Tasks. On the emulator the library
  stores `COREML_ON_DEVICE_SOLUTIONS` usage events and schedules uploads to Google's
  logging endpoint (`firebaselogging.googleapis.com`). No images or landmarks are part of
  it, but it contradicts "nothing is uploaded". **Builds up to 0.9.25 (including the GHCR
  images published so far) could send this library usage telemetry.**
- Done: `INTERNET` removed from the merged manifest (`tools:node="remove"`).
  `ACCESS_NETWORK_STATE` stays: read-only, and Android 14 throws a SecurityException for
  network-constrained jobs without it. Evidence on the emulator: the forced upload job fails
  with `EPERM` inside the library's executor, the app keeps running, crash buffer empty.
  `PrivacyGuardTest` fails if any permission beyond camera, network-state and AndroidX's
  receiver signature permission appears, if INTERNET returns, or if backup is enabled.
- Done (F6): the engine test no longer reflects into MediaPipe's options class; the
  engine's result/error listeners delegate to internal `handle`/`fail` used by the test.
- Validation: 51 tests, 98.2 % merged lines; E2E 4/4; Docker PASS; `aapt dump
  permissions` of the debug APK lists CAMERA, ACCESS_NETWORK_STATE and the receiver
  permission only.

### 0.9.27 — test(e2e): read the diagnostics readout by its unique text

- Evidence: the remote run for 0.9.24 failed in UiFlowTest only: the CI emulator ran at
  5 FPS, the app correctly showed "Processing is slow (5 FPS)…", and the test picked that
  status line (first text containing "FPS") instead of the diagnostics readout.
- Done: the test locates the readout by "latency p50". Product behaviour unchanged and
  correct (slow-processing message on a slow device).
- Validation: 4 instrumented tests PASS on the local emulator.

### 0.9.28 — docs: complete README, architecture and hardware-validation protocol (plan_v2 M7)

- Done: README rewritten per plan_v2 §3.14.1: badge set in the myLastFmPlayer/
  Cullendula style (pipeline, latest release, license, Android, Kotlin, CameraX,
  MediaPipe, coverage, gate, GHCR, no-network, languages), honest status table, four
  genuine emulator screenshots (welcome, synthetic demo warning, setup visibility check on
  the emulator camera, settings), how it works, placement, usage, settings, privacy
  (including the pre-0.9.26 telemetry disclosure), training/replay, setup, testing levels,
  pipeline/CI, Docker/GHCR, roadmap, license/notices. New `docs/architecture.md` and
  `docs/hardware-validation.md` (plan_v2 §7 protocol, emulator numbers labelled as such,
  real-phone rows "not yet measured"). `scripts/screenshots.sh` grants the camera after
  `pm clear` and adds the setup screen. `scripts/check_links.py` (pipeline) verifies all
  relative Markdown links and anchors.
- Decisions on plan_v2 §9 open questions (owner did not answer; defaults taken):
  German added; default people stays 4 (vision FR-03 1–4, one tap to reduce); orientation
  follows how the phone is propped (both layouts supported; locked while calibrated);
  no Heavy model download; new project-specific release key; registry name as proposed.
- Validation: full pipeline green (51 tests, 98.2 %, E2E 4/4, Docker PASS, 16 Markdown
  files with valid links).

### 0.9.29 — fix: address self-review findings (replay fidelity, log robustness, partial seats)

Self-review of everything committed in this session (`574f87b..0a19011`, 82 files).
`/reviewBranch` itself resolves base = head on `main` (all work is on main), so its diff
is empty; the same method was applied to the session range instead. Findings fixed:

1. MEDIUM — `Replay` used a bare `Detector` with the fixed 500 ms gap while the phone's
   `Monitor` widens budgets from the measured frame period and rebuilds after stale gaps,
   so slow-phone sessions (the ones tuning matters for) replayed differently. Replay now
   runs the same `Monitor` (tick before each frame); `Monitor.frame` reports acceptance
   and only accepted frames are logged. Test: a 2.5 FPS recording replays with 100 %
   agreement and identical reminder count, including a long-gap expiry.
2. MEDIUM — non-finite numbers were written as `NaN` (invalid JSON; a corrupt middle line
   makes the whole log unreadable) and non-object lines crashed the reader with a raw
   exception. Non-finite landmarks are stored as unseen (0,0,0); malformed lines raise a
   message with the line number (the tool prints usage instead of a stack trace).
3. MEDIUM — seat regions for only some people left the others permanently unmonitored
   (the tracker assigns only inside regions). Finish setup now requires a region for every
   person or none (automatic assignment), with an explanation (en/de).
4. LOW — `CameraSession.result` freed the analyzer before taking the frame geometry, so the
   next frame could overwrite it; the snapshot is now taken first.

- Validation: 52 tests, 98.2 % merged lines, E2E 4/4, Docker PASS.
- Reviewed and kept (documented, not defects): recorder writes on the main thread (a few
  KB/s gzip), activity recreation on system config changes during a meal ends the session
  safely (silence, statistics saved), MediaPipe result-never-arrives is not observed with
  one frame in flight.

### 0.10.30 — feat: apply the plan_v2 design system: dim-room theme, seat cards, reminder card

- Done (plan_v2/design/ui-ux.md): colour tokens as resources with a system-following dark
  "dim room" variant; seat identity colours; per-seat cards with words-and-colour state chips;
  session line; Pause directly under the status (the camera e2e test found it pushed below
  the fold by four seat cards); centred reminder card "Elbows off the table, please · Blue seat
  · left" and a 2 s "Thank you!" after a real correction; perimeter fades in 400 ms / out
  600 ms, pulse 0.6↔1.0 over 2.4 s; paused preview dimmed; Back asks "Stop monitoring?";
  Welcome shows a Ready line and "Start dinner"; every setting has a one-line explanation
  (en/de); status "Watching the table." instead of repeating the title. Configuration
  changes (dark mode, font scale, locale, rotation outside camera screens) re-render in place
  instead of recreating the activity, so a meal session survives a dark-mode switch at dusk.
- Deviation: light-mode amber/grey chip text darkened (#8A5A1D, #5F6662) — the sheet's
  #C9822B/#8A918D on their soft backgrounds fail its own 4.5:1 contrast rule.
- Found and fixed while validating: seat cards were first rebuilt every tick (the e2e suite
  slowed from 4 to 21 minutes because the UI never idled); cards now re-render only when a
  state changes (a Kotlin precedence slip in the first cache key was caught by the tests),
  and per-tick texts update only on change. UiFlowTest takes failure screenshots before the
  scenario closes; assertions use stable facts instead of the 3 s grace text.
- Vision traceability table rewritten as the final-gate audit (see above).
- Validation: 53 tests, 98.3 % merged lines (2097/2133), E2E 4/4 (4 min), Docker PASS,
  refreshed genuine screenshots.
- Not done (optional polish from plan_v2): loupe, auto-proposed seat zones, cross-fade
  between screens, presenter extraction.

### 0.10.31 — fix: keep Stop and diagnostics next to Pause on the monitor

- Evidence: remote run for 0.10.30 failed in UiFlowTest only: on the slower CI emulator the
  diagnostics toggle sat below four seat cards and its live text shifted the layout while
  the test scrolled (failure screenshot in the run artifacts). plan_v2's design puts the
  adult toggle next to Pause.
- Done: monitor order is now status → Pause → [Stop · Adult diagnostics] → adult panel
  (collapsed) → seat cards → session line.
- Validation: full local pipeline green; instrumented suite passed twice in a row.
