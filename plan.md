<!-- SPDX-FileCopyrightText: 2026 Marcel Petrick -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Implementation plan and delivery ledger

## Goal and interpretation

Build the Android-first, offline dining-table elbow monitor in `vision.md` using
a native Kotlin UI and CameraX/MediaPipe pipeline, persistent calibration,
independent elbow classification, conservative temporal alarms, and local diagnostic
data. The user explicitly selected Kotlin for the entire Android app; this overrides the
vision’s Flutter recommendation.

Docker will serve a downloadable Android APK with source/license links. The Android
app includes an explicitly labelled synthetic demo for reproducible UI testing.

## Plan v3 — completing plan_v2 (owner request 2026-09-26)

Owner input: "Don't do the Raspberry Pi — mark it as never. Check plan_v2, incorporate it
into what we have with a plan, overhaul; make all easy decisions as an expert designer,
developer and product manager; a shiny, great product; follow my patterns. plan_v2 has to be
done now, then push and make a public GitHub release, and check that CI turns green."

Decisions (product, design, engineering), each one step below:

- **Never:** Raspberry Pi / multi-camera appliance (vision §3.2, §30–31, phase 9) — owner
  decision; removed from roadmap and traceability. Depth and learned/image classifiers stay
  conditional as the vision prescribes (they need real data, not engineering).
- [x] V3-1 Architecture (plan_v2 §3.2.1): `MonitorSession` in `:core` owns the running meal
  (monitor, alarm policy, grace, false-alarm rest, thank-you, reminder target, statistics,
  status kind) and exposes an immutable `MonitorUiState`; the activity only renders it.
- [x] V3-2 Setup (plan_v2 screens 01–04): permission rationale card with *Open settings*
  after a denial; Position screen with a people stepper, lens chips and a *specific* reason
  when the visibility check fails; a placement illustration; widest rear lens as the default
  for new setups; a loupe while tapping/dragging corners; seat regions auto-proposed from the
  table edges (editable, overlap-checked).
- [x] V3-3 Monitor (screens 05–06, §8): meal summary card on Stop (duration, reminders per
  seat colour, longest reminder-free stretch — positive framing); "nobody visible for a
  while — has the phone moved?" hint with a recalibrate action; long-press volume-down
  pauses; thermal banner suggesting Lite; 180 ms cross-fade between screens.
- [x] V3-4 Settings (screen 07): grouped sections (Reminders, Sensitivity, Camera & model,
  Data); sensitivity presets Conservative / Normal / Responsive; a choice of three soft
  generated chimes; processor CPU / GPU (experimental, automatic CPU fallback).
- [x] V3-5 Platform polish: adaptive launcher icon with monochrome layer; per-app language
  (Android 13+ `locales_config`); static shortcut "Start dinner"; run the e2e suite on an
  API 36 emulator image as target-SDK evidence if the image can be installed.
- [x] V3-6b Owner request: a *Restart the 10-second check* button on the Position screen.
- [x] V3-6c Owner request: `docs/c4-architecture.md` — understandable C4 views (context,
  containers, components), the runtime workflows and the everyday user workflow.
- [x] V3-6a Docs: `docs/emulator.md` — run the app on a laptop emulator (SDK install, AVD
  creation, boot, install, virtual camera/webcam, demo, e2e, troubleshooting); README link.
- [x] V3-6r Self-review of the v3 diff (0272154..bb33681), fix before release:
  - [x] R1 (medium) people stepper keeps the engine's old pose limit — reopen the source when
    the pose limit it was built with differs from what the screen needs.
  - [x] R2 (medium) per-seat reminder counts include grace, rest and too-slow episodes —
    count seats only while the session is reminding.
  - [x] R3 (low) pausing/stopping mid-reminder counts it as calm — silence() starts a new
    calm stretch.
  - [x] R4 (low) stored `violations` statistic changed meaning — keep Monitor.violations.
  - [x] R5 (low) TOO_MANY unreachable (numPoses = people) — setup screens detect up to four.
- [x] V3-6 Quality and release: self-review of the whole v3 diff, fixes, refreshed genuine
  screenshots (light, dark, landscape), README/docs, full pipeline, public release, green CI.

