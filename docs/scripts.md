# Developer scripts

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

Run scripts from the repository root. Java 21, Android SDK (`ANDROID_HOME`, default
`~/Android/Sdk`), Python 3 and ShellCheck must be installed. Gradle is pinned via the
wrapper.

| Script | Purpose and usage |
| --- | --- |
| `./localPipeline.sh` | The quality pipeline, modelled on myLastFmPlayer/Cullendula. Numbered stages: Models, ShellCheck, Python, Whitespace, Format (ktlint), Android Lint (warnings as errors), Unit Tests (JVM + Robolectric, Kover >=95% lines, HTML report), APK Build, E2E (instrumented tests on an attached emulator/device), Docker, Open Coverage, Launch App. Every stage runs; stages whose prerequisite failed are skipped; a stage-by-stage PASS/FAIL/WARN/SKIP summary with details (test counts, coverage %, APK sizes, device) is printed last. Exit code is non-zero if any mandatory stage fails. `--help` lists options. |
| `./localPipeline.sh --noRun --noOpen` | Do not install/launch the app on a device and do not open the coverage report (CI uses both). |
| `./localPipeline.sh --e2e auto\|required\|skip` | `auto` (default) runs E2E when a device is attached and otherwise marks WARN; `required` fails without a device (CI); `skip` skips. `--docker` accepts the same modes. |
| `./localPipeline.sh --report-dir DIR` | Keep per-stage logs, `environment.txt` and `summary.txt` in `DIR` (CI uploads `artifacts/pipeline`). |
| `scripts/emulator.sh [AVD]` | Boot an emulator headless (default AVD `ForkApi34` or `$FORK_AVD`) and wait for boot completion, so the E2E stage can run locally. No-op when a device is attached. Creating the AVD on a laptop: [emulator.md](emulator.md). |
| `scripts/screenshots.sh [DIR]` | Install the debug APK on an attached emulator/device and capture genuine welcome, synthetic-demo-warning, settings, camera-setup, dark-theme and landscape screenshots (default `docs/screenshots/`); display settings are restored afterwards. The demo is generated stick figures, never camera footage. |
| `scripts/docker-dist.sh [APK]` | Stage `build/docker-dist/` (Dockerfile, nginx config, site with APK, SHA-256, license, install page). Prefers the signed release APK, else the debug APK. See [docs/docker.md](docker.md). |
| `scripts/docker-smoke.sh [TAG]` | Stage, build and run the distribution image, then verify health, install page, license, APK MIME type and checksum, and 404 for missing paths. The pipeline's Docker stage runs it. |
| `scripts/replay.sh replay [OPTIONS] LOG...` | Re-run the detector over recorded training session logs (`.jsonl[.gz]`) and print reminders, false-alarm overlaps, detected labels, UNKNOWN fraction and live/replay agreement; `--trigger-ms/--clear-ms/--cooldown-ms/--hold-ms/--window-ms` for tuning experiments. `scripts/replay.sh demo-log OUT [LOOPS]` writes the labelled synthetic session. See [data.md](data.md). |
| `python3 scripts/chime.py [--check]` | Regenerate the three reminder chimes in `app/src/main/res/raw/` deterministically (Bell `chime.wav`, Marimba `chime_marimba.wav`, Glass `chime_glass.wav`; 22.05 kHz mono); `--check` (run by the pipeline) fails if a committed file differs. |
| `python3 scripts/check_links.py [FILE.md...]` | Verify that relative links, image paths and `#anchors` in Markdown files resolve (default: all tracked `*.md`). Run by the pipeline. |
| `python3 scripts/models.py` | Download MediaPipe Pose Landmarker Full/Lite (model version 1) into ignored `app/src/main/assets/` and verify SHA-256 from `models/checksums.json`. Idempotent; the pipeline runs it first. |
| `python3 scripts/report.py tests DIR` / `coverage XML` | Summarize JUnit XML results or Kover line coverage for the pipeline summary. |
| `python3 scripts/version.py patch\|minor` | Increment VERSION patch and the Android BUILD_NUMBER; `minor` also bumps the minor component for a major feature. Run once per commit. |
| `./gradlew spotlessApply` | Format authored Kotlin and build files before running the pipeline. |

Kover measures all app Kotlin source with no product-source exclusions. Set
`FORK_REPORT_BROWSER` to choose the program that opens the coverage report.
