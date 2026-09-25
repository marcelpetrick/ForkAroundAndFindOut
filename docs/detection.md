# Detection contract

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

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
A single frame never triggers, even with zero configured dwell. Occlusion, invalid
scores, duplicate/backward timestamps or gaps over 500ms clear evidence to UNKNOWN.
The UI must also expire results when no callback arrives; that watchdog belongs to
the monitoring controller, because the detector runs only when frames arrive.

Regression tests cover brief crossing, left/right/both support, correction, reaching,
passing, elbows outside, occlusion, seat departure, reordered detections, ambiguity,
invalid calibration, stale data, hysteresis and cooldown. These synthetic tests prove
software behavior, not household accuracy. A stationary elbow hovering in a plausible
pose can remain visually ambiguous in monocular RGB; real-world evaluation is required.