## Plan v4 — a set table: pots, plates and glasses in the view (owner request 2026-09-26)

Owner input: "Is this also considering stuff on the table? … a pot or plates occlude the direct
view of the pose … can we improve this. Consider a non-empty table as well." Then: "also run
once /updateDependencies".

Assessment: an occluded joint (confidence < 0.7) makes that arm UNKNOWN — safe (no false
reminder) but blind: an elbow on the table with the hand behind a bottle is never reminded, a
dish passed in front drops and restarts a running reminder, and the setup check runs on an
empty table. Decisions, all conservative (missing evidence still never *starts* a reminder):

- [x] O1 Brief-occlusion hold: a running VIOLATION survives up to 600 ms of hidden joints
  (a dish passed in front) instead of flickering off; frame gaps and invalid scores still
  reset at once. New `Timing.holdMs`, persisted.
- [x] O2 Hidden-hand bridge: after full supported evidence (shoulder, elbow, wrist), the arm
  stays "supported" while only the wrist is hidden, the elbow stays within a small radius of
  where it rested and is still, for at most 10 s. A hand hidden from the start is *not*
  guessed — that needs real labelled sessions (training mode + replay) first.
- [x] O3 Occlusion hint: when an arm of a present seat is hidden most of the time for 20 s,
  the monitor says which seat and side and suggests moving the pot/bottle or the phone.
- [x] O4 Set-table setup: the visibility check and placement copy ask to run it with the table
  set as for dinner; hardware protocol gains set-table and occlusion scenarios.
- [x] O5 Tests (synthetic occlusion scenarios), docs (detection, C4 workflow, README), full
  pipeline; `/updateDependencies` and the release continue as L6.

## Plan v5 — licensing, SBOM, About screen, repository metadata (owner request 2026-09-26)

Owner input: "run /githubAbout and make decisions what to remove and what to add — reflect the
current state"; "make sure we obey GPLv3"; "the app has an info screen which tells who made it
and what dependencies are used, with their license"; "make sure everything is SPDX tagged and
create an SBOM as part of the release pipeline"; "everything tested and covered, coverage at
least 95 %, part of the pipeline"; "get all done, make a plan, then public release again".

- [x] L1 SPDX everywhere: every authored text file carries `SPDX-License-Identifier`; binary
  and generated files (PNG, WAV, models, wrapper JAR) are annotated in `REUSE.toml`;
  `scripts/spdx.py` checks it and runs in the pipeline (Python stage), so a new untagged file
  fails CI.
- [x] L2 SBOM: CycloneDX JSON for the release runtime classpath (`org.cyclonedx.bom` Gradle
  plugin, pinned), plus the bundled models as components; built by the pipeline, attached to
  every GitHub release, served by the Docker image and uploaded as a CI artifact.
- [x] L3 About screen: author and copyright, GPL notice with warranty disclaimer (GPLv3 §§ 0,
  15–16 "appropriate legal notices" for an interactive program), then every bundled
  third-party component with version, license and link — generated from the SBOM into a
  checked-in asset (`scripts/licenses.py`, `--check` in the pipeline, so the screen can never
  drift from what is shipped); full license texts (GPL-3.0, Apache-2.0) readable in the app.
- [x] L4 GPLv3 audit: `LICENSE` (full text), `LICENSES/` (GPL-3.0-or-later, Apache-2.0), headers,
  corresponding source for every binary (release notes and Docker install page link the exact
  tag), third-party license texts shipped with the APK and the image, Apache-2.0 compatibility
  recorded in `NOTICES.md`, and `docs/licensing.md` explaining it.
- [x] L5 Coverage: keep the merged Kover gate (≥ 95 % lines, `koverVerifyAll`, already a
  pipeline stage and CI gate); cover all new code; publish the coverage figure in the summary
  and the release notes.
- [x] L7 Linters for the whole stack in the pipeline and CI, before the release (owner
  request): detekt (Kotlin static analysis, alongside ktlint and Android lint), ruff (Python),
  ShellCheck (kept), actionlint (GitHub workflow), hadolint (Dockerfile), yamllint, xmllint
  (well-formed XML resources), markdownlint (docs) — each pinned, each a numbered stage or
  part of one, failures fail the pipeline.
