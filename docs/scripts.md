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
| `scripts/emulator.sh [AVD]` | Boot an emulator headless (default AVD `ForkApi34` or `$FORK_AVD`) and wait for boot completion, so the E2E stage can run locally. No-op when a device is attached. |
| `python3 scripts/models.py` | Download MediaPipe Pose Landmarker Full/Lite (model version 1) into ignored `app/src/main/assets/` and verify SHA-256 from `models/checksums.json`. Idempotent; the pipeline runs it first. |
| `python3 scripts/report.py tests DIR` / `coverage XML` | Summarize JUnit XML results or Kover line coverage for the pipeline summary. |
| `python3 scripts/version.py patch\|minor` | Increment VERSION patch and the Android BUILD_NUMBER; `minor` also bumps the minor component for a major feature. Run once per commit. |
| `./gradlew spotlessApply` | Format authored Kotlin and build files before running the pipeline. |

Kover measures all app Kotlin source with no product-source exclusions. Set
`FORK_REPORT_BROWSER` to choose the program that opens the coverage report.
