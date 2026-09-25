# Fork Around & Find Out

[![Pipeline](https://github.com/marcelpetrick/ForkAroundAndFindOut/actions/workflows/pipeline.yml/badge.svg?branch=main)](https://github.com/marcelpetrick/ForkAroundAndFindOut/actions/workflows/pipeline.yml)
[![License: GPL v3 or later](https://img.shields.io/badge/license-GPLv3%20or%20later-blue.svg)](LICENSE)
[![Android 14+](https://img.shields.io/badge/Android-14%2B%20%28API%2034%29-3ddc84.svg)](https://developer.android.com/about/versions/14)
[![Kotlin 2.2](https://img.shields.io/badge/Kotlin-2.2-7f52ff.svg)](https://kotlinlang.org/)
[![CameraX 1.6.2](https://img.shields.io/badge/CameraX-1.6.2-4285f4.svg)](https://developer.android.com/media/camera/camerax)
[![MediaPipe Tasks 1.0.0](https://img.shields.io/badge/MediaPipe%20Tasks-1.0.0-0097a7.svg)](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/android)
[![Coverage: 98%](https://img.shields.io/badge/coverage-98%25-brightgreen.svg)](app/build.gradle.kts)
[![Coverage gate: 95%](https://img.shields.io/badge/coverage%20gate-95%25-brightgreen.svg)](localPipeline.sh)
[![Offline: on-device](https://img.shields.io/badge/processing-on--device%2C%20offline-success.svg)](docs/detection.md)

An offline Android dining-table elbow monitor, written entirely in Kotlin.
See [the delivery ledger](plan.md), [plan v2](plan_v2/plan_v2.md), [vision](vision.md)
and [pose framework selection](docs/pose-frameworks.md).

**Author: Marcel Petrick. License: GPLv3 or later. Built with AI assistance.**

## Development

Install Java 21, Android SDK, Python 3 and ShellCheck. Set `ANDROID_HOME`, then:

```sh
./localPipeline.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android 14/API 34 is the minimum. Compilation and target SDK are API 37.
The pipeline formats/checks/tests/builds through pinned Gradle, runs identically
on GitHub Actions, and enforces >=95% Kotlin line coverage. CI uploads APKs and
reports. Release APKs are arm64-only and signed with the project key when it is
configured (see [Docker and signing](docs/docker.md)).

```sh
docker run --rm -p 8080:8080 ghcr.io/marcelpetrick/forkaroundandfindout:latest
```

serves the signed APK, its checksum and the license for download to a phone.

See [script documentation](docs/scripts.md) and [working rules](agents.md).
