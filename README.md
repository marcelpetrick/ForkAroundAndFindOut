# Fork Around & Find Out

[![Pipeline](https://github.com/marcelpetrick/ForkAroundAndFindOut/actions/workflows/pipeline.yml/badge.svg?branch=main)](https://github.com/marcelpetrick/ForkAroundAndFindOut/actions/workflows/pipeline.yml)
[![License: GPLv3+](https://img.shields.io/badge/license-GPLv3%2B-blue.svg)](LICENSE)
![Android](https://img.shields.io/badge/Android-14%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-native-7F52FF)
![Coverage gate](https://img.shields.io/badge/coverage_gate-95%25-brightgreen)

An offline Android dining-table elbow monitor, written entirely in Kotlin.
Implementation is underway: see [the handoff plan](plan.md), [vision](vision.md),
and [pose framework selection](docs/pose-frameworks.md). The initial foundation
is not yet a detector.

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
reports. Debug APKs are installable development artifacts; release APKs are
unsigned until a production signing key is configured.

See [script documentation](docs/scripts.md) and [working rules](agents.md).
Docker delivery and a real Android UI screenshot are tracked in `plan.md`.
