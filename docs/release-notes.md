First public release of **Fork Around & Find Out** — an offline Android helper that
gently reminds a family to keep elbows off the dinner table.

**Status: software complete, real-table validation pending.** Everything below is
implemented and tested automatically (unit, Robolectric, end-to-end on an Android 14
emulator camera, Docker). How often it gives a false reminder at a real meal has **not been
measured yet**; the protocol is in `docs/hardware-validation.md`. Version 1.0.0 is reserved
for when those targets are met.

## What it does
- Camera setup with a 10-second visibility check, four-corner table marking (drag to
  adjust), optional seat regions, persisted calibration in camera-image space.
- Per-elbow detection with MediaPipe Pose Landmarker (Full/Lite, up to 4 people) and a
  conservative, time-filtered rule: reaching, passing food and brief crossings do not
  trigger; hidden elbows are "Not visible", never "good posture".
- Kind reminders: red border (or icon / tint / slow pulse), a card naming the seat by colour,
  optional soft chime (once / repeat / continuous, respects Do-Not-Disturb); always-visible
  Pause; "False alarm" rests reminders for 30 s.
- Adult diagnostics (FPS, latency, scores), opt-in statistics, opt-in training logs of body
  landmarks (never images) with export/delete, and a desktop replay tool.
- English and German, light and dim-room themes, synthetic demo.

## Privacy
No image or video is ever stored; the app has **no internet permission**. Note: builds
before 0.9.26 (including earlier GHCR images) still carried the network permission pulled
in by MediaPipe's telemetry transport, which could send library usage events. This release
blocks it and a test guards against its return.

## Install
Download the APK below (arm64, Android 14+), verify it with the `.sha256` file, and install it.
Signing certificate SHA-256:
`AA:F8:58:04:C1:50:BB:DF:83:8A:28:49:B1:5A:7A:F3:F8:A9:74:7F:08:3E:A0:82:47:33:7A:10:7A:86:5A:E0`.
Or run the download server: `docker run --rm -p 8080:8080 ghcr.io/marcelpetrick/forkaroundandfindout:latest`.

GPL-3.0-or-later. Third-party notices: `NOTICES.md`.
