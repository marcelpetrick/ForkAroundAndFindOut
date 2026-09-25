# Plan v2 — full review, decisions, and the road to a finished product

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

Written 2026-09-25 against commit `c32d9a0` (version 0.3.7, CI run 36191251268 green)
plus the uncommitted work present at that moment (`demo/SyntheticDemo.kt`,
`FrameSource`/`bounds` in `CameraSession.kt`, `Polygon.overlaps`). Line references are
to `c32d9a0` unless stated. Everything measured here was measured on this machine
(Manjaro, Java 21, Gradle 9.7.1, AGP 9.3.2) or on the running `ForkApi34` x86_64
emulator. Nothing below is a claim about a physical phone; the physical-phone work is
the biggest open item and is planned in §7.

---

## 0. How to read this

- **§1** is the short version: the twelve decisions that matter.
- **§2** is where the project stands, requirement by requirement.
- **§3** is the review by aspect. Each aspect ends with a boxed *Decision*.
- **§4** lists concrete findings in the current code, ranked.
- **§5** is the re-sequenced delivery plan with acceptance criteria and required evidence,
  and it folds in every line of the owner's original brief (repeated in §5.0).
- **§6** is the vision traceability matrix, v2.
- **§7** is the hardware validation protocol — the one thing no test can replace.
- **§8** is "beyond the vision": how to make the app genuinely pleasant.
- **§9** open questions for the owner. **§10** self-review log.

The UI/UX design that goes with this plan is in [`design/ui-ux.md`](design/ui-ux.md).

---

## 1. Executive summary — the twelve decisions

1. **Keep the architecture shape.** Camera → pose engine → pure-Kotlin detection →
   monitor → UI, single process, no service. It is the right shape and it is already
   well tested. Do not rewrite; fix the five things in items 2–6.
2. **Make rotated-image space the one canonical coordinate system.** Landmarks,
   calibration polygon, seat zones and detection all live in normalized coordinates of
   the *rotated analysis image*. The preview view converts on the way in (taps) and on
   the way out (overlay). Today `CameraSession` maps landmarks into *preview-view* space
   and the new `bounds` field papers over the letterbox; that couples calibration to
   layout and is why `calibrationAspect` invalidation exists. (§3.2.3)
3. **Cut two 3.7 MB allocations and a software rotation per frame.** Use a smaller
   analysis resolution than the preview, feed MediaPipe a reused RGBA `ByteBuffer`, and
   pass rotation through `ImageProcessingOptions.setRotationDegrees` instead of
   `Bitmap.createBitmap(rotate)`. All three APIs exist in the pinned 1.0.0 AAR. (§3.3.4)
4. **Decouple the latency budget from the gap budget.** `Monitor.frame` drops any frame
   older than 500 ms and `TemporalFilter` resets on gaps > 500 ms. On a mid-range phone
   with the Full model and `numPoses = 4` that budget may be blown by inference alone,
   which yields a permanent silent UNKNOWN. Raise the freshness bound, derive the gap
   bound from the measured inference period, and tell the user when the phone is too
   slow. (§3.4.3, finding F1)
5. **Record landmark streams, not feature snapshots, in training mode.** The current
   sample record (`LocalStore.kt:67`) stores one feature vector at the instant of the
   label. That cannot retrain or even re-tune the rules. A gzip'd JSONL of poses +
   timestamps + calibration + labels per session (tens of KB per minute, no images)
   plus a JVM replay harness makes vision §29's acceptance table executable against
   real meals. This is the single highest-leverage addition. (§3.7)