- [x] L8 Owner request: collect all of today's guidelines in `agents.md` as a real working guide
  (plan first, reviews into the plan, product principles, quality gates, licensing, docs,
  release procedure).
- [ ] L9 Backlog: split `MainActivity` (single-activity UI, excluded from detekt's LargeClass)
  into per-screen classes.
- [ ] L6 `/updateDependencies` once, `/githubAbout` with decisions reflecting the current state,
  full pipeline, green CI, public release.

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
- 2026-09-26 session: "make a plan, get all done" — finish plan v3 (V3-1…V3-6) now. Also
  "write docu: how to run the emulator on a laptop, etc." and hand over a GitHub URL of the
  Android APK package at the end (release asset link).
- 2026-09-26, after the self-review: "good, put them to the plan and get them fixed".
- 2026-09-26: "check what is left for the project and fix those things as well", "all handles,
  go go go, and make a public release"; "allow to restart the config period of 10 s …
  a button near that screen"; "add a C4 architecture markdown document … workflows … and the
  general workflow for the user … make it understandable".
- 2026-09-26: "is this also considering stuff on the table? … pot or plates occlude … can we
  improve this. consider a non-empty table as well"; "also run once /updateDependencies".
- 2026-09-26: "/githubAbout … decide what to remove and what to add … reflect the current
  state"; "obey GPLv3"; "info screen: who made it, which dependencies, with their license";
  "everything SPDX tagged, and create an SBOM as part of the release pipeline"; "everything
  tested and covered, at least 95 %, coverage part of the pipeline"; "get all done, make a
  plan"; "then public release, again".
- 2026-09-26: "yes, REUSE is a good thing, make sure we have a badge for the README"; "also mark
  all the guidelines I gave today in agents.md, so that we collect some really good guide".
- 2026-09-26: "add linters of all kinds, for this tech stack, to the pipeline — before the
  release"; "big plan, then iterate and get it done".

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
- [x] Step 10: run `/reviewBranch`, fix confirmed findings, run `/githubAbout`, audit
  every `vision.md` section, full local pipeline, green Actions, verified GHCR image,
  clean working tree.
- [x] Step 11: public GitHub release (tag `v<VERSION>`) with the APK attached and
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
| §3.2, §30–31, phase 9 Raspberry Pi / multi-camera | — | — | **Never** (owner decision 2026-09-26) |
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

- [x] Deterministic synthetic scenario replay shared by demo and regression tests (`SyntheticDemo` drives the demo, `SyntheticDemoTest` and `replay.sh demo-log`).
- [x] Calibration reminder after camera/model changes and orientation handling (aspect/rotation invalidation since 0.6.15, lens change clears the outline).
- [x] Session-separated export metadata for future classifier training without leakage (session id in every log header; whole-session evaluation in `docs/data.md`).

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

### 0.10.32 — chore(release): first public release v0.10.32

- Gate: self-review done and fixed (0.9.29); `/githubAbout` applied — About text updated to
  "Offline Android app that gently reminds a family to keep elbows off the dinner table…
  Software-tested; real-meal validation pending." (topics kept: android, kotlin, mediapipe,
  camerax, pose-estimation, computer-vision, posture-detection, table-manners, on-device-ml,
  privacy-first); vision audit in the traceability table; `plan_v2/` re-checked (unchanged
  since 0.5.13); full local pipeline green; remote Actions green for 0.10.31.
- Release: tag `v0.10.32` → Actions builds, tests, signs, publishes
  `ghcr.io/marcelpetrick/forkaroundandfindout:0.10.32` and `latest`, and creates the GitHub
  release with the signed arm64 APK, its SHA-256 and `docs/release-notes.md`.
- Honest status: software complete for the vision's applicable scope; phone performance and
  real-meal accuracy (§15 targets, §29 household acceptance) are **not measured** — protocol
  in `docs/hardware-validation.md`. Learned/image classifiers and depth remain (Raspberry Pi: never, owner decision)
  conditional future work as the vision prescribes. Hence 0.x, not 1.0.0.
- Owner actions: back up `~/.android/fork-around-and-find-out-release.{jks,properties}`;
  run the hardware protocol; answer plan_v2 §9 questions if the defaults do not fit.

