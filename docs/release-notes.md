**Fork Around & Find Out 0.11.44** — an offline Android helper that gently reminds a family
to keep elbows off the dinner table. This release completes the plan_v2 product design.

**Status: software complete, real-table validation pending.** Everything below is
implemented and tested automatically (unit, Robolectric, end-to-end on Android 14 and
Android 16 emulators, Docker). How often it gives a false reminder at a real meal has **not
been measured yet**; the protocol is in `docs/hardware-validation.md`. Version 1.0.0 is
reserved for when those targets are met.

## New since 0.10.32
- **Guided setup:** placement picture, people stepper, Wide/Main/Tele lens chips (new setups
  start on the widest lens), and a visibility check that says *why* it does not pass
  (nobody, too few or too many people, arms hidden on the left/middle/right, slow phone).
  **Restart the 10-second check** once everyone has settled.
- **Easier marking:** a loupe magnifies the corner under your finger; **Suggest seats**
  proposes one region per person from the table edges with a live "inside a seat" count.
- **During dinner:** hold **volume-down** to pause without looking; a warm or slow phone is
  offered the **Lite model** in one tap; "nobody visible for a while — has the phone moved?"
  offers to recalibrate; screens fade softly.
- **After dinner:** a friendly **Last meal** card — time, reminders, longest calm stretch and
  reminders per seat colour.
- **Settings** grouped into Reminders, Sensitivity, Camera and model, Data; sensitivity
  presets Conservative / Normal / Responsive; three soft chimes (Bell, Marimba, Glass);
  processor CPU or **GPU (experimental)** with automatic CPU fallback.
- **Platform:** adaptive launcher icon with themed (monochrome) layer, per-app language
  (English/German) on Android 13+, launcher shortcut **Start dinner**.
- **Docs:** plain-language C4 architecture and workflows (`docs/c4-architecture.md`) and a
  laptop emulator guide (`docs/emulator.md`).

## What it does
- Per-elbow detection with MediaPipe Pose Landmarker (Full/Lite, up to 4 people) and a
  conservative, time-filtered rule: reaching, passing food and brief crossings do not
  trigger; hidden elbows are "Not visible", never "good posture".
- Kind reminders: red border (or icon / tint / slow pulse), a card naming the seat by colour,
  optional soft chime; always-visible Pause; "False alarm" rests reminders for 30 s.
- Adult diagnostics, opt-in statistics, opt-in training logs of body landmarks (never
  images) with export/delete, and a desktop replay tool. Synthetic demo, light and
  dim-room themes.

## Privacy
No image or video is ever stored; the app has **no internet permission**, and a test guards
against its return.

## Install
Download `fork-around-and-find-out-0.11.44.apk` below (arm64, Android 14+), verify it with the `.sha256` file,
and install it. Signing certificate SHA-256:
`AA:F8:58:04:C1:50:BB:DF:83:8A:28:49:B1:5A:7A:F3:F8:A9:74:7F:08:3E:A0:82:47:33:7A:10:7A:86:5A:E0`.
Or run the download server: `docker run --rm -p 8080:8080 ghcr.io/marcelpetrick/forkaroundandfindout:latest`.

GPL-3.0-or-later. Third-party notices: `NOTICES.md`.
