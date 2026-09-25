# UI / UX design — Fork Around & Find Out

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

Companion to [`../plan_v2.md`](../plan_v2.md) §3.8. Mockups are the SVG files in this
directory; they are wireframe-fidelity with final colour and copy, drawn at 390×844
(portrait) and 844×390 (landscape). Everything here is implementable with plain
Android Views, one custom `OverlayView`, and the theme tokens below.

## 1. Principles

1. **Two taps to dinner.** Once set up, the app opens on *Ready* and one tap starts
   monitoring. Pause is always one tap, always visible.
2. **Kind, not punitive.** The reminder is a nudge for a child at a family table. Short
   words, soft colour, a chime not a siren. The app never scores people.
3. **Words for every state.** Colour is never the only carrier. "Not visible" is not
   "good".
4. **Uncertainty looks calm.** UNKNOWN is a neutral grey chip, never amber.
5. **Adults own the controls.** Diagnostics, false-alarm feedback, settings and data
   sit behind a small toggle so they are not dismissed by accident from the table.
6. **Honest about what it is.** The demo is labelled synthetic on every frame; the
   status screen says what has and has not been validated.
7. **Works on the phone as it stands.** Landscape-first for monitoring (the table is
   wide), portrait fine for setup; large touch targets; respects font scaling.

## 2. Design tokens

### Colour (light, "warm ivory")

| Token | Hex | Use |
| --- | --- | --- |
| `bg` | `#F7F2E8` | screen background |
| `card` | `#FFFDF9` | cards, sheets |
| `line` | `#E4DCCB` | hairlines, dividers |
| `ink` | `#1F2A26` | primary text |
| `muted` | `#6B7570` | secondary text, icons |
| `green` | `#286552` | primary actions, CLEAR, brand (already `colorAccent`) |
| `greenSoft` | `#DCEBE3` | CLEAR chip background, success surfaces |
| `amber` | `#C9822B` | SUSPECT, warnings that are not alarms |
| `amberSoft` | `#F6E7D2` | SUSPECT chip background |
| `red` | `#B3261E` | VIOLATION perimeter, reminder card accent |
| `redSoft` | `#F4DAD6` | reminder card background |
| `grey` | `#8A918D` | UNKNOWN chip text |
| `greySoft` | `#ECEAE3` | UNKNOWN chip background |
| seat 1 `#286552` · seat 2 `#3E6FA8` · seat 3 `#C9822B` · seat 4 `#7A5C9E` | | seat identity colours (never names) |

### Colour (dark, "dim room")

`bg #17201C`, `card #1F2A26`, `line #2E3B35`, `ink #F1EDE3`, `muted #A5AFA9`,
`green #6FB597`, `greenSoft #24463A`, `amber #E2A25A`, `amberSoft #4A3620`,
`red #E5766B`, `redSoft #4A2522`, `greySoft #2A332F`. Follows the system setting;
no in-app toggle.

### Type

System `sans-serif`; headings `sans-serif-medium`. Scale (sp): display 34, title 24,
heading 20, body 17, secondary 15, caption 13. Line height 1.3. Minimum body 17 sp
because the phone is read from across a table.

### Spacing and shape

8-dp grid. Screen gutter 20 dp. Card radius 20 dp, chip radius 999 dp, button radius
16 dp. Buttons 56 dp tall (primary) / 48 dp (secondary). Touch targets ≥ 48 dp.

### Motion

Cross-fade 180 ms between screens. Chip state change: colour tween 250 ms. Reminder
perimeter fades in over 400 ms and out over 600 ms — no pop, no flash. Pulse mode:
opacity 0.6↔1.0 over 2.4 s, ease-in-out.

## 3. Navigation

```
 Welcome/Ready ──▶ Position ──▶ Mark table ──▶ Seats (optional) ──▶ Monitor ◀──▶ Reminder
      │   │                                                             │
      │   └── Try demo ──────────────────────────────────────────▶ Monitor (demo ribbon)
      │
      ├── Settings ─── Camera & model · Reminders · Sensitivity · Data · About
      └── Data & training ─── sessions list · export · delete
                                                                   Monitor ── ⚙ diagnostics toggle
```
See [`10-flow.svg`](10-flow.svg). Back always goes one step left; from Monitor, back
asks "Stop monitoring?" only while a session is active.