### 0.10.33 — docs: plan v3 to complete plan_v2; Raspberry Pi marked never

- Done: consolidated plan (above) folding every open plan_v2 item and the chosen §8 product
  ideas into steps V3-1…V3-6; Raspberry Pi / multi-camera marked **never** in plan, README,
  agents.md and pose-framework notes (vision.md stays the unedited original input).
- Validation: documentation only; link check and whitespace in the pipeline.

### 0.10.34 — refactor: move the running meal into a JVM-tested MonitorSession (V3-1)

- Done: `MonitorSession` (`:core`) owns monitor, alarm policy, grace, false-alarm rest,
  thank-you, reminder target, per-seat reminder episodes, calm-stretch record and a status
  enum; it emits an immutable `MonitorUiState` per tick. `MainActivity` only renders it and
  plays the returned sound. Part of V3-3 landed with it: a positive *Last meal* card on the
  welcome screen (time, reminders, longest calm stretch, reminders per seat colour) and a
  "nobody visible for a while — has the phone moved?" hint with a *Recalibrate* action.
- Fixed during the move: backgrounding re-renders the monitor so it reads *Paused/Resume*
  on return; session statistics keep millisecond duration.
- Validation: `MonitorSessionTest` (grace → reminder → thank-you → rest → pause → summary;
  nobody hint; slow/too-slow; suspend), Robolectric flows extended (summary card, hint);
  full local pipeline.

### 0.11.35 — feat: guided setup with placement picture, people and lens choice, loupe and seat suggestions (V3-2)

- Done: Position screen shows a placement illustration, a people stepper and Wide/Main/Tele
  lens chips (new setups start on the widest rear lens; a lens change reopens the camera and
  clears the outline); the visibility check now names the reason (nobody, too few, too many,
  arms hidden with left/middle/right of the picture, slow processing → Lite). A refused camera
  permission shows the rationale and *Open app settings*; Android's rationale case asks first.
  A loupe magnifies a still of the preview while a corner is pressed or dragged.
  *Suggest seats* proposes one region per person from the table edges (`SeatProposal`:
  people spread by edge length, bands mitred so neighbours never overlap, clipped to the
  image) with a live "people inside a seat now: n of m" check.
- Decision: seat proposals are a button, not automatic — zones restrict who is watched, so a
  wrong automatic proposal could silently leave someone unwatched; automatic assignment
  stays the default.
- Validation: `SeatProposalTest`, `VisibilityCheckTest` (reasons, sides), Robolectric
  setup flow (stepper, lens chips, every advice text, loupe, suggestion, seat check,
  settings intent); full local pipeline.

### 0.11.36 — feat: pause by holding volume-down, offer Lite on a warm or slow phone, fade between screens (V3-3)

- Done: holding volume-down pauses a running meal (a short press still lowers the volume;
  holding never resumes — resuming stays a deliberate tap); a banner explains a warm
  (thermal ≥ moderate) or slow phone and switches to the Lite model in one tap while the
  meal and calibration continue; screens fade in over 180 ms. With 0.10.34 (meal summary,
  "has the phone moved?" hint) V3-3 is complete.
- Validation: Robolectric (thermal banner, Lite switch reopens inference, key handling on and
  off the monitor); full local pipeline.

### 0.11.37 — feat: grouped settings with sensitivity presets, three chimes and an experimental GPU processor (V3-4)

- Done: Settings are grouped (Reminders · Sensitivity · Camera and model · Data, with *Test
  sound* and *Local data* in their sections). Sensitivity presets Conservative / Normal /
  Responsive set trigger level, reminder and clear delays and cooldown; hand-tuned values
  show *Custom*, and every value card refreshes when a preset changes them. Three generated
  chimes (Bell, Marimba, Glass; `scripts/chime.py`, Bell byte-identical to before) with
  *Test sound* playing the choice. Processor CPU / GPU (experimental): the GPU delegate is
  requested and inference falls back to the CPU when it cannot start; diagnostics show where
  it runs. Settings saved by older versions keep the defaults.
