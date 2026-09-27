<!-- SPDX-FileCopyrightText: 2026 Marcel Petrick -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Native Android workflow

## Intended experience

A supervising adult sets up a fixed phone once. After that, each meal starts in one or two
taps, or with the launcher shortcut *Start dinner*.

**Look and feel.** The interface follows `plan_v2/design/ui-ux.md`:

- warm ivory colour tokens (`values/colors.xml`) and a "dim room" dark variant that follows
  the system setting (`values-night/`);
- deep green controls, large text, rounded cards with hairlines, and restrained amber and
  red accents.

Light-mode amber and grey chip text is darker than the design sheet (#8A5A1D, #5F6662), to
meet its own 4.5:1 contrast rule.

**Behaviour.**

- Status always has words, not just colour (state chips such as "Left: Clear").
- Layouts scroll on small screens and at large font sizes.
- Screens fade in over 180 ms.
- Dark mode, font scale, locale and rotation outside the camera screens re-render in place
  and never end a running meal.
- English and German, with a per-app language on Android 13+.

Each screen is its own class (`WelcomeScreen`, `SetupScreen`, `MonitorScreen`,
`SettingsScreen`, `DataScreen`, `AboutScreen`). `MainActivity` hosts them: it owns the
lifecycle, navigation, the camera and the frame loop.

1. **Welcome / ready.** It explains local-only processing, that no images are saved, and
   where to place the camera.
   - Actions: Set up camera, or Start dinner once calibrated, and Try demo (clearly
     synthetic).
   - After a meal, a *Last meal* card shows the time, the reminders, the longest calm
     stretch and the reminders per seat colour.
   - A refused camera permission shows the reason and *Open app settings*.
2. **Position.** The live preview comes with a placement picture, a *People at the table*
   stepper and Wide/Main/Tele lens chips. A new setup starts on the widest lens.
   - The copy asks for the table to be **set as for dinner**.
   - A ten-second visibility check gates *Mark table*. Setup looks for up to four people.
   - The check says why it does not pass: nobody, too few, too many, arms hidden on the
     left, middle or right of the picture, or slow processing (suggesting Lite).
   - *Restart the 10-second check* starts it over once everyone has settled.
   - *Mark table without the check* stays available.
3. **Mark table.** Tap the four corners in order.
   - Numbered handles, the outline, undo and reset.
   - Drag a corner to adjust it; a loupe magnifies the spot under the finger.
   - Crossing or degenerate shapes are rejected.
   - Taps stay aligned to the visible image, never to letterboxed margins.
4. **Seats (optional).** Non-overlapping regions, up to four.
   - *Suggest seats* proposes one region per person from the table edges.
   - A live line counts the people inside a seat.
   - Seats keep assignments stable; seat numbers and colours never identify people.
5. **Monitor.** The preview comes first. **Pause**, then Stop and the adult toggle, come
   directly under the title and never move.
   - Everything whose size changes sits below those controls: the status line, the
     *Recalibrate camera* hint ("nobody visible for a while"), the hidden-arm hint ("Blue
     seat · the left arm is often hidden"), the Lite banner for a warm or slow phone, the
     adult tools, and one card per seat.
   - Seat cards use the seat's identity colour (green, blue, orange, purple: colours, never
     names) with a chip per elbow.
   - A session line reads "Session 12:03 · reminders: 1".
   - Holding volume-down pauses without looking at the phone. Paused dims the preview.
   - Back asks "Stop monitoring?".
   - Uncertainty reads "Not visible", never "Good posture".
   - Setup and settings never produce alarms. Settings has an explicit *Test sound*. The
     demo previews the visual warning only, never sound or storage.
6. **Warning.**
   - The default is a static red perimeter (fades in over 400 ms, out over 600 ms; the
     pulse mode runs 0.6↔1.0 over 2.4 s).
   - A centred card reads "Elbows off the table, please · Blue seat · left". After a real
     correction, a short "Thank you!" card appears.
   - A dish passed in front of a resting elbow does not interrupt the reminder.
   - Sound is optional: one of three soft generated chimes (Bell, Marimba, Glass), played
     once, repeated or continuous. It respects Do-Not-Disturb.
   - "False alarm" silences the reminder and rests reminders for 30 s.
   - A reminder clears automatically on correction, and silences at once on pause,
     backgrounding, a lost camera or stale data.
7. **Settings.** Grouped into four sections, each row with a one-line explanation:
   - **Reminders:** visual mode, sound, chime, volume, repeat, start grace.
   - **Sensitivity:** Conservative / Normal / Responsive presets above the raw values.
   - **Camera and model:** people, lens, Full/Lite, CPU/GPU (experimental).
   - **Data:** overlay, statistics, local data.
8. **Adult diagnostics.** An expandable panel with the skeleton, confidence, table, seats,
   score, FPS, latency, dropped frames, processor, thermal state and battery. *False alarm*
   and *Missed violation* feedback is kept separate from routine use.
9. **Training.** An explicit opt-in to local session logs of body landmarks with labels,
   never images. Export and delete are on the *Local data* screen.
10. **About.** Who made the app and the copyright. The GPL notice with its warranty
    disclaimer and the full GPL text. The source of this exact version. Every bundled
    component with version and licence, from the SBOM. The licence texts and the notices
    those licences require. Long texts are paged.

## Guardrails

- Changing cameras or image geometry invalidates calibration. Remind users to recalibrate
  after physically moving the phone ("nobody visible for a while" is a hint, not motion
  detection).
- A demo never activates the physical camera or writes training data automatically.
- Recovery messages say what to do: grant permission, choose an available camera, retry
  the model, switch to Lite, or recalibrate. A red warning never stays stuck after errors.
- Controls never move under a finger; text that grows goes below them.
- Avoid full-screen flashing. Slow-pulse mode must remain slow and gentle.
- Do not infer validated dining accuracy from a plausible skeleton overlay.

## Verification

- Robolectric tests cover stateful screen actions, persistence, the About screen and the
  shortcut.
- Instrumented tests exercise the installed APK's setup, demo, pause, settings and
  data-management flows.
- `scripts/screenshots.sh` captures the README screenshots from an emulator (welcome, demo,
  setup, settings, About, dark, landscape). Synthetic-demo screenshots are labelled as such.
- Physical camera visibility and meal metrics are tested separately with
  [the hardware protocol](hardware-validation.md).