## 4. Screens

Each screen lists: purpose · layout · copy · states · what the presenter exposes.

### 01 Welcome / Ready — [`01-welcome.svg`](01-welcome.svg)

- Purpose: first run explains privacy and placement; later runs are the *Ready* screen.
- Layout: wordmark; three short privacy lines with icons (on-device · no images saved ·
  no internet); primary **Set up camera** (first run) or **Start dinner** (when a
  calibration exists, showing "Calibrated 12 Sep · 2 seats · Main lens"); secondary
  **Try the demo**; footer links Settings · Data · About.
- Copy: "Everything happens on this phone. Camera images are used in memory and
  discarded. Nothing is uploaded."
- States: first-run / ready / calibration-invalid ("Camera or orientation changed —
  please mark the table again").

### 02 Position + visibility check — [`02-position.svg`](02-position.svg)

- Purpose: put the phone where shoulders, elbows, wrists and the tabletop are all
  visible; answers vision §19 in-product.
- Layout: live preview (letterboxed, FIT_CENTER) with faint skeleton overlay; a tip
  card ("Above head height, from a corner, looking down at an angle"); the **visibility
  card**: "2 of 2 people · both elbows visible ✓ · 12 FPS · 85 ms" with a 10-second
  progress ring; seat-count stepper ("Seats: 2"); lens chips (Main / Wide); primary
  **Continue** (disabled until the check passes); link "Why is it not passing?".
- Permission: requested on entering this screen with a one-line rationale; denied →
  card with **Open settings**.
- States: waiting for camera · checking (ring) · passed (green) · failing (amber, with
  the specific reason: "Seat on the right: elbows hidden") · too slow ("Processing is
  slow (4 FPS). Switch to Lite?").

### 03 Mark table — [`03-mark-table.svg`](03-mark-table.svg)

- Purpose: FR-05, four corners in order.
- Layout: preview; numbered handles 1–4 as the user taps; translucent green
  "tablecloth" polygon once four exist; a **loupe** (magnified circle) above the finger
  while a handle is pressed; bottom bar **Undo** · **Reset** · **Continue**.
- Validation copy: "Corners must not cross" / "Tap inside the picture".
- States: 0–3 corners (hint "Tap corner 3") · 4 corners valid · invalid (red outline
  + message).

### 04 Seats — [`04-seats.svg`](04-seats.svg)

- Purpose: FR-06, optional; auto-proposed from the table edges.
- Layout: preview with the table polygon; one rounded seat region per used edge in its
  seat colour with a numbered chip; drag to move/resize; toggle per seat; "Seats help
  keep the same chair as the same seat — they are not people". Bottom **Skip** ·
  **Continue**.
- Validation: overlapping regions show amber and the message "Seats must not overlap".

### 05 Monitor — [`05-monitor.svg`](05-monitor.svg), landscape [`05b-monitor-landscape.svg`](05b-monitor-landscape.svg)

- Purpose: FR-07/11/12; the screen that runs for the whole meal.
- Layout (portrait): preview on top with an unobtrusive overlay (table outline only,
  unless diagnostics is on); a row of **seat cards** each with two chips
  "L ✓ Clear" / "R — Not visible"; session line "12 min · 1 reminder"; a large
  **Pause** button (full width, 56 dp); a small ⚙ opens diagnostics. Landscape: preview
  left (≈ 62 %), seat cards + pause on the right.
- Chips: CLEAR greenSoft "Clear" · SUSPECT amberSoft "Checking…" · VIOLATION redSoft
  "Elbow" · UNKNOWN greySoft "Not visible".
- Paused state: preview dims, chips grey, button becomes **Resume**; a "Paused" pill.
- Demo: a permanent top ribbon "Synthetic demo — not a camera".
- Health banner (only when needed): "Processing is slow (4 FPS) — Lite model?" ·
  "Phone is warm — switched to Lite" · "Camera lost — Retry".

### 06 Reminder — [`06-warning.svg`](06-warning.svg)

- Purpose: FR-09/10; the default static perimeter.
- Layout: 12 dp red perimeter inside the screen edge; the offending seat card gets the
  red chip and a slightly larger reminder card in the centre: seat colour dot,
  "Elbows off the table, please", "Blue seat · left". Pause stays. Adult row (only when
  diagnostics is on): **False alarm** · **Missed one**.
- Modes: perimeter (default) · icon only (a red badge on the seat card) · full tint
  (redSoft over the preview) · slow pulse (perimeter 0.6↔1.0 opacity over 2.4 s).
- Clears automatically: perimeter fades out; the card turns greenSoft "Thank you" for
  2 s then disappears.

### 07 Settings — [`07-settings.svg`](07-settings.svg)

- Groups: **Reminders** (visual mode radio, sound: off/once/repeat every N s/continuous,
  volume slider with ▶ preview, grace period at start), **Sensitivity** (Conservative /
  Normal, "Remind after 1.0 s", "Clear after 0.5 s"; raw values in Diagnostics),
  **Camera & model** (lens, model Full/Lite, GPU experimental), **Seats** (count,
  re-mark table, edit seats), **Data** (statistics on/off, training mode on/off, open
  Data screen), **Language**, **About** (version, licences, privacy).
- Every row has a one-line explanation under it.

### 08 Diagnostics — [`08-diagnostics.svg`](08-diagnostics.svg)

- Overlay on Monitor when the ⚙ toggle is on: skeletons with joint dots sized by
  confidence, table polygon, seat zones, per-arm score and state text next to the
  elbow, and a translucent stats card: FPS, inference p50/p95 ms, dropped frames,
  thermal, battery, model, resolution, and the raw timing values. The adult feedback
  row appears here too.

### 09 Data & training — [`09-training-data.svg`](09-training-data.svg)

- Purpose: FR-15/16, §21, §23, §27.
- Layout: explanation card ("Training mode stores body landmark positions — never
  pictures — so the reminder rules can be improved. Stored only on this phone."); toggle
  cards for statistics and training; the label bar that appears on Monitor when training
  is on (**Normal · Left · Right · Both**); list of sessions with date, duration, size,
  labels count; per-row **Export** · **Delete**; **Delete all**.

## 5. Component inventory (Views)

`PrimaryButton`, `SecondaryButton` (styled `Button`), `StateChip` (`TextView` with
background drawable per state), `SeatCard` (`LinearLayout`), `OverlayView` (custom;
draws skeletons, polygon, handles, seat regions, perimeter; takes an image→view
`Matrix`), `Loupe` (small custom View), `Ribbon`, `HealthBanner`, `StatsCard`,
`SettingRow` (title + explanation + control). All colours from `res/values/colors.xml`
(light) and `res/values-night/colors.xml` (dark).

## 6. Accessibility checklist

- Text contrast ≥ 4.5:1 on all chips (checked for the pairs above).
- Every icon-only control has `contentDescription`; chips announce "Left elbow: clear".
- Font scale 200 %: setup screens scroll; Monitor keeps the pause button visible.
- No content flashes faster than 0.5 Hz; pulse mode is 0.42 Hz.
- Reminder has sound *and* visual; either can be turned off independently.
- Touch targets ≥ 48 dp; handles on Mark-table have a 44 dp hit radius.

## 7. Copy sheet (en / de proposals)

| Key | en | de |
| --- | --- | --- |
| app_name | Fork Around & Find Out | Fork Around & Find Out |
| welcome_privacy | Everything happens on this phone. Nothing is uploaded, no pictures are saved. | Alles passiert auf diesem Telefon. Nichts wird hochgeladen, keine Bilder gespeichert. |
| start_dinner | Start dinner | Essen starten |
| pause | Pause | Pause |
| resume | Resume | Weiter |
| state_clear | Clear | Frei |
| state_checking | Checking… | Prüfe… |
| state_elbow | Elbow | Ellbogen |
| state_unknown | Not visible | Nicht sichtbar |
| reminder_text | Elbows off the table, please | Ellbogen vom Tisch, bitte |
| false_alarm | False alarm | Fehlalarm |
| missed | Missed one | Übersehen |
| demo_ribbon | Synthetic demo — not a camera | Synthetische Demo — keine Kamera |
| slow_banner | Processing is slow (%1$d FPS). Switch to the Lite model? | Verarbeitung ist langsam (%1$d FPS). Zum Lite-Modell wechseln? |

## 8. Mapping to milestones

Screens 01–04 → M3; 05, 06, 07 → M4; 08 → M2 (stats) + M4 (overlay); 09 → M5;
dark theme and dim-room polish → M7 with the screenshots.
