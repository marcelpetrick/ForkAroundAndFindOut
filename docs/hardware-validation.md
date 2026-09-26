<!-- SPDX-FileCopyrightText: 2026 Marcel Petrick -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Hardware validation

Automated tests prove software behaviour on synthetic data and on an emulator camera.
They cannot prove how the app behaves at a real family table. That evidence must come
from a real phone and consenting people, and **must never be fabricated**. This page is
the protocol (from `plan_v2/plan_v2.md` §7) and the place to record results.

## Evidence so far

| Date | Device | What | Result |
| --- | --- | --- | --- |
| 2026-09-26 | Android 14 emulator (x86_64 host CPU), emulated rear camera | Full model, analysis 640×360 | 27.9 FPS, latency p50 23 ms / p95 35 ms (not phone evidence) |
| 2026-09-26 | GitHub Actions emulator (API 34, pixel_6 profile) | E2E flows | about 5 FPS; the app correctly reports "Processing is slow" |
| — | Real phone | Steps 1–5 below | **Not yet measured** |

## Protocol

Record every run with date, phone model, Android version, app version, model (Full/Lite),
seat count, lens and orientation.

1. **First contact.** Install the release APK (see the Docker page or the GitHub
   release). Open *Set up camera*. Record FPS and latency p50/p95 from adult diagnostics for
   Full and Lite with 1, 2 and 4 people; thermal status after 10 minutes; battery drain per
   10 minutes. Choose the default model from these numbers.
2. **Visibility (vision §19).** Intended placement: above head height, diagonal, from a
   corner. With 1, 2 and 4 seated adults, long and short sleeves, and the table **set as
   for dinner** (plates, a tall pot, bottles, glasses): does the visibility check pass? Target: every elbow visible in ≥ 90 % of frames.
   If not met, move the phone before touching any threshold.
3. **Behaviour (vision §29).** Consenting adults act each acceptance row three times
   (knife and fork, forearms on the edge, hands below the table, reaching across, passing a
   plate, brief crossing < 0.5 s, left/right/both elbows resting > 2 s, correction, hidden
   elbow, leaving the seat, someone walking past, a bowl occluding an arm, a dish passed in
   front of a resting elbow (the reminder must not flicker), a resting elbow with the hand
   behind a glass or pot (the reminder must continue ≤ 10 s), a pot hiding one arm for a
   minute (the monitor must name that arm)). Record the state shown and the reminder timing. Tap *False alarm* for every wrong reminder.
4. **Meals.** At least three ordinary meals with training mode on and consent from
   everyone present. Record false alarms per meal, missed sustained violations (tap
   *Missed violation*), UNKNOWN fraction per seat, FPS over time and thermal events. Export
   the logs and run `scripts/replay.sh replay` on them; change thresholds only by replaying
   held-out sessions.
5. **Targets for 1.0.0.** < 1 false alarm per ordinary meal; > 95 % of violations lasting
   > 2 s reminded within 3 s; no stuck warnings; no crashes; the phone does not throttle
   within 45 minutes.

Version 1.0.0 is declared only when these targets are met and recorded here.

## Result template

| Date | Phone / Android | App | Model | People | Lens / orientation | Step | Measurements | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | | |
