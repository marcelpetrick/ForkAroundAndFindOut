<!-- SPDX-FileCopyrightText: 2026 Marcel Petrick -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

**Fork Around & Find Out 0.13.51** — an offline Android helper that gently reminds a family
to keep elbows off the dinner table.

**Status: software complete, real-table validation pending.** Everything below is
implemented and tested automatically (unit, Robolectric, end-to-end on Android 14 and
Android 16 emulators, Docker). How often it gives a false reminder at a real meal has **not
been measured yet**; the protocol is in `docs/hardware-validation.md`. Version 1.0.0 is
reserved for when those targets are met.

## New since 0.11.44

- **A set table:** pots, plates and glasses hide arms. A dish passed in front no longer
  interrupts a reminder, a resting elbow whose hand disappears behind a glass keeps its
  reminder while the elbow stays still (up to 10 s, only after it was clearly seen), and an
  arm that stays hidden is named on the monitor so the pot can be moved. A hand hidden from
  the start is never guessed. Setup now asks for the check at the set table.
- **About screen:** who made it, the GPL notice with its warranty disclaimer and full text,
  the link to this version's source, and all 93 bundled components with version and licence,
  generated from the SBOM, plus the notices those licences require and the licence texts.
- **Licensing:** every file carries SPDX tags (REUSE 3.3, checked in CI); a CycloneDX SBOM is
  attached to this release and served by the Docker image; the GPLv3 review is in
  `docs/licensing.md`.
- **Quality:** new pipeline stages for the lint suite (reuse, ruff, yamllint, xmllint,
  hadolint, actionlint, markdownlint), detekt and the SBOM; rule thresholds and joint indices
  are named in code; dependencies, the Gradle toolchain and CI actions updated and pinned.

## What it does

- Guided setup: placement picture, people and lens choice, a restartable 10-second
  visibility check that explains what is wrong, table marking with a loupe, suggested seats.
- Per-elbow detection with MediaPipe Pose Landmarker (Full/Lite, up to 4 people) and a
  conservative, time-filtered rule; reaching, passing food and brief crossings do not trigger.
- Kind reminders: red border (or icon / tint / slow pulse), a card naming the seat by colour,
  optional soft chime (Bell, Marimba, Glass); Pause (or hold volume-down); a friendly meal
  summary; offline, English and German, light and dim-room themes, synthetic demo.

## Privacy

No image or video is ever stored; the app has **no internet permission**, and a test guards
against its return.

## Install

Download `fork-around-and-find-out-0.13.51.apk` below (arm64, Android 14+), verify it with the
`.sha256` file, and install it. Signing certificate SHA-256:
`AA:F8:58:04:C1:50:BB:DF:83:8A:28:49:B1:5A:7A:F3:F8:A9:74:7F:08:3E:A0:82:47:33:7A:10:7A:86:5A:E0`.
The SBOM is `fork-around-and-find-out-0.13.51.cdx.json`. Or run the download server:
`docker run --rm -p 8080:8080 ghcr.io/marcelpetrick/forkaroundandfindout:latest`.

GPL-3.0-or-later. Complete source of this version: tag `v0.13.51`. Third-party notices: `NOTICES.md`.
