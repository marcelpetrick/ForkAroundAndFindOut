<!-- SPDX-FileCopyrightText: 2026 Marcel Petrick -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Licensing, SPDX and the SBOM

Fork Around & Find Out is free software by Marcel Petrick under the **GNU General Public
License v3 or later** (`GPL-3.0-or-later`). This page explains how the project meets that
licence and the licences of everything it bundles, and how the pipeline keeps it that way.
It is a careful engineering review, not legal advice.

## 1. Every file is tagged (REUSE 3.3)

- Every source, build, script, document and resource file starts with
  `SPDX-FileCopyrightText` and `SPDX-License-Identifier` lines.
- Files that cannot carry a comment (images, sounds, JSON, `VERSION`, the Gradle wrapper, the
  owner's unedited `vision.md`, the planning agent's `plan_v2/`, downloaded models, verbatim
  third-party notices) are annotated in [`REUSE.toml`](../REUSE.toml).
- Every licence used is in [`LICENSES/`](../LICENSES): GPL-3.0-or-later, Apache-2.0,
  BSD-3-Clause and MIT. [`LICENSE`](../LICENSE) is the same GPL text for GitHub.
- `reuse lint` runs in the pipeline (stage *Lint Suite*, [`scripts/lint.sh`](../scripts/lint.sh)).
  A new file without tags fails CI.

## 2. The software bill of materials

- `./gradlew :app:cyclonedxDirectBom` lists exactly the **release runtime classpath**, which
  is what ends up in the APK. The CycloneDX Gradle plugin records resolved versions, hashes
  and the licences declared in each component's POM.
- [`scripts/sbom.py`](../scripts/sbom.py) `build` adds the app itself (GPL-3.0-or-later,
  author, source), the two bundled MediaPipe models (Apache-2.0, SHA-256 from
  [`models/checksums.json`](../models/checksums.json)) and the licence choice for dual-licensed
  components. It writes `build/sbom/fork-around-and-find-out-<version>.cdx.json`
  (CycloneDX 1.6).
- The pipeline stage *SBOM* builds it on every run. Every **GitHub release** attaches it, the
  **Docker image** serves it, and CI uploads it as an artifact.
- `scripts/sbom.py notices` derives `app/src/main/assets/third_party.json`, the list the
  app's **About** screen shows. `--check` fails the pipeline if the checked-in list differs
  from the SBOM, so the screen cannot drift from what ships.

## 3. What the app shows (GPLv3 § 0 "Appropriate Legal Notices")

The About screen shows:

- the author and the copyright line;
- the GPL notice with the warranty disclaimer, and the full GPL text inside the app;
- a link to the complete source of the exact version (`…/tree/v<version>`);
- every bundled component with version, SPDX licence and link, from the SBOM;
- the full Apache-2.0, BSD-3-Clause and MIT texts;
- each notice a licence requires to be passed on: MediaPipe's Apache NOTICE (its native
  libraries' upstream notices, shown in pages), jakarta.inject's NOTICE, and the copyright
  lines of protobuf and the Checker Framework.

## 4. Corresponding source (GPLv3 § 6)

Every published binary (the release APK and the GHCR image) comes from a public git tag
`v<version>` of this repository. The release notes, the About screen and the Docker install
page link that tag. The tag contains all build scripts, the Gradle wrapper and
`localPipeline.sh`: everything needed to rebuild. The two models are not source code; they
are fetched by `scripts/models.py` and verified by SHA-256.

**Installation information.** Android lets anyone install an APK they built themselves. It
only has to be signed with their own key, and the old app uninstalled first. Nothing locks the
user out of running modified versions, so no signing key needs to be published.

## 5. Third-party licences and GPLv3 compatibility

| Licence | Components (from the SBOM) | GPLv3 |
| --- | --- | --- |
| Apache-2.0 | AndroidX, Kotlin, MediaPipe Tasks and models, Guava, Dagger, Flogger, and more | Compatible (GPLv3, not GPLv2). The Apache NOTICE files are passed on in the app (§ 4(d)). |
| BSD-3-Clause | protobuf-javalite; parts of CameraX core | Compatible; copyright notice shown in the app |
| MIT | checker-qual; checker-compat-qual (dual "GPL-2.0 with Classpath exception" OR MIT, used under MIT) | Compatible; copyright notice shown in the app |

MediaPipe's native library (`libmediapipe_tasks_jni.so`) is built from many upstream
projects. Its NOTICE (187 sections) was scanned for licence families. Found: Apache-2.0,
BSD, MIT, ISC, zlib, Unicode and public-domain texts. Also found were MPL-2.0 (Eigen), the
GCC runtime library exception, and a triple-licensed library (googleurl, MPL/GPL/LGPL). All
are compatible with GPLv3. BoringSSL is under Apache-2.0. Two copies of the historic UC
Berkeley 4-clause BSD text appear; the University rescinded its advertising clause in 1999.

**Open question (recorded honestly).** The OpenCV section is a catalogue of every licence
used anywhere in OpenCV's tree. It includes a generic 4-clause BSD template with no named
holder, and it does not say which files use it. MediaPipe links only OpenCV's core (OpenCV
4.13 itself is Apache-2.0). No file in that core is known to use the template, but that
cannot be confirmed from the binary. If it ever matters, ask upstream (MediaPipe/OpenCV), or
build MediaPipe from source with the OpenCV modules audited.

## 6. How to check it yourself

```sh
scripts/lint.sh reuse                        # REUSE 3.3 compliance of every file
./gradlew :app:cyclonedxDirectBom && python3 scripts/sbom.py build
python3 scripts/sbom.py notices --check      # About list equals the SBOM
```