6. **Ship the "visibility check" as the first screen after positioning.** The vision's
   Phase-1 question ("can MediaPipe track both elbows of everyone seated from this
   position?") becomes an in-app readout: *2 of 2 people, both elbows visible for
   10 s ✓*. Start is enabled only after it passes. (§3.8.3, design screen 02)
7. **Plain Android Views, no Compose, no Material library.** `PreviewView` is a View, a
   Canvas overlay is the natural way to draw skeletons and polygons, the app has about
   eight screens, and Robolectric covers Views far more cheaply than Compose. (§3.8.1)
8. **Split `detection` (and the pure parts of `monitoring`) into a JVM Gradle module.**
   It already has zero Android imports. As a JVM module its tests run in seconds without
   Robolectric, the replay harness becomes a plain `main()`, and the 95 % gate is
   measured where it means something. (§3.11.2)
9. **ABI-split the release APK.** 78 MB today, of which ~48 MB is four copies of
   `libmediapipe_tasks_jni.so`. Every Android 14 phone is arm64; release =
   `arm64-v8a` only (≈ 26 MB); debug keeps `x86_64` for the emulator. (§3.12.2)
10. **Sign release builds in CI with a dedicated keystore held in GitHub secrets**, and
    publish the fingerprint in the README. An unsigned release APK is not a product. (§3.12.3)
11. **Docker stays minimal and honest:** an `nginx:alpine` image that serves the signed
    APK, its SHA-256, the LICENSE and an install page; built and pushed to GHCR by
    Actions; smoke-tested by `curl` + checksum in the pipeline. (§3.13.2)
12. **Instrumented tests join CI** via an emulator job (API 34 x86_64, cached AVD) that
    runs `NativeModelTest`, drives the synthetic demo through the UI, and captures the
    README screenshot. Physical-phone evidence is recorded by the protocol in §7 and
    never fabricated. (§3.11.3)

Everything else in the current design — the conservative rule set, the per-elbow
hysteresis, the seat tracker's ambiguity rejection, the privacy posture, the pinned
toolchain, the per-commit versioning — is right and stays.

---

## 2. Where the project stands

### 2.1 Codebase at a glance (HEAD `c32d9a0`)

| Package | Lines | Role | Android deps | Test style |
| --- | ---: | --- | --- | --- |
| `detection/` | 352 | geometry, pose features, rules, seat tracking, temporal filter | none | JUnit, deterministic |
| `monitoring/` | 356 | settings (JSON v1), session controller, alarm policy, local store | `Context`, `SharedPreferences`, `AtomicFile`, `org.json` | JUnit + Robolectric |
| `camera/` | 311 | CameraX session, busy-drop analyzer, MediaPipe engine | CameraX, MediaPipe | Robolectric + Mockito + one instrumented test |
| `MainActivity.kt` | 20 | placeholder `TextView` | — | Robolectric launch test |
| `demo/` (uncommitted) | 62 | deterministic synthetic two-seat meal | none | none yet |

Coverage measured locally on HEAD: 552/560 lines (98.6 %). With the uncommitted
`SyntheticDemo.kt` and no test for it the gate currently fails at 94.8 % — that is the
other session's in-flight work, not a defect.

APK: debug 81.6 MB, release (unsigned) 77.7 MB. Composition of the release APK:

| Item | Size |
| --- | ---: |
| `lib/x86/libmediapipe_tasks_jni.so` | 15.6 MB |
| `lib/x86_64/libmediapipe_tasks_jni.so` | 13.7 MB |
| `lib/arm64-v8a/libmediapipe_tasks_jni.so` | 11.0 MB |
| `lib/armeabi-v7a/libmediapipe_tasks_jni.so` | 7.7 MB |
| `assets/pose_landmarker_full.task` | 9.4 MB |
| `assets/pose_landmarker_lite.task` | 5.8 MB |

Toolchain pins vs upstream on 2026-09-25: `tasks-vision 1.0.0` (latest; previous line
was 0.10.35), CameraX `1.6.2` (latest stable; 1.7.0-alpha03 exists), `activity-ktx
1.13.0` (latest stable), Robolectric `4.17` (latest). Nothing is stale.

### 2.2 Requirement status (vision §14–15)

| Req | Statement | Status | Evidence / gap |
| --- | --- | --- | --- |
| FR-01 | continuous frames from a selected rear camera | Logic done, no UI | `CameraSession.bind`, Camera2 id filter; no camera picker |
| FR-02 | on-device, no cloud | Done | manifest requests only `CAMERA`; models bundled |
| FR-03 | 1–4 people | Done | `numPoses = settings.people` |
| FR-04 | arm landmarks with confidence | Done | `MediaPipeEngine` maps min(visibility, presence) |
| FR-05 | table calibration, persisted | Persistence done, UI missing | `Settings.table`, `Polygon` validation |
| FR-06 | optional seat zones | Persistence + matching done, UI missing | `SeatTracker`, `Polygon.overlaps` (uncommitted) |
| FR-07 | per-elbow UNKNOWN/CLEAR/SUSPECT/VIOLATION | Done | `TemporalFilter` |
| FR-08 | no single-frame alarm | Done, tested | `TemporalFilterTest` |
| FR-09 | configurable visual/audio warning | Policy done, no rendering/sound | `AlarmPolicy`, `VisualMode`, `AudioMode` |
| FR-10 | automatic clearing | Done | clear delay + cooldown |
| FR-11 | immediate pause | Logic done, no UI | `Monitor.pause` |
| FR-12 | debug overlay | Missing | nothing draws; FPS/latency not measured |
| FR-13 | persisted configuration | Done except `maxGapMs` | `Settings.encode/decode` |
| FR-14 | no recording by default | Done | no image path exists |
| FR-15 | training mode | Store done, wrong granularity, no UI | see §3.7 |
| FR-16 | statistics | In-memory only, no correction-event count | `Monitor.violations`, `confidenceTotal` |
| NFR | ≥10 FPS, 720p, zero backlog, background thread, uncertainty ⇒ no alarm | Backlog/thread/uncertainty done; FPS unmeasured; latency budget risk | §3.4.3 |

Everything in the "Missing" and "no UI" cells is the remaining product work; §5 sequences it.

### 2.3 Addendum — the tree advanced while this was written (`c32d9a0` → `abcfbc9`, 0.5.12)

Five commits from the implementing session landed during the review. Checked against
the findings and milestones below so nothing is planned twice:

| Landed in `49e660c` | Effect on this plan |
| --- | --- |
| `6beb849` full native UI: welcome, position (live skeleton + people count), four-corner marking with undo/reset and letterbox-tap rejection via `CameraSession.bounds`, optional seats with overlap rejection, monitor with per-seat words, always-visible Pause/Resume, grace countdown, 100 ms watchdog tick, border/icon/tint/0.5 Hz pulse, once/repeat/continuous tones, diagnostics, persisted settings, synthetic demo; `MainActivity : ComponentActivity`, `FLAG_KEEP_SCREEN_ON`, permission launcher, `CreateDocument` export; 1198/1220 lines covered | **M3 and M4 are largely delivered.** Their remaining items are now: visibility-check gating of *Continue* (§3.8.3), camera picker with Main/Wide labels, auto-proposed seat zones, loupe, orientation lock, insets/predictive back, the false-alarm 30 s cooldown, and the copy/colour pass from `design/`. |
| Calibration stores `calibrationAspect = view.width / view.height` (`MainActivity.kt:383`) and the stage is drawn from `bounds` | **F2 is now baked into persisted calibrations.** Do the image-space switch (§3.2.3) *before* anyone calibrates a real table, i.e. at the top of M1, because a later switch invalidates every stored calibration. |
| `Monitor.kt`, `TemporalFilter.kt`, `FrameAnalyzer.kt`, `PoseEngine.kt` unchanged | F1, F4, F7, F8 open exactly as described. |
| `CameraSession.kt` still `catch (error: Exception)` at 82/129/170; `completed()` still in the main-thread `finally` (173) | F5, F8 open (line numbers at `49e660c`). |
| `ui/Speaker.kt`: `ToneGenerator(STREAM_MUSIC)` with `TONE_PROP_BEEP` (400 ms) and `TONE_SUP_DIAL` for continuous | Works and is testable. §3.6's SoundPool chime with `USAGE_NOTIFICATION_EVENT` is a polish item for M7, not a blocker; a dial tone as the "continuous" sound should be replaced by then. |
| `49e660c` pipeline restyle: numbered stages, per-stage logs, `--e2e required` on an API 34 KVM emulator in CI, Docker stage stub (`scripts/docker-smoke.sh` expected), `scripts/report.py`, `scripts/emulator.sh`, extended badge set | **Decision 12 is done** (instrumented tests in CI). §3.13.1's model cache and `packages: write` are still to add; M6's emulator job item is done; M8's Docker stage already has its hook. |
| `agents.md` collaboration rules: explicit-path commits, `plan_v2/` read-only for the implementing agent, `plan.md` first | Matches how this directory is meant to be used (see `plan_v2/README.md`). |
| No `splits`, no `signingConfigs`, manifest still only `CAMERA`, no `screenOrientation`/`configChanges` | F9, F10 (orientation/insets/back), decision 10 open. |
| `abcfbc9` (0.5.12) training/feedback/statistics/data: False-alarm and Missed buttons store feature vectors of all seats with session id; training mode (off by default, never persisted) with seat selector and NORMAL/LEFT/RIGHT/BOTH labels captures **5 s of per-frame feature vectors for one seat** as one atomic batch; opt-in session statistics on stop (duration, violations, false alarms, missed, mean confidence); data screen with count, corruption notice, JSON export via file picker, confirmed delete-all; `LocalStore.addAll` all-or-nothing under the 5000-record cap; 1314/1336 lines covered | **F15 is done** (statistics persisted, correction events counted). **F3 moves from "snapshot" to "partial":** labels now carry a 5 s window, which is enough to *inspect* a rule decision but still not enough to re-tune or train — the window holds derived features (rules can't be recomputed after a feature change), covers one seat, and starts at the tap rather than spanning the session. §3.7's whole-session landmark log stays the M5 target; the new screen, export and delete flows are the right UI for it and can be kept. The replay harness (`tools/replay`) and module split are untouched. |

Net effect on §5: M1 and M2 stand as written and are now the *next* work; M3/M4 shrink
to the residual list above; M5 shrinks to landmark session logs + replay harness +
module split (its UI is done); M6–M9 stand. Coverage at `abcfbc9` is 98.4 % (1314/1336).

---

## 3. Review by aspect

Each subsection: what exists → assessment → alternatives considered → decision.

### 3.1 Product interpretation and scope

**What exists.** The vision recommends Flutter + native Kotlin. The owner chose Kotlin
throughout (`agents.md`, `docs/pose-frameworks.md`). Docker is mandated as a
distribution channel. The vision's conditional items (learned classifier, crop
classifier, depth, Raspberry Pi) are correctly kept conditional.

**Assessment.** The Kotlin-only decision removes an entire layer (platform channel,
texture bridging, Dart state) and the vision's own §17 argues Kotlin must own the
pipeline anyway. Everything in the vision that mentions Flutter maps 1:1 onto native
screens. No requirement is lost. The Docker mandate is unusual for a phone app; the
honest framing (already in `agents.md`) is "Docker distributes the APK, it does not run
the app". Keep it small.

**Decision.** Kotlin-only stands. Docker = static APK distribution image (§3.13.2). The
conditional research items stay conditional and are called out as such in the README.

### 3.2 Architecture

#### 3.2.1 Layers and ownership

```
                     main thread                      analysis thread            MediaPipe thread
┌──────────┐   ┌───────────────────────┐   ┌───────────────────────────┐   ┌──────────────────┐
│ Activity │──▶│ Monitor (state owner) │◀──│ FrameAnalyzer (busy-drop) │──▶│ PoseLandmarker   │
│ + Views  │   │ Detector (pure)       │   │ CameraX ImageAnalysis     │   │ LIVE_STREAM      │
│ + overlay│◀──│ AlarmPolicy           │   └───────────────────────────┘   └────────┬─────────┘
└──────────┘   └───────────────────────┘                 ▲                          │ result
                         ▲                               └──────── completed() ◀────┘
                         └──────────── main.execute { onFrame(poses) } ◀────────────┘
```

**Assessment.** Good. State is owned on the main thread; the detector is pure and
deterministic; the analyzer never blocks CameraX; exactly one inference is in flight;
late callbacks after `close()` are suppressed. The one structural omission is a
**presenter/view-model between `Activity` and `Monitor`**: without it the Activity
will accumulate the calibration state machine, permission flow, alarm rendering and
lifecycle glue, and the 95 % gate will then push tests into Robolectric-driving a fat
Activity. Introduce a `MonitorPresenter` (plain Kotlin, main-thread, no Android imports
beyond `Handler`-free time injection) that owns the screen state and exposes an
immutable `UiState`; the Activity only renders it.

**Alternatives considered.**
- *Foreground service owning the camera.* Not needed: the screen is the alarm surface,
  so the Activity is always visible during a meal. A camera foreground service on
  Android 14 adds `FOREGROUND_SERVICE_CAMERA`, a notification, and no benefit.
- *`ViewModel` + `LiveData`/`Flow`.* Acceptable, but adds lifecycle dependency for a
  single-Activity app. A presenter with an injected clock is simpler to test and
  survives configuration changes if orientation is locked during a session (§3.2.4).
- *Separate process for inference.* No; MediaPipe already runs on its own native thread.

**Decision.** Keep the layering; add `MonitorPresenter` with `UiState`; keep the
Activity thin. No service.

#### 3.2.2 Threading and back-pressure

**What exists.** Single-thread analysis executor; `STRATEGY_KEEP_ONLY_LATEST`;
`FrameAnalyzer.busy` AtomicBoolean; `MediaPipeEngine.pending` AtomicReference owns the
`MPImage` until the async result or error; all UI delivery via `main.execute`.

**Assessment.** Correct and defensively written. One gap: `completed()` is called from
the main-thread `finally` in `CameraSession.result` (`CameraSession.kt:153`), so the analyzer
stays busy until the main thread gets around to the result. Under UI jank that adds
main-thread latency to the inference period. Call `completed()` on the MediaPipe
callback thread *before* posting to main (the engine has already released the image).

**Decision.** Move `completed()` to the callback thread. Keep everything else.

#### 3.2.3 Coordinate systems (the important one)

**What exists.** `FrameAnalyzer` rotates the bitmap to upright and reports
`(width, height, OutputTransform)`; `CameraSession.result` maps every landmark through
`CoordinateTransform(imageTransform, previewView.outputTransform)` and divides by the
*view* size, so `Monitor`/`Detector` receive *preview-view-normalized* coordinates and
`aspect = view.width / view.height`. Calibration taps would naturally be taken in the
same view space. The uncommitted `bounds: RectF` reports where the image sits inside
the letterboxed view. `Settings.calibrationAspect` invalidates a session when the
aspect drifts by > 0.03.

**Assessment.** This works, but it makes geometry depend on layout:
- Any change of the view's size or insets (system bars, edge-to-edge on API 35+, split
  screen, a different phone) shifts where the same physical table lands in normalized
  view space — hence the aspect guard, which also has false positives (a font-scale
  change that shrinks the preview) and false negatives (same aspect, different inset).