- Validation: `SensitivityTest`, option/speaker tests (presets, custom, chime switch unloads
  the old sound), codec round trip and migration, GPU/CPU fallback through the factory seam,
  Robolectric grouped page; e2e runs the Lite model with a GPU request on the emulator —
  measured: the emulator's GPU delegate does not start and the engine falls back to the CPU
  (log "LITE requested GPU, runs on CPU"); real GPU speed needs a phone. The e2e scroll helper
  now also finds buttons by content description (the grouped page is longer).
- Validation run: full local pipeline green except the first e2e run (scroll helper, fixed);
  emulator suite re-run 4/4 green.

### 0.11.38 — feat: adaptive launcher icon, per-app language and a Start-dinner shortcut (V3-5)

- Done: adaptive launcher icon (background, foreground scaled into the safe zone, monochrome
  layer for themed icons) replacing the flat vector; `locales_config` (en, de) so Android 13+
  offers a per-app language; a static launcher shortcut *Start dinner* that goes straight to
  monitoring when the table is set up and otherwise explains the one-time setup (a running
  meal is never restarted; the activity is `singleTask`).
- Target-SDK evidence: the API 36 x86_64 Google APIs image was installed (`ForkApi36`, see
  `docs/emulator.md`); the full local pipeline including the 4 e2e tests passed on it (SDK 36).
  CI stays on API 34 (minSdk).
- Validation: Robolectric shortcut flow (not set up / set up / repeat / plain launch), lint;
  full local pipeline on the API 36 emulator.

### 0.11.39 — docs: record self-review findings R1–R5 for the v3 diff

- Done: `/reviewBranch` over 0272154..bb33681 found two medium and three low issues; the owner
  asked to put them in the plan and fix them (R1–R5 above).
- Validation: documentation only; whitespace and link checks.

### 0.11.40 — fix: address self-review R1–R5 and let the visibility check restart

- R1/R5: the pose limit is chosen per screen — setup looks for up to four people (an extra
  diner now shows "More people are visible…"), monitoring for exactly the configured number;
  the source reopens when the limit it was built with no longer fits (stepper, recalibrate
  from the monitor).
- R2: per-seat reminder counts only while the session reminds (not in grace, rest or on a
  too-slow phone). R3: pausing/stopping mid-reminder starts a new calm stretch.
- R4: the stored `violations` statistic stays the per-elbow episode count.
- Owner request: *Restart the 10-second check* on the Position screen discards the evidence
  and closes the gate until ten fresh seconds pass.
- e2e: a tap right after a scroll swipe hit a still-flinging page and only stopped it (the
  longer welcome screen made it reproducible); the helper now drags slowly and lets the
  page settle. Failure artefacts now include a UI hierarchy dump next to the screenshot.
- Validation: new `MonitorSessionTest` cases (grace, pause mid-reminder), Robolectric pose
  limits per screen and the restart; full local pipeline green except that e2e case, which
  then passed 4/4 on the API 36 emulator after the fix.

### 0.11.41 — fix: keep the people stepper label readable on phones

- Done: the refreshed screenshots showed "People at the table: n" wrapping between the − and +
  buttons; the label now sits above them.
- Validation: Robolectric setup flow; full local pipeline green (e2e 4/4 on API 36).

### 0.11.42 — docs: C4 architecture and workflows, laptop emulator guide, refreshed screenshots

- Done: `docs/c4-architecture.md` (owner request: understandable C4 context, containers and
  components, runtime workflows — setup, per-frame, elbow states, training/replay, release —
  and the everyday family workflow); `docs/emulator.md` (owner request: SDK, AVD for
  x86_64/Apple Silicon, virtual camera or webcam, boot, install, demo, e2e, troubleshooting);
  README (new features, grouped settings, docs links, dark and landscape screenshots);
  architecture and scripts docs follow MonitorSession, SeatProposal, GPU fallback and the
  extended screenshot script. Genuine screenshots re-captured on the API 36 emulator
  (welcome, demo warning, setup check, settings, dark, landscape). Backlog items from 0.0.1
  closed with references.
- Validation: link check, ShellCheck, whitespace; full local pipeline green on this tree.

### 0.11.43 — chore(release): prepare public release v0.11.43 (not published)

