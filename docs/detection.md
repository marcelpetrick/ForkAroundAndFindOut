<!-- SPDX-FileCopyrightText: 2026 Marcel Petrick -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Detection contract

`detection/` is pure Kotlin and has no Android or MediaPipe dependency. Inputs are
33 landmarks normalized to the upright (rotated) camera analysis image — the one
canonical coordinate system shared by landmarks, table, seats and exports — with
joint confidence; outputs are per-seat,
per-arm evidence and UNKNOWN/CLEAR/SUSPECT/VIOLATION states. The caller supplies
monotonic capture timestamps and the upright image's width/height ratio.

Four ordered corners define a convex table polygon. Self-crossing, collinear and
out-of-image calibration is rejected. Signed distance is positive inside and uses
aspect-corrected coordinates. Geometry/motion is normalized by shoulder width.

Seats use visible shoulder centers consistently because hips are often hidden by
the table. Configured polygons require a unique pose and unique region match.
Without regions, nearest-center association has distance/ambiguity gates; missing
or ambiguous seats lose their motion and temporal evidence. No face identity is used.

Each arm needs visible shoulder, elbow and wrist (>=0.7 confidence). A rolling
second provides velocity and variance; at least three observations spanning 400ms
are required. A stationary elbow near the table with bent, downward upper-arm
geometry produces a conservative 0.95 evidence score; unsupported geometry is 0.05.
Scores are rules, **not calibrated physical-contact probabilities**. Feature vectors
also expose wrist distance, limb lengths, angles, torso/shoulder inclination and
confidence for diagnostics and future labelled training.

Default trigger is >=0.75 for 1000ms; clear is <=0.35 for 500ms; cooldown is 1500ms.
Freshness and continuity are separate budgets: a result may be up to
max(1500 ms, 3 × median frame period) old on arrival, and the stream may have gaps of
up to max(configured floor 500 ms, 3 × median period). Above a 200 ms median period the
UI reports slow processing; above 700 ms it reports "too slow" and never warns.
A single frame never triggers, even with zero configured dwell. Invalid scores,
duplicate/backward timestamps or gaps over 500ms clear evidence to UNKNOWN at once.

## A set table: pots, plates, glasses

A set table hides arms. Missing evidence still never *starts* a reminder, but three rules
keep the app useful at a real dinner:

- **Brief-occlusion hold** (`Timing.holdMs`, default 600 ms): when an arm's joints are
  hidden in otherwise fresh frames — a dish passed in front — a *running* VIOLATION stays
  on instead of flickering off and rebuilding. It never holds SUSPECT, never bridges a
  frame gap, and never holds for missing history (sparse frames on a slow phone).
- **Hidden-hand bridge** (`ArmClassifier.BRIDGE_MS` 10 s, `BRIDGE_RADIUS` 0.15 shoulder
  widths): after the arm was fully seen resting (shoulder, elbow and wrist), only the wrist
  may disappear — behind a glass or a pot — while the elbow stays still within the radius.
  The rest continues for at most 10 s after it was last fully seen. An elbow that moves, a
  hidden elbow or shoulder, or a hand hidden *from the start* gives no evidence: the latter
  would need a learned rule trained on real labelled sessions (training mode + replay).
- **Occlusion hint**: when an arm of a person in view is hidden in ≥ 70 % of recent frames
  (exponential average, τ = 10 s) for at least 20 s, the monitor names the seat colour and
  side and suggests moving the pot/bottle or the phone.

Setup asks for the visibility check to run at the set table. Session logs record `holdMs`;
logs from before 0.12.47 replay with a hold of 0 ms, as they were decided live.
The UI must also expire results when no callback arrives; that watchdog belongs to
the monitoring controller, because the detector runs only when frames arrive.

Regression tests cover brief crossing, left/right/both support, correction, reaching,
passing, elbows outside, occlusion, seat departure, reordered detections, ambiguity,
invalid calibration, stale data, hysteresis and cooldown. These synthetic tests prove
software behavior, not household accuracy. A stationary elbow hovering in a plausible
pose can remain visually ambiguous in monocular RGB; real-world evaluation is required.