- Detection's metric correction uses the *view* aspect, but the letterbox means the
  view aspect ≠ image aspect. Distances normalized by shoulder width are unaffected,
  but `Polygon.signedDistance(…, aspect)` is applied in the wrong aspect whenever the
  view is letterboxed.
- Every consumer (overlay, taps, export, replay) needs the same transform.

**Alternative (recommended).** Canonical space = normalized coordinates of the
*rotated analysis image* (`0..1` × `0..1`, aspect = rotated image w/h, which is a
property of the camera + rotation, not of the layout):
- MediaPipe already outputs exactly this (with `setRotationDegrees` it outputs
  relative to the rotated image — verify once on the emulator with an asymmetric test
  image; add that assertion to `NativeModelTest`).
- Calibration taps: view → image via the inverse of `previewView.outputTransform`
  composed with the image transform (CameraX `CoordinateTransform` supports both
  directions by swapping arguments). Reject taps outside the visible image.
- Overlay: image → view with the forward transform; one `Matrix` per frame.
- Persist with the calibration: camera id, sensor rotation, analysis aspect. Invalidate
  on change of any of those three — all *real* geometry changes — and drop
  `calibrationAspect`.
- Export/replay: landmarks are already in image space; nothing to convert.

**Decision.** Switch to image space before the calibration UI is built (it is cheap
now and expensive later). Keep `bounds` only as a derived value for hit-testing.

#### 3.2.4 Process/lifecycle model

**What exists.** `MainActivity : Activity`, no orientation handling, no keep-screen-on,
no permission flow yet. `CameraSession` binds to the Activity lifecycle.

**Decision.**
- `ComponentActivity` (activity-ktx is already a dependency) for the permission
  launcher and `OnBackPressedDispatcher`.
- `FLAG_KEEP_SCREEN_ON` while monitoring; clear it on pause.
- Lock orientation to the *calibrated* orientation while a calibration exists (store
  it with the calibration). A propped-up phone does not rotate; locking avoids Activity
  recreation mid-meal and keeps the transform stable. Offer "recalibrate" if the user
  wants the other orientation.
- Handle edge-to-edge insets explicitly: `targetSdk 37` means the system enforces
  edge-to-edge (since API 35) and predictive back (since API 36). Use
  `ViewCompat.setOnApplyWindowInsetsListener` on the root and keep controls out of the
  gesture areas; test at API 34 and 37 in Robolectric.
- `onStop` → pause monitoring and close the camera session; `onStart` → return to the
  "ready" state, never auto-resume an alarm.

### 3.3 Pose technology

#### 3.3.1 Library and version

`com.google.mediapipe:tasks-vision:1.0.0` is the current release (metadata lists
0.10.33, 0.10.35, 1.0.0). Bundled models are pinned by SHA-256 to model version 1.
Apache-2.0 for both library and models; a NOTICES screen is still missing (§3.14.3).

**Decision.** Keep. Add the NOTICES screen. Add a lint/test guard that the manifest
requests exactly `[CAMERA]` so a transitive dependency can never sneak `INTERNET` in.

#### 3.3.2 Model choice and `numPoses`

The three bundles share the same input sizes (detector 224², landmarker 256², float16);
they differ in landmarker depth. Google publishes no Android latency numbers. In
VIDEO/LIVE_STREAM modes MediaPipe tracks poses and only re-runs the detector when it
has fewer than `numPoses` tracked — so **`numPoses = 4` with two people at the table
runs the detector every frame**, the slowest path.

**Decision.**
- Default `people` to **2** in onboarding and ask explicitly ("How many seats?").
  `numPoses` = seats (already wired).
- Start with **Full**, as the vision says, but make the in-app FPS/latency readout the
  first thing that works on a phone (§3.8.3) and switch the default to Lite if Full
  cannot hold ≥ 10 processed FPS with the real seat count. Record the numbers in
  `plan.md`. Heavy is not bundled and stays out unless a measured elbow-tracking gain
  justifies +25 MB.

#### 3.3.3 Delegate

`Delegate.GPU` exists in tasks-core 1.0.0 and is a one-line switch. GPU delegates on
Android have device-specific failure modes (context loss on background, unsupported
ops falling back silently).

**Decision.** CPU baseline; expose GPU as a setting labelled "experimental"; measure on
the owner's phone; make it default only if it is both faster and survives
background/foreground cycles in the instrumented test.

#### 3.3.4 Frame path (performance)

**What exists (`FrameAnalyzer.analyze`, lines 34–47):**
1. `image.toBitmap()` — allocates an ARGB bitmap and copies RGBA (3.7 MB at 1280×720).
2. If rotated: `Bitmap.createBitmap(bitmap, …, Matrix().postRotate(deg), true)` —
   second 3.7 MB allocation plus a filtered software rotation. On a phone in the usual
   propped orientation this runs on **every** frame.
3. `BitmapImageBuilder(rotated).build()` — MediaPipe then converts/downsizes internally.

At 15 FPS that is ~110 MB/s of garbage and a full-frame CPU rotation per frame, before
inference starts. The first-frame bitmap is also never recycled explicitly.

**Decision (all verified present in the 1.0.0 AARs):**
- Set a separate `ResolutionSelector` for `ImageAnalysis` targeting **640×480** (or
  640×360 for 16:9). The models downscale to 224/256 anyway; the preview keeps 720p.
  Verify on-device that landmark quality does not measurably change (compare elbow
  jitter at both sizes in the diagnostics screen).
- Copy plane 0 into a **reused direct `ByteBuffer`** (respecting `rowStride`; if
  `rowStride == width*4` a single `put`), close the `ImageProxy`, and build
  `ByteBufferImageBuilder(buffer, w, h, MPImage.IMAGE_FORMAT_RGBA)`. One copy of
  ~1.2 MB, zero allocations in steady state. (Do *not* hand MediaPipe the
  `ImageProxy`'s own buffer: the proxy must be closed promptly for CameraX.)
- Pass rotation as `ImageProcessingOptions.builder().setRotationDegrees(deg)` to
  `detectAsync(image, options, ts)`; no bitmap rotation. Assert once in
  `NativeModelTest` that output coordinates are relative to the rotated image.
- Keep the busy-drop; keep `KEEP_ONLY_LATEST`.

Expected effect: the non-inference part of the frame period drops from tens of ms to a
few ms on a mid-range SoC. This must be *measured* on the phone, and the numbers go in
`plan.md`.

### 3.4 Camera pipeline

#### 3.4.1 Selection

`CameraSelector.requireLensFacing(BACK)` plus an optional Camera2 id filter. Good.

**Improvement.** A dining table from a corner benefits from a **wide** lens. Enumerate
rear cameras with `Camera2CameraInfo` and `LENS_INFO_AVAILABLE_FOCAL_LENGTHS`, label
them "Main", "Wide", "Tele" by relative focal length, and default to the widest rear
lens *if* CameraX exposes it as a separate camera on that phone (many phones hide the
ultra-wide behind a logical camera; then fall back to the main lens). This is a
setup-screen picker, not automatic switching.

#### 3.4.2 Use cases and resolution

Preview 720p FIT_CENTER + ImageAnalysis 720p RGBA today. Per §3.3.4, analysis moves to
~640×480. `COMPATIBLE` implementation mode is set so that `outputTransform` is
available; keep it, but note it costs a TextureView compositing pass — fine.

#### 3.4.3 Latency and gap budgets (finding F1)

- `Monitor.frame` (`Monitor.kt:65`): drop if `now - captured > timing.maxGapMs` (500 ms).
- `TemporalFilter.update` (`TemporalFilter.kt:37–38`): reset to UNKNOWN if the gap
  between consecutive *scored* frames exceeds `maxGapMs` (500 ms).
- `Monitor.tick` (`Monitor.kt:85–89`): rebuild the detector if the last frame is older than
  `maxGapMs`.

All three use the same 500 ms constant for two different things: *freshness* (how old
may a result be when it arrives) and *continuity* (how sparse may the stream be before
evidence is untrustworthy). If inference + delivery takes 500 ms — plausible for Full
× 4 poses on CPU on a 2022 mid-range SoC — every frame is discarded and the app shows
UNKNOWN forever with no explanation. Even at 300 ms latency the effective sample rate
is ~3 FPS, and the classifier's "≥ 3 samples spanning ≥ 400 ms" window barely fills.

**Decision.**
- Freshness bound = max(1500 ms, 3 × median inference period).
- Continuity bound (`maxGapMs`) = max(500 ms, 3 × median inference period), fed to the
  filter by `Monitor` from its running measurement; persist the *floor* in settings.
- The presenter surfaces a "Processing is slow (4 FPS). Switch to the Lite model?"
  banner when the median period exceeds 200 ms, and a hard "too slow to monitor"
  state above 700 ms.
- Add `maxGapMs` to `Settings.encode/decode` (currently dropped: `Settings.kt:78–84`).

#### 3.4.4 Failure handling

`CameraSession` and `FrameAnalyzer` catch `Exception`. Native library load failures
throw `UnsatisfiedLinkError` (an `Error`), and out-of-memory on the bitmap path is an
`Error` too; both currently crash the process instead of producing the actionable
message in `failed()`. Catch `Throwable` *only* around engine creation and frame
conversion, convert to the user-facing "Camera or model unavailable" state, and rethrow
anything that is neither `LinkageError` nor `OutOfMemoryError`.

Also handle: camera stolen by another app (`CameraState.ERROR_CAMERA_IN_USE` via
`CameraInfo.cameraState`), permission revoked while running, and `onStop`. All lead to
*pause with message*, never to a stuck red frame (already the rule in `docs/ux.md`).

### 3.5 Detection and classification

#### 3.5.1 What the rules do

`ArmClassifier.evaluate` (`Pose.kt:51–115`) requires visible shoulder, elbow, wrist and
opposite shoulder (≥ 0.7 min(visibility, presence)); normalizes by shoulder width;
keeps a 1 s history; emits 0.95 if *all* of: elbow signed distance to the table polygon
≥ −0.03, elbow angle 25–155°, elbow ≥ 0.15 below the shoulder, upper arm ≥ 0.15,
forearm ≥ 0.1, wrist not more than 0.5 below the elbow, mean speed < 0.18, max speed
< 0.4, positional variance < 0.015; else 0.05. Score is `null` until ≥ 3 samples
spanning ≥ 400 ms.

**Assessment.** This is a sound Version A: conservative, explainable, body-scale
invariant, and it refuses to guess. Its known weakness is stated in `docs/detection.md`:
from an oblique-down camera, a *hovering* elbow held still above the table projects
inside the polygon and passes every rule. Two other observations:
- The polygon-interior test treats the whole tabletop as one region. What matters is
  the elbow's relation to the **near edge** for that seat: a resting elbow sits *on* the
  edge region in front of the person; an elbow inside the far half of the polygon (a
  reach) is not resting. Distance to the seat's nearest table edge, signed along that
  edge's inward normal, is a stronger single feature than interior distance.
- `wristHeight < 0.5` is generous. Chin-in-hand and "leaning on elbow" both put the
  wrist *above* the elbow in the image (negative `wristHeight`); a forearm laid flat has
  wrist ≈ elbow height. The vision's example "forearms touching table, elbows outside"
  is handled by the polygon test, but "forearm flat *on* the table with the elbow just
  inside the edge" — very common and acceptable at many tables — would alarm. Consider
  requiring wrist *at or above* elbow height for the resting rule (`wristHeight ≤ 0.1`)
  and let real data decide.

**Decision.** Keep Version A as shipped; add the near-edge feature and the
head/nose-to-elbow distance (landmark 0) to `Features` now so they are logged from the
first real session; do **not** tune thresholds by intuition — tune them on recorded
sessions (§3.7). Keep the "score is not a probability" disclaimer everywhere it is
shown.

#### 3.5.2 Seat tracking

`SeatTracker` uses the shoulder midpoint, unique-zone matching when zones exist,
otherwise gated nearest-centre matching with ambiguity rejection and no evidence
inheritance for new arrivals. Good and well tested. Two refinements for the UI stage:
- **Auto-propose seat zones** from the table polygon: one zone per polygon edge,
  extending outward from the edge midpoint (design screen 04). The user drags to adjust.
  This turns FR-06 from "draw four polygons" into "confirm four chips".
- Seats need a stable **colour** (not a name) so the monitor and the warning can say
  "the green seat's left elbow" without identity.

#### 3.5.3 Per-elbow temporal logic

`TemporalFilter` matches vision §11/§26 exactly and is tested for the no-alarm and
sustained cases. Keep. Expose `Timing` in settings under plain labels ("How long before
reminding: 1.0 s", "Sensitivity: conservative/normal") rather than raw thresholds; keep
the raw values in the diagnostics screen.

### 3.6 Alarm behaviour

`AlarmPolicy` produces `BEEP/START/STOP/NONE` independent of detection and always
stops on inactivity. Keep. Rendering decisions:
- **Visual:** static perimeter (default), icon-only, full tint, slow pulse. Pulse period
  ≥ 2 s (WCAG: < 3 flashes/s; we are far below). Perimeter width 12 dp in a soft red
  (`#B3261E` at 85 %), never a flashing white/red.
- **Audio:** `SoundPool` with a bundled two-note chime (generated procedurally at build
  time or a CC0 sample; no runtime synthesis), `USAGE_NOTIFICATION_EVENT` audio
  attributes so it respects Do-Not-Disturb, app-level volume 0–100 mapped to
  `SoundPool.play` volume. Repeat interval and continuous mode exactly as the policy
  emits. Preview button in settings.
- Grace period at session start (exists) and a per-seat **cooldown after a false-alarm
  tap** (new: tapping "False alarm" silences that seat for e.g. 30 s and logs the event —
  this is also the FR-16 "false-alarm correction events" counter).

### 3.7 Data strategy (training mode, evaluation, learning)

**What exists.** `LocalStore.add(sampleRecord(...))` stores, at the instant of a label,
the two arms' state, score and 15-feature vector for one seat, with session id and
`imageRecorded: false`. Bounded to 5000 records, atomic writes, export as raw JSON,
delete. `Monitor` accumulates violations, confidence totals and elapsed time in memory.

**Assessment.** The privacy design is right (no images, explicit opt-in, deletable,
session-scoped). The *granularity* is wrong for every stated purpose:
- To tune Version A thresholds you need the feature time series *around* the label,
  not one vector.
- To train Version B you need enough samples per class; one vector per manual tap will
  never be enough, and "adjacent frames are nearly identical" (vision §22) cuts both
  ways — you need whole sessions to split by session.
- To recompute features after a rule change you need the *landmarks*, not the features.

**Decision — the session log.**
- In training mode (explicit opt-in, per session), write one gzip'd JSONL file per
  session under `filesDir/sessions/<date>-<uuid>.jsonl.gz` containing: a header
  (schema, app version, model, camera id, rotation, analysis size, calibration polygon,
  seat zones, timing), then one line per processed frame: `t`, and per pose 33 ×
  (x, y, c) rounded to 4 decimals, plus per-arm `state`/`score` as computed live, plus
  label events (`NORMAL/LEFT/RIGHT/BOTH/FALSE_ALARM/MISSED`, seat, t) as their own
  lines. ≈ 700 bytes/frame uncompressed → ~5 MB/hour at 10 FPS before gzip, ~1 MB after.
- Statistics mode (separate opt-in) writes only the per-session summary line (duration,
  reminders per seat, false-alarm taps, mean confidence, processed FPS) to
  `stats.jsonl`. This is FR-16, persisted.
- Export via the system share sheet / `ACTION_CREATE_DOCUMENT` (no storage permission).
  Delete per session and delete all. Show sizes.
- **Replay harness** (JVM module, `tools/replay`): `replay <session.jsonl.gz>…
  [--timing …] [--rules …]` re-runs `Detector` over the landmark stream and prints per
  session: reminders, reminders that overlap a `FALSE_ALARM` tap, labelled violations
  detected within 2 s, UNKNOWN fraction, and confusion by label. This makes vision §29
  measurable. Sessions never enter git; the harness is tested on the synthetic demo log.
- **Session split rule** (vision §22) is enforced by the harness: train/validation/test
  are named sets of session files.
- Version B (small logistic regression / GBT on feature windows) becomes a
  `tools/train` script that reads the same logs and emits thresholds or a tiny model
  — *after* the owner has real sessions. Not before.

### 3.8 UI / UX

Full design in [`design/ui-ux.md`](design/ui-ux.md). Decisions that affect architecture:

#### 3.8.1 Framework

Plain Views. `ComponentActivity`, XML layouts (for tooling and RTL/insets support),
one custom `OverlayView` drawing on `Canvas` (skeletons, table polygon, seat zones,
tap handles, perimeter warning). No Compose (dependency weight, slow Robolectric,
`PreviewView` interop overhead for eight screens). No Material Components library;
stock `Switch`, `SeekBar`, `RadioGroup` styled with a ~40-line theme in the ivory/green
palette that `styles.xml` already starts. Fonts: system `sans-serif` with
`sans-serif-medium` for headings (no bundled font; keeps the APK small and respects
user font scale).

#### 3.8.2 Navigation

Single Activity, a `Screen` sealed class in the presenter, `FrameLayout` swap with a
short cross-fade. Predictive back handled through `OnBackPressedDispatcher`. Screens:
Welcome → Position (+ visibility check) → Mark table → Seats (optional) → Monitor
(↔ Warning) with Pause; Settings, Diagnostics, Training/Data and About as
secondary screens from Monitor/Welcome. A synthetic **Demo** runs the Monitor screen
from `SyntheticDemo` with a permanent "Synthetic demo — not a camera" ribbon.

#### 3.8.3 The visibility check (new, answers vision §19)

On the Position screen, once the camera runs, the presenter counts for a 10 s window:
people detected (mode of `poses.size`), and per person whether both elbows and both
wrists are visible (`joint()` non-null) in ≥ 80 % of frames. It shows "2 of 2 people
visible · both elbows ✓ · 12 FPS · 85 ms". *Continue* is enabled only when the count
equals the configured seats and all elbows pass; otherwise it shows the placement tips
from vision §6. This is the Phase-1 experiment, productized, and it is the first thing
to run on the owner's phone.

#### 3.8.4 Accessibility and tone

Every status has words, not just colour; 48 dp targets; content descriptions on
icons; respects font scale (scrollable layouts); no flashing; the child-facing warning
text is short and kind ("Elbows off the table, please"); the adult-facing controls
(false alarm, missed) are behind a small "diagnostics" toggle so a child cannot
dismiss reminders by accident.

#### 3.8.5 Localization

All strings in `res/values/strings.xml` from the first UI commit. Add `values-de/`
(the owner's environment is German-speaking; confirm — §9). Numbers/dates via
`java.text` formatters; JSON exports stay locale-independent (epoch ms, `.` decimal).

### 3.9 Persistence and privacy

- Settings JSON v1 in `SharedPreferences` with `commit()` and a checked result: fine.
  Add `schemaVersion` migration path now (a `when (schema)` with v1 → current) so the
  first change does not become "calibrate again".
- `allowBackup=false` and full extraction-rule exclusion: correct — calibration and
  session logs must not leave the device via backup.
- Manifest: only `CAMERA`; add a Robolectric test asserting
  `packageInfo.requestedPermissions == [CAMERA]`.
- In-app privacy note (Welcome + About): frames are processed in RAM and discarded;
  landmarks are body data and are stored only in training mode, locally, deletable.
- Screenshots for README are taken in **demo mode** and labelled as synthetic; no
  family footage ever enters the repository (already the rule).

### 3.10 Performance, battery, thermal

- Targets (vision §15): ≥ 10 processed FPS, zero backlog, UI unaffected. Backlog and
  UI isolation are architectural facts already; FPS must be measured on the phone with
  the seat count actually used.
- Thermal: register `PowerManager.addThermalStatusListener`; at `THERMAL_STATUS_SEVERE`
  switch to Lite and halve the analysis rate (skip every other frame), show a small
  "phone is hot" notice; at `SHUTDOWN`-adjacent levels pause. Meals are 20–60 min; a
  phone doing continuous inference will warm up.
- Battery: recommend charging during use in the Position screen tip; show battery %
  in diagnostics.
- Screen: keep on during monitoring; a brightness slider is unnecessary — the system
  one exists.

### 3.11 Testing strategy

#### 3.11.1 What exists

19 JVM/Robolectric tests; deterministic fixtures in `GeometryTest.kt` (`table`,
`pose()`); acceptance scenarios from vision §29 in `DetectorTest`; storage corruption
paths; camera session lifecycle with mocks; one instrumented native test. Coverage
98.6 % at HEAD. Solid.

Two fragilities:
- `CameraTest.engineMapsConfidenceAndReleasesInputOnResultErrorAndClose` reaches into
  MediaPipe's AutoValue class with reflection (`getDeclaredMethod("resultListener")`).
  It will break on the next MediaPipe release without any product change. Replace with
  a factory seam that captures the listeners (the engine already takes a `factory`;
  pass a fake `PoseLandmarker` whose `detectAsync` invokes the captured listener).
- `ShadowLandmarker` stubs the native static initializer for Robolectric. Fine, but
  document it in `docs/scripts.md` next to the instrumented test so nobody "fixes" it.

#### 3.11.2 Module split (decision 8)

```
:detection   pure Kotlin JVM   — Geometry, Pose, ArmClassifier, SeatTracker,
                                 TemporalFilter, Detector, Timing   (100 % target)
:core        pure Kotlin JVM   — Settings model + JSON codec (org.json → kotlinx-free
                                 hand-rolled or move to :app), Monitor, AlarmPolicy,
                                 session-log schema, SyntheticDemo
:tools       JVM application   — replay harness, synthetic log generator
:app         Android           — camera, engine, presenter, views, store, activity
```
`org.json` is Android-only (stubbed on the JVM); `:core` should use `kotlinx.serialization`
or a tiny hand-written JSON writer/reader so it runs on the JVM. Kover aggregates across
modules; the 95 % rule stays global and per-module rules are set to 100/95/90/90.

#### 3.11.3 Levels

| Level | Runs where | Covers | Gate |
| --- | --- | --- | --- |
| JVM unit | every commit, CI | `:detection`, `:core`, `:tools`, replay on synthetic log | required |
| Robolectric | every commit, CI | presenter, views, overlay geometry, permissions, store, insets at API 34/37 | required |
| Instrumented | emulator job in CI (API 34 x86_64, cached AVD, `reactivecircus/android-emulator-runner`) | `NativeModelTest` (both models, rotation assertion), demo-mode UI flow via UiAutomator, screenshot capture | required on `main`, allowed to be `workflow_dispatch`/nightly if it proves flaky |
| Docker smoke | every commit, CI | image builds, serves APK, checksum matches artifact | required |
| Physical | owner, per §7 | FPS, visibility, false alarms per meal | recorded in `plan.md`, never fabricated |

#### 3.11.4 Coverage policy

95 % over all product source stays. The module split makes it honest: the pure modules
carry ~100 %, and the Android module is covered by Robolectric with fakes injected
through the existing factory parameters — no reflection, no exclusions of product logic.
Generated code (`R`, `BuildConfig`) is already excluded by Kover defaults.

### 3.12 Build, dependencies, size, signing

#### 3.12.1 Toolchain

Gradle 9.7.1 wrapper, AGP 9.3.2 with built-in Kotlin, Java 17 bytecode on JDK 21,
Kover 0.9.9, Spotless + ktlint 1.7.1, lint with `warningsAsErrors`. All pinned; lint
freshness checks disabled deliberately. Keep. Run the `updateDependencies` workflow
monthly and record it in the ledger.

#### 3.12.2 APK size (decision 9)

```kotlin
android {
    splits { abi { isEnable = true; reset(); include("arm64-v8a"); isUniversalApk = false } }
}
```
for release; debug adds `x86_64` for the emulator (or use a `debug`-only flavour of the
split). Expected release ≈ 11 MB JNI + 15 MB models + ~1 MB code ≈ 27 MB. R8/minify
stays **off** for v1 (MediaPipe needs keep rules; the gain is ~0.5 MB). Consider
shipping only one model bundle per build variant later if size matters.

#### 3.12.3 Signing (decision 10)

Generate `release.jks` once (never committed); store base64 + passwords as GitHub
secrets; `signingConfigs.release` reads them from env in CI and from
`~/.gradle/gradle.properties` locally; `localPipeline.sh` builds unsigned when the env
is absent and says so. Publish the certificate SHA-256 in README. Versioning: keep
`VERSION`/`BUILD_NUMBER`; a GitHub Release is created by a tag workflow with the signed
APK and its checksum attached.

### 3.13 CI/CD and Docker

#### 3.13.1 Pipeline

`localPipeline.sh` = model fetch → shellcheck → py compile → spotless, lint, unit,
kover, assemble → whitespace. CI runs the same script. Keep. Add:
- `actions/cache` for `app/src/main/assets/*.task` keyed on `models/checksums.json`
  (saves 15 MB download and the dependency on storage.googleapis.com per run).
- The emulator job (§3.11.3) as a second job, with the AVD snapshot cached.
- The Docker job: build, smoke test, push to GHCR on `main` (needs
  `permissions: packages: write`, currently `contents: read` only).
- A `release.yml` on `v*` tags: signed APK + checksum + GitHub Release.
- Timeouts on every job and `concurrency` as today.

#### 3.13.2 Docker (decision 11)

`Dockerfile` (≈ 10 lines): `FROM nginx:alpine`, copy `app-release.apk`, `SHA256SUMS`,
`LICENSE`, `NOTICES.md`, `index.html` (install instructions, fingerprint, source link)
into `/usr/share/nginx/html`; OCI labels with version, revision, licence. Smoke test in
the pipeline: `docker build`, `docker run -d -p 8080:80`, `curl -fsS :8080/SHA256SUMS`,
compare to the freshly built APK, `curl -I :8080/app-release.apk` → `200` and
`application/vnd.android.package-archive`. Publish `ghcr.io/marcelpetrick/forkaroundandfindout:<version>` and
`:latest`. `docs/docker.md` documents run/pull. Nothing in the image runs a camera.

### 3.14 Documentation, README, notices

#### 3.14.1 README (brief: badges like myLastFmPlayer/Cullendula, setup, testing, pipeline, docker, usage, real screenshot)

Sections in order: badges (Pipeline, Release, License, Android 14+, Kotlin, Coverage
gate, GHCR image), one-paragraph what/for whom, **Status** (honest: what is validated
synthetically vs on a phone), **Screenshots** (demo mode, labelled), **How it works**
(the pipeline diagram from §3.2.1 and the states), **Camera placement**, **Setup**
(Java 21, SDK, `./localPipeline.sh`, `adb install`), **Usage** (two-tap start, pause,
what a reminder looks like), **Settings**, **Privacy**, **Training mode & data**,
**Testing** (levels, how to run each, emulator), **Pipeline & CI**, **Docker & GHCR**,
**Hardware validation results** (table from §7, filled or "not yet measured"),
**Roadmap** (conditional items), **Licence & notices**, **Author / AI note** as in the
sibling repos. Fix the stale line "The initial foundation is not yet a detector".

#### 3.14.2 Docs directory

`docs/detection.md` (keep, update for image space and new features),
`docs/pose-frameworks.md` (keep), `docs/scripts.md` (keep growing), `docs/ux.md` (fold
into `plan_v2/design/ui-ux.md` or point to it), add `docs/architecture.md` (§3.2 of this
file, kept current), `docs/data.md` (session log schema, export, replay, split rule),
`docs/hardware-validation.md` (§7 protocol + results), `docs/docker.md`.

#### 3.14.3 Notices

`NOTICES.md` at root and an About screen listing MediaPipe Tasks (Apache-2.0), the
Pose Landmarker models (Apache-2.0 per model card — cite the URL and the SHA-256s),
CameraX/AndroidX (Apache-2.0), Kotlin (Apache-2.0). GPLv3 header on every authored
file (already the practice; add to XML layouts and the Dockerfile).

### 3.15 Process

Keep: work on `main`, atomic conventional commits, patch bump every commit, minor bump
for major features, `plan.md` ledger per commit, green before commit/push, push
continuously. Add: the ledger entry template stays ≤ 12 lines; the traceability matrix
moves to `plan_v2.md` §6 and is updated at milestone ends, not per commit. When two
sessions work the tree concurrently (as happened today), each commits only its own
paths and rebases before push.

---

## 4. Findings in the current code (ranked)

Severity: **H** = will produce a wrong product behaviour on a real phone; **M** =
robustness/maintenance; **L** = polish.

| # | Sev | Where (`c32d9a0`) | Finding | Fix |
| --- | --- | --- | --- | --- |
| F1 | H | `Monitor.kt:65`, `TemporalFilter.kt:37–38`, `Monitor.kt:85–89` | One 500 ms constant serves as both freshness and continuity bound; realistic inference latency can exceed it → permanent, unexplained UNKNOWN. | §3.4.3 |
| F2 | H | `CameraSession.kt:137–148` | Landmarks mapped into preview-view space; calibration then depends on layout; metric aspect is the view's, not the image's. | §3.2.3 |
| F3 | H | `LocalStore.kt:67` `sampleRecord` | Training records are single feature snapshots; cannot tune or train. | §3.7 |
| F4 | M | `FrameAnalyzer.kt:34–47` | Two full-frame bitmap allocations + software rotation per frame. | §3.3.4 |
| F5 | M | `CameraSession.kt:71,118,150`, `FrameAnalyzer.kt:48` | `catch (Exception)` misses `UnsatisfiedLinkError`/`OutOfMemoryError` → crash instead of message. | §3.4.4 |
| F6 | M | `CameraTest.kt` (`getDeclaredMethod("resultListener")`) | Reflection into MediaPipe internals; breaks on upgrade. | §3.11.1 |
| F7 | M | `Settings.kt:78–84` | `Timing.maxGapMs` not persisted; decode silently uses default. | add field, schema stays 1 (additive with default) |
| F8 | M | `CameraSession.kt:153` | `analyzer.completed()` runs on the main thread after result delivery; UI jank extends the inference period. | §3.2.2 |
| F9 | M | `app/build.gradle.kts` | Four ABIs shipped; 78 MB release. | §3.12.2 |
| F10 | M | `AndroidManifest.xml`, `MainActivity.kt` | No keep-screen-on, orientation, insets, back handling — must exist before any monitoring UI ships. | §3.2.4 |
| F11 | L | `Pose.kt:108–111` rules | `wristHeight < 0.5` admits a flat forearm with the elbow just inside the edge. | log now, tune on data (§3.5.1) |
| F12 | L | `Monitor.kt:85–89` | Detector rebuilt on any gap → seat identity lost; acceptable, but the presenter should show "re-acquiring". | UI only |
| F13 | L | `README.md` | "The initial foundation is not yet a detector" is stale since 0.1.5. | rewrite per §3.14.1 |
| F14 | L | pipeline | Model download every CI run; no cache. | §3.13.1 |
| F15 | L | `MonitorTest`, `StorageTest` | Statistics are in-memory only; FR-16 asks for optional persistence and false-alarm correction counts. | §3.7 stats.jsonl |

Nothing above contradicts the tests that exist; all 19 pass and CI is green. These are
gaps between "tested logic" and "works on a phone at dinner".

---

## 5. Delivery plan v2

### 5.0 The owner's brief, restated as checkable items

From the original instruction (kept verbatim in spirit):

- [x] read `vision.md`; plan; decide autonomously; keep working — *plan is this file*
- [x] work on `main`; atomic conventional commits; semver; patch bump per commit; minor for features; green before commit/push; push continuously — *practised since 0.0.1*
- [x] `localPipeline.sh` early with test/lint/format/static/coverage/build — *0.0.3*
- [ ] … later e2e + docker checks in the same pipeline — *M6, M8*
- [x] coverage ≥ 95 % — *98.6 % at HEAD; keep*
- [x] GitHub Actions mirrors the local pipeline — *same script*
- [x] small reusable scripts, documented — *`docs/scripts.md`; keep growing*
- [ ] local version works first, then dockerize; Actions build + publish to GHCR — *M8*
- [x] GPLv3, `LICENSE`, headers — *done; extend to XML/Dockerfile*
- [ ] README with badges like the sibling repos; setup, testing, pipeline, docker, usage; **at least one real screenshot** — *M7*
- [ ] unit, integration, e2e tests where appropriate; skip no requirement — *M6*
- [ ] before finishing: `/reviewBranch`, fix, `/githubAbout`, final check against every vision item, full local pipeline, Actions green, Docker image works, everything pushed, tree clean — *M9*
- [ ] stop only when the whole vision is processed and the product works — *M9 + §7 evidence*

### 5.1 Milestones

Each milestone is 2–6 commits. "Evidence" is what must be true and recorded in
`plan.md` before the milestone is ticked.

#### M1 — Foundations for the UI (fixes F1, F2, F5, F7, F8, F10) — version 0.4.x
- [ ] Canonical image-space coordinates end to end; `bounds` derived; `calibrationAspect`
      replaced by `(cameraId, rotation, analysisAspect)` in the calibration record.
- [ ] Freshness/continuity budgets per §3.4.3; `maxGapMs` persisted; presenter-facing
      `ProcessingHealth` (median period, FPS, latency).
- [ ] `Throwable` handling at engine creation/frame conversion.
- [ ] `ComponentActivity`, keep-screen-on, orientation lock with calibration, insets
      handling, back dispatcher; `MonitorPresenter` + `UiState`.
- [ ] Robolectric tests for all of the above at API 34 and 37.
- Evidence: pipeline green; coverage ≥ 95 %; `NativeModelTest` extended with the
  rotation-coordinate assertion and run on the emulator (`connectedDebugAndroidTest`
  output pasted into the ledger).

#### M2 — Frame path performance (F4, F9) — 0.5.x
- [ ] Separate 640×480 analysis resolution; reused `ByteBuffer`; rotation via
      `ImageProcessingOptions`; `completed()` on callback thread.
- [ ] ABI split for release; debug keeps x86_64.
- [ ] Diagnostics readout: processed FPS, inference ms (p50/p95), dropped frames,
      thermal status, battery.
- Evidence: emulator numbers recorded (labelled as emulator, not phone); release APK
  size recorded; **first phone run** of the Position screen with the readout — numbers
  in `plan.md` (this is the earliest point a phone is needed; see §7 step 1).

#### M3 — Setup flow: Welcome, Position + visibility check, Mark table, Seats — 0.6.x
- [ ] Screens 01–04 from the design; permission flow with rationale and settings
      deep-link; camera picker with Main/Wide labels; model picker.
- [ ] Table calibration: four taps with loupe, drag handles, undo/reset, convexity
      validation messages; seats auto-proposed from table edges, drag to adjust,
      overlap rejection (uses `Polygon.overlaps`).
- [ ] Visibility check gating *Continue*.
- Evidence: Robolectric tests of every state transition; instrumented flow on the
  emulator using the synthetic pose source injected through `FrameSource`; screenshots.

#### M4 — Monitor, Warning, Pause, Alarm rendering, Settings — 0.7.x (minor bump: first usable product)
- [ ] Screens 05–07: per-seat chips, overlay, pause, warning perimeter/icon/tint/pulse,
      SoundPool chime with volume/repeat/continuous, grace, false-alarm cooldown.
- [ ] Settings screen with plain-language timing, sensitivity, visual/audio modes,
      volume preview, seats count, camera, model, GPU (experimental), diagnostics
      toggle, training/statistics opt-ins, language.
- [ ] Demo mode from `SyntheticDemo` with the permanent ribbon.
- Evidence: full flow on emulator in demo mode; alarm timings asserted in Robolectric
  with an injected clock; **phone run of a real (adult, consenting) 10-minute test per
  §7 steps 2–3**, results in `plan.md`.

#### M5 — Data: session logs, statistics, export, replay harness (F3, F15) — 0.8.x
- [ ] Session log writer (gzip JSONL) with schema doc; statistics line; export via
      share sheet / SAF; per-session and all delete; sizes shown.
- [ ] Module split (`:detection`, `:core`, `:tools`); `tools/replay` with metrics from
      §3.7; synthetic log fixture; `docs/data.md`.
- [ ] New features logged (near-edge distance, nose–elbow distance).
- Evidence: replay reproduces the synthetic demo's expected reminders exactly; export
  file opened on the desktop; the harness run on at least one real session from §7.

#### M6 — Test completion: emulator job in CI, e2e, guards — 0.9.x
- [ ] `reactivecircus/android-emulator-runner` job with cached AVD running
      `NativeModelTest` + UiAutomator demo flow + screenshot artifact.
- [ ] Permission-set guard test; insets tests; thermal listener test with a fake.
- [ ] Reflection removed from `CameraTest` (F6).
- Evidence: two consecutive green CI runs including the emulator job; runtime recorded.

#### M7 — README, docs, screenshots, notices, About screen (F13) — 0.10.x
- [ ] README per §3.14.1 with badges in the sibling-repo style and the emulator
      screenshot(s) labelled "synthetic demo"; `NOTICES.md`; About screen;
      `docs/architecture.md`, `docs/hardware-validation.md`, `docs/docker.md`.
- Evidence: links checked (`lychee` or a tiny script); badges resolve.

#### M8 — Signing, release workflow, Docker + GHCR (decisions 10, 11) — 0.11.x
- [ ] Keystore in secrets; signed release in CI; fingerprint in README.
- [ ] `Dockerfile`, smoke test in `localPipeline.sh` (skipped with a message when
      Docker is absent, never silently), GHCR publish job, `release.yml` on tags.
- Evidence: `docker pull ghcr.io/…:<version>` on this machine, `curl` of the APK,
  checksum equal to the CI artifact; APK installs on the phone from that download.

#### M9 — Completion gate — 1.0.0 (major bump: product works and is validated)
- [ ] `/reviewBranch` → fix → `/githubAbout` (re-run; the About text should drop
      "work in progress").
- [ ] Vision audit: every row of §6 marked with evidence or explicitly "conditional".
- [ ] §7 protocol completed for at least three real meals; false alarms per meal and
      sustained-violation recall recorded honestly; if the numbers miss the targets,
      1.0.0 is *not* declared — instead the next tuning iteration from replay data is
      planned.
- [ ] Full local pipeline, Actions green, image works, all pushed, tree clean.

### 5.2 Order rationale

M1 before any screen because coordinate space and lifecycle are foundations that every
screen would otherwise bake in. M2 before M3 because the visibility check (M3) needs
the FPS/latency readout and because the first phone contact should happen as early as
possible with the cheapest possible screen. Data (M5) directly after the first usable
product (M4) because the first real meals are the most valuable ones to record. Docker
and signing last among the engineering items because they package what exists.

---

## 6. Vision traceability matrix v2

| Vision | Requirement | Delivery | Milestone | Status at `c32d9a0` |
| --- | --- | --- | --- | --- |
| §1, §5, §34 | detect *supported* elbow, not passing/approaching | rules + motion + hysteresis | done; tune in M5/M9 | synthetic only |
| §2 | architecture pipeline | §3.2 | done | — |
| §3 | Android first; Pi later | Kotlin app; Pi deferred | — | as specified |
| §4 | MediaPipe Pose Landmarker | `MediaPipeEngine`, Full/Lite | done | emulator-verified |
| §6 | camera placement guidance | Position screen tips + visibility check | M3 | missing |
| §7, FR-05 | four-corner calibration, persisted, recalibrate on move | Mark-table screen; image-space storage | M1, M3 | persistence only |
| §8, FR-06 | seat zones via torso centre; no identity | `SeatTracker`; auto-proposed zones | M3 | logic only |
| §9 | features (position, table-relative, arm, torso, motion) | `Features` + near-edge + nose distance | done; M5 | done |
| §10.1, §28 | Version A rules, conservative | `ArmClassifier` | done | done |
| §10.2, §21–22 | Version B learned classifier after real sessions | `tools/train` on session logs, session split | conditional after M9 | not started (by design) |
| §10.3 | crop classifier only if needed | — | conditional | — |
| §11, §26, FR-07, FR-08 | temporal state machine, tests | `TemporalFilter` | done | done |
| §12 | per-elbow state incl. UNKNOWN | done | — | done |
| §13, FR-09, FR-10 | alarm modes, static perimeter default, auto-clear | `AlarmPolicy` + rendering + sound | M4 | policy only |
| FR-01 | selected rear camera | picker | M3 | logic only |
| FR-02, §23 | local only, privacy | manifest guard, notices, in-app note | M6, M7 | done in substance |
| FR-03, FR-04 | 1–4 people, arm landmarks with confidence | done | — | done |
| FR-11 | immediate pause | Monitor screen | M4 | logic only |
| FR-12 | debug overlay incl. FPS/latency | Diagnostics | M2, M4 | missing |
| FR-13 | persisted config incl. camera/model | Settings screen | M4 | persistence done |
| FR-14 | no recording by default | — | — | done |
| FR-15, §21, §27 | training mode, labels, false-alarm/missed feedback | session logs + label buttons | M4, M5 | store only |
| FR-16 | statistics | `stats.jsonl` | M5 | in-memory |
| §15 | ≥ 10 FPS, 720p, zero backlog, uncertainty ⇒ no alarm | measured in M2/§7 | M2, M9 | unmeasured |
| §16–17 | Kotlin owns camera; small events cross to UI | `UiState` | M1 | done in substance |
| §18 | MediaPipe config | 0.6 thresholds, LIVE_STREAM, numPoses = seats, CPU | done; GPU M4 | done |
| §19 | partial-body visibility risk → first PoC question | visibility check | M3, §7 | open |
| §20 | depth optional | — | conditional | — |
| §24 | CI: tests, APK artifact, fixtures | pipeline + emulator job | M6 | partial |
| §25 | project structure | module split | M5 | single module |
| §29 | acceptance table | `DetectorTest` (synthetic) + replay on real logs | M5, M9 | synthetic |
| §30 | not-for-v1 list | respected | — | respected |
| §31–33 | Pi, phases, stack | phases mapped to M1–M9 | — | — |
| §35 | first milestone: live feed, skeletons, table, seats, elbows, confidence, FPS | M2–M4 | — | open |

---

## 7. Hardware validation protocol (cannot be automated; must not be fabricated)

Record every run in `docs/hardware-validation.md` with date, phone model, Android
version, app version, model bundle, seat count, lens, orientation.

1. **First contact (after M2).** Install the debug APK. Position screen only. Record:
   processed FPS and p50/p95 inference ms for Full and Lite with `people` = 1, 2, 4;
   thermal status after 10 minutes; battery drain per 10 minutes. Decide the default
   model from these numbers.
2. **Visibility (vision §19, after M3).** With the intended placement (above head
   height, diagonal, from a corner), 1, 2 and 4 seated adults, long and short sleeves,
   plates and a bowl on the table: for each person, percentage of frames with both
   elbows visible over 60 s. Target ≥ 90 %. If not met, move the phone before touching
   any threshold.
3. **Behaviour (after M4).** Consenting adults act each row of vision §29 three times.
   Record the observed state and the reminder timing. Any false alarm → tap "False
   alarm" (logged).
4. **Meals (after M5).** At least three ordinary meals with training mode on and
   consent from everyone present. Record false alarms per meal, missed sustained
   violations (labelled live with "Missed"), UNKNOWN fraction per seat, FPS over time,
   thermal events. Run the replay harness on the logs; iterate thresholds only via
   replay on held-out sessions.
5. **Targets for 1.0.0.** < 1 false alarm per ordinary meal; > 95 % of violations
   lasting > 2 s reminded within 3 s; no stuck warnings; no crashes; phone not hot
   enough to throttle within 45 minutes.

---

## 8. Beyond the vision — making it a product people like

These are optional, marked by effort; none is required for 1.0.0.

- **Meal summary card** on pause/end (S): duration, reminders per seat, longest clean
  stretch. Positive framing ("41 minutes, one reminder").
- **Seat colours, not names** (S): four fixed pleasant colours; the warning says "the
  blue seat". No identity, still unambiguous at the table.
- **Auto-proposed seat zones** (M, in M3): from the table polygon edges.
- **Loupe while tapping corners** (S, in M3): a magnified circle above the finger.
- **Dim room mode** (S): system dark theme → charcoal/green palette; the perimeter
  warning stays legible.
- **Widest-lens default** (M, in M3): the table fits from a bookshelf.
- **Gentle chime set** (S): two or three chimes to choose from, all soft.
- **"Elbow-free streak" for kids** (S, opt-in): a small counter on the seat chip. Off by
  default; the product should never feel like surveillance.
- **Quick pause gestures** (S): volume-down long-press pauses; a big pause button stays.
- **Recalibration reminder** (S): if the visibility check fails at start, suggest the
  phone moved.
- **Placement illustration** (S): the vision §6 sketch drawn properly on the Position
  screen.
- **Home-screen shortcut "Start dinner"** (S): `ShortcutManager` static shortcut that
  opens directly on Monitor when calibration exists.
- **Two-camera future** (L, deferred): the vision's Pi idea — not for the phone.

---

## 9. Open questions for the owner

1. Second language: add German (`values-de`) alongside English? (Assumed yes; trivial
   to drop.)
2. Default seat count for onboarding: 2? (Assumed 2 from the family context.)
3. Is the phone typically propped in **landscape** (recommended: wider table view) or
   portrait? The designs show both; the default affects the Monitor layout.
4. Do you want the Heavy model as an optional download (not bundled) for the
   benchmark in §7 step 1? (Assumed no for 1.0.0.)
5. Release keystore: generate a new one for this project, or reuse an existing personal
   key? (Plan assumes a new, project-specific one.)
6. Docker registry name: `ghcr.io/marcelpetrick/forkaroundandfindout` (lower-case is
   required by GHCR). OK?

---

## 10. Self-review log

Checked after writing, against the code and the vision:

- Every line reference was checked with `git show c32d9a0:<file> | sed -n` after
  writing. Six were off on the first pass (`CameraSession.kt` 94→118/150, 118–133→
  137–148, 133→153; `Pose.kt` 50–113→51–115, 98–101→108–111; `FrameAnalyzer.kt`
  31→34) and were corrected; the `monitoring/` line count was 359 → 356. The
  uncommitted `CameraSession` edits shift lines by +9 after line 26, which is why
  references are pinned to `c32d9a0`.
- All eleven SVG mockups were validated with `xmllint` and rendered with
  `rsvg-convert`; three text overruns (screens 02, 06, 07) were shortened after
  looking at the renders.
- The three MediaPipe API claims (`detectAsync` with `ImageProcessingOptions`,
  `setRotationDegrees`, `Delegate.GPU`, `ByteBufferImageBuilder`, `IMAGE_FORMAT_RGBA`)
  were verified with `javap` on the cached 1.0.0 AARs, not from memory.
- The "detector runs every frame when fewer than `numPoses` are tracked" statement
  comes from the MediaPipe Android guide ("uses tracking to avoid triggering the
  detection model on every frame"); the exact re-detection rule is not documented, so
  §3.3.2 phrases it as the slowest path *when tracking cannot fill the requested
  count* — the practical advice (set seats to the real number) holds either way.
- The latency-budget finding (F1) is a reasoning result, not a measurement: no phone
  numbers exist yet. It is ranked H because the failure mode is silent. §7 step 1 is
  where it gets confirmed or dismissed.
- I initially reported the local pipeline as passing; the log shows it failed at
  `koverVerifyDebug` (94.8 %) because of the other session's untested `SyntheticDemo`.
  Corrected in §2.1.
- Upstream version claims come from the Google Maven and Maven Central metadata fetched
  on 2026-09-25.
- The APK composition numbers come from `unzip -l` on the locally built release APK.
- Nothing in this plan removes a vision requirement; two vision items are intentionally
  reinterpreted and both were already decided by the owner (Kotlin instead of Flutter;
  Docker as distribution only).
- Wording softened in two places after review: "will" → "may" for the latency
  failure, and the forearm-flat rule (F11) is marked *log now, tune on data* rather than
  a change to make today.
- After the first full draft the tree had moved to `49e660c`; every finding was
  re-checked against that commit with `git show`/`grep` (results in §2.3) instead of
  assuming the review baseline still held. Two things changed materially: M3/M4 are
  mostly delivered, and F2 became more urgent because view-aspect calibration is now
  persisted.