- Gate: `/reviewBranch` over the v3 diff → R1–R5 fixed (0.11.40); `/githubAbout` re-checked —
  About text and the ten topics still match the product and are backed by the repo, left
  unchanged; vision traceability table re-audited (no requirement changed status: software
  complete for the applicable scope; §15/§29 real-meal targets **not measured**; learned and
  image classifiers and depth conditional; Raspberry Pi never); `plan_v2/` re-checked
  (unchanged since 0.5.13, every item folded into V3-1…V3-6); full local pipeline green on
  0.11.41/0.11.42 trees (e2e on API 36); remote Actions green up to the pushed commits.
- Release: tag `v0.11.43` → Actions builds, tests, signs, publishes
  `ghcr.io/marcelpetrick/forkaroundandfindout:0.11.43` and `latest`, and creates the GitHub
  release with the signed arm64 APK, its SHA-256 and `docs/release-notes.md`.
- Honest status: still 0.x — 1.0.0 waits for the hardware protocol's real-meal numbers.
- Outcome: the `v0.11.43` tag run failed in CI e2e ("'latency p50' did not appear") although
  the same commit passed on `main`; nothing was published (GHCR and release steps skipped).
  The tag was removed; the fix and the release follow as 0.11.44.

### 0.11.44 — fix(ui): keep monitor controls fixed while hints appear; release v0.11.44

- Cause: on a slow phone (the CI emulator runs at ~5 FPS) the Lite banner appeared above the
  adult toggle, and the status line and "Recalibrate" hint above Pause change height during
  the meal, so a control could move under a finger just before the tap — a real usability
  defect the e2e run exposed.
- Fix: Pause, Stop and the adult toggle now come directly under the title and never move;
  status, hints, banner, diagnostics and seat cards follow below them.
- Release: `docs/release-notes.md` for 0.11.44; tag `v0.11.44` after green `main` CI.
- Validation: Robolectric, lint; full local pipeline (e2e on the API 36 emulator).

### 0.11.45 — docs: plan v4 for a set table (occlusion by pots, plates, glasses)

- Done: assessment and decisions O1–O5 above; owner input recorded.
- Validation: documentation only; whitespace and link checks.

### 0.11.46 — docs: plan v5 for licensing, SBOM, About screen and repository metadata

- Done: decisions L1–L6 above; owner input recorded. Order: v4 (O1–O5), then v5, then
  dependency update, GitHub About, release.
- Validation: documentation only; whitespace and link checks.

### 0.12.47 — feat: a set table — brief-occlusion hold, hidden-hand bridge and occlusion hint (plan v4)

- Done: O1 `Timing.holdMs` (600 ms) keeps a running VIOLATION while joints are hidden in
  fresh frames (never SUSPECT, never across frame gaps, never for sparse-frame history —
  evidence now says *why* it is missing); O2 `ArmClassifier` bridges a fully seen rest while
  only the wrist is hidden, the elbow stays still within 0.15 shoulder widths, ≤ 10 s; a hand
  hidden from the start is never guessed; O3 `MonitorUiState.hiddenArm` names an arm hidden
  in ≥ 70 % of in-view frames for 20 s; O4 setup copy asks for the set table; `holdMs`
  persisted in settings and session logs (older logs replay with 0 ms, as decided live);
  `replay --hold-ms`. Docs: detection, C4, hardware protocol scenarios, README.
- Note: the plan-only commit ee589e8 did not bump VERSION; this one continues the sequence.
- Validation: TemporalFilter (hold, no hold for SUSPECT/gaps/sparse history, zero hold),
  Detector (brief occlusion, hidden hand bridge, moved elbow, expiry, hidden from start),
  MonitorSession and Robolectric hint, replay option; full local pipeline.

### 0.13.48 — feat: REUSE/SPDX everywhere, full linter suite, SBOM in the release and a licence-complete About screen (plan v5)

