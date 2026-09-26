<!-- SPDX-FileCopyrightText: 2026 Marcel Petrick -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Docker distribution

The product is an Android app: the camera, pose model and warnings run only on the
phone. Docker therefore does **not** run the monitor. The image is a small, pinned
`nginx:1.30.5-alpine` server that distributes the app:

| Path | Content |
| --- | --- |
| `/` | Install page with version, build kind and SHA-256 |
| `/fork-around-and-find-out-<version>.apk` | The APK (`application/vnd.android.package-archive`) |
| `/fork-around-and-find-out-<version>.apk.sha256` | Checksum file for `sha256sum -c` |
| `/LICENSE.txt` | GPL v3 |
| `/healthz` | Health endpoint used by the image `HEALTHCHECK` |

## Use the published image

```sh
docker run --rm -p 8080:8080 ghcr.io/marcelpetrick/forkaroundandfindout:latest
# then open http://<this-computer>:8080 on the phone (same network) and download the APK
```

Tags: `latest` (main), `<version>` (every main commit), `sha-<commit>`. Images are
built and pushed by the `Pipeline` workflow only after every pipeline stage passed.

## Build locally

```sh
./localPipeline.sh            # builds and verifies the APKs, then the Docker stage
scripts/docker-smoke.sh       # stage + build + run + verify (MIME type, checksum, license)
docker run --rm -p 8080:8080 fork-around-and-find-out:local
```

`scripts/docker-dist.sh` stages `build/docker-dist/`. It prefers the signed release APK
and falls back to the debug APK (labelled as such on the install page) when no signing
key is configured. The site also serves the release SBOM
(`fork-around-and-find-out-<version>.cdx.json`), the licence texts under `licenses/`,
`NOTICES.txt`, and a link to the exact source tag; the smoke test checks all of them
([licensing](licensing.md)).

## Release signing

Release APKs are signed with a dedicated key (RSA 4096). Certificate SHA-256:

```text
AA:F8:58:04:C1:50:BB:DF:83:8A:28:49:B1:5A:7A:F3:F8:A9:74:7F:08:3E:A0:82:47:33:7A:10:7A:86:5A:E0
```

Verify a download with `apksigner verify --print-certs <apk>`. Locally, Gradle reads a
properties file (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) from
`$FORK_SIGNING` or `~/.android/fork-around-and-find-out-release.properties`; CI rebuilds
it from the `FORK_KEYSTORE_BASE64` and `FORK_KEYSTORE_PASSWORD` repository secrets.
Without a key the release APK stays unsigned. The keystore is never committed.
