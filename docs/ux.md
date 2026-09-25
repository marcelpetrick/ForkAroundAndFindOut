# Native Android workflow

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

## Intended experience

A supervising adult sets up a fixed phone once, then starts each meal in two taps.
The interface uses warm ivory surfaces, deep green controls, large text, rounded
cards, and restrained amber/red status accents. Status always has words, not just
color. Use a scrollable layout on small screens and at large system font sizes.

1. **Welcome / ready:** explain local-only processing, no images saved, and camera
   placement. Primary action: Set up camera. Secondary: Try demo (clearly synthetic).
2. **Position:** show live preview before monitoring; request camera permission only
   on this action. Explain that shoulders, elbows, wrists and table must be visible.
   Offer rear camera selection and Full/Lite model selection in settings.
3. **Mark table:** tap four corners around the table in order. Show numbered taps,
   preview polygon, undo/reset, and reject crossing/degenerate polygons. Keep taps
   aligned to the visible image, never to letterboxed margins.
4. **Seats (optional):** define non-overlapping torso regions, up to four. Explain
   their role in stable assignment and that seat numbers do not identify people.
5. **Monitor:** preview plus per-seat left/right statuses. Primary control becomes
   Pause, always available without opening a menu. Uncertainty reads “Not visible”
   rather than “Good posture”. Setup, settings and demo never produce alarms.
6. **Warning:** static perimeter and a plain-language elbow reminder by default.
   Audio and all alternate visual modes are configurable. Clear automatically on
   correction; silence immediately on pause, backgrounding, lost camera or stale data.
7. **Adult diagnostics:** expandable skeleton, confidence, table, seats, score, FPS
   and latency. False alarm / missed violation feedback is separate from routine use.
8. **Training:** explicit opt-in to locally stored labelled feature samples, including
   session metadata, export and delete controls. No image/video recording required.

## Guardrails

- Changing cameras or image geometry invalidates calibration. Remind users to
  recalibrate after physically moving the phone; do not claim motion detection.
- A demo never activates the physical camera or writes training data automatically.
- Recovery messages say what to do: grant permission, choose an available camera,
  retry the model, or recalibrate. Do not leave a red warning stuck after errors.
- Avoid full-screen flashing. Slow-pulse mode must remain slow and gentle.
- Do not infer validated dining accuracy from a plausible skeleton overlay.

## Verification

Robolectric tests cover stateful screen actions and persistence. Instrumented tests
exercise the installed APK's setup, demo, pause, settings and data-management flow.
Capture README screenshots from the emulator using `adb exec-out screencap -p`;
label synthetic-demo screenshots accurately. Test physical camera visibility and
meal metrics separately using the checklist in the delivery plan.
