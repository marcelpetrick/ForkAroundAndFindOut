<!-- SPDX-FileCopyrightText: 2026 Marcel Petrick -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Native Android workflow

## Intended experience

A supervising adult sets up a fixed phone once, then starts each meal in two taps.
The interface follows `plan_v2/design/ui-ux.md`: warm ivory tokens (`values/colors.xml`)
and a "dim room" dark variant that follows the system setting (`values-night/`), deep
green controls, large text, rounded cards with hairlines, restrained amber/red accents.
Status always has words, not just colour (state chips "Left: Clear"). Layouts scroll on
small screens and at large font sizes. System configuration changes (dark mode, font
scale, locale, rotation outside camera screens) re-render in place and never end a
running meal session. Light-mode amber/grey chip text is darker than the design sheet
(#8A5A1D, #5F6662) to meet its own 4.5:1 contrast rule.

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
5. **Monitor:** preview; Pause directly under the status line (always visible, one tap,
   however many seats); one card per seat in its identity colour (green/blue/orange/
   purple — colours, never names) with a chip per elbow; a session line "Session 12:03 ·
   reminders: 1". Paused dims the preview. Back asks "Stop monitoring?". Uncertainty reads “Not visible”
   rather than “Good posture”. Setup and settings never produce alarms (Settings has an explicit "Test sound" preview); the demo previews the visual warning only, never sound or storage.
6. **Warning:** static perimeter (fades in 400 ms, out 600 ms; pulse 0.6↔1.0 over 2.4 s)
   and a centred card "Elbows off the table, please · Blue seat · left" by default; after
   a real correction a short "Thank you!" card.
   Audio (a soft generated two-note chime, respecting Do-Not-Disturb) and all alternate
   visual modes are configurable. "False alarm" silences and rests reminders for 30 s. Clear automatically on
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