- L1/L7 lint and licensing gates: every file REUSE 3.3 compliant (canonical
  `SPDX-FileCopyrightText`/`SPDX-License-Identifier` headers, `REUSE.toml` for binaries, JSON,
  wrapper, `plan_v2/`, models and verbatim notices, `LICENSES/`); new pipeline stages *Lint
  Suite* (`scripts/lint.sh`: reuse, ruff, yamllint, xmllint, hadolint, actionlint,
  markdownlint in pinned images; `docker/lint/Dockerfile`, `ruff.toml`, `.yamllint.yaml`,
  `.markdownlint-cli2.yaml`) and *Detekt* (`detekt.yml`); all findings fixed: magic numbers
  named (MediaPipe `Joint` indices, the arm rule's thresholds, tolerances), long functions
  split (arm rule, temporal filter, monitor, alarm policy, replay, seat tracker, overlay,
  settings, activity), intended boundary catches annotated with reasons; `MainActivity`
  size recorded as backlog L9. REUSE badge (backed by the CI gate) plus SBOM/detekt/ktlint
  badges in the README.
- L2 SBOM: CycloneDX plugin 3.4.1 over the release runtime classpath; `scripts/sbom.py build`
  adds the app, the models and licence choices → `build/sbom/*.cdx.json` (pipeline stage
  *SBOM*, CI artifact, GitHub release asset, served by the Docker image).
- L3/L4 About and GPLv3: author, copyright, GPL notice with the warranty disclaimer and the
  full GPL text, the exact source tag, all 93 bundled components with version and SPDX
  licence (from the SBOM; `notices --check` in the pipeline), the Apache-2.0/BSD-3-Clause/MIT
  texts, MediaPipe's and jakarta.inject's Apache NOTICE files (paged) and the protobuf and
  Checker Framework copyright notices; `NOTICES.md` rewritten; `docs/licensing.md` records
  the compatibility review, the MIT choice for checker-compat-qual, the scan of MediaPipe's
  187 native-library notices and one honest open question (OpenCV's generic 4-clause
  template). Docker page links SBOM, licences and the source tag; smoke test checks them.
- L5 coverage: gate unchanged (≥ 95 % lines, `koverVerifyAll`); new code covered.
- Validation: `reuse lint` compliant (all files), all 7 container linters clean, detekt 0
  findings, full local pipeline.

### 0.13.49 — docs: collect the owner's guidelines in agents.md

- Done: `agents.md` rewritten as the project's working guide — every earlier rule kept, and
  today's instructions added: plan first and iterate, review findings into the plan, product
  principles (conservative, set table, fixed controls, restartable checks), quality gates
  (all linters, detekt, coverage ≥ 95 %, e2e habits), licensing (GPLv3 notices and source,
  REUSE, SBOM, compatible licences), documentation (understandable C4, emulator, licensing),
  release procedure (tag after green CI, failed tag → new version, hand over the APK URL).
- Validation: markdownlint, link check, REUSE.

### 0.13.50 — build(deps): update toolchain, plugins, test libraries, actions and lint image

- `/updateDependencies` (L6): checked every pin against Google Maven, Maven Central, the
  Gradle Plugin Portal, Gradle's version service, Docker Hub and the GitHub releases API.
  Updated: Android Gradle Plugin 9.3.2 → 9.4.1, Gradle wrapper 9.7.1 → 9.8.0, Spotless
  8.10.0 → 8.10.3, ktlint 1.7.1 → 1.8.0 (new default: blank lines between multi-line `when`
  branches — adopted, code reformatted), UiAutomator 2.3.0 → 2.4.0, lint image Python 3.13 →
  3.14 (digest-pinned); GitHub Actions pinned from floating majors to exact latest tags
  (checkout 7.0.1, setup-java 6.0.1, setup-android 4.0.4, gradle/actions 6.3.0, cache 6.1.0,
  android-emulator-runner 2.38.0, upload-artifact 7.0.1). Already latest: activity-ktx
  1.13.0, CameraX 1.6.2, MediaPipe Tasks 1.0.0, test runner 1.7.0, ext-junit 1.3.0, Mockito
  5.24.0, JUnit 4.13.2, Robolectric 4.17, Kover 0.9.9, detekt 1.23.8, CycloneDX 3.4.1, nginx
  1.30.5 (newest stable line; 1.31 is mainline).
- Validation: full local pipeline (all stages green except Format, then fixed by adopting
  ktlint 1.8 and re-verified: format, detekt, all unit/Robolectric tests, Android lint,
  coverage 97.8 %).
