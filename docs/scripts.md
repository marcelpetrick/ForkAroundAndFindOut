# Developer scripts

Run scripts from the repository root unless stated otherwise. Java 21, Android SDK,
Python 3 and ShellCheck must be installed. Gradle is pinned via the wrapper.

| Script | Purpose and usage |
| --- | --- |
| `./localPipeline.sh` | Full local/CI gate: Kotlin formatting, compiler/static checks, Android lint, JVM/Robolectric tests, >=95% Kotlin line coverage, debug and unsigned release APK builds, shell lint, Python compilation, whitespace. Fail-fast, no bypass flags. |
| `./gradlew spotlessApply` | Format authored Kotlin and build files before running the pipeline. |
| `python3 scripts/version.py patch` | Increment VERSION patch and independent Android BUILD_NUMBER. `minor` also increments the minor component for a major feature; patch still advances per the user's rule. Run once per commit. |

Native instrumentation/e2e and Docker gates will be added with those components.
Kover measures all app Kotlin source with no product-source exclusions.
