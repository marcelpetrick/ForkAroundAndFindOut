#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Marcel Petrick
# SPDX-License-Identifier: GPL-3.0-or-later
# Stage the Docker build context in build/docker-dist: Dockerfile, nginx config and a
# site with the APK, its SHA-256, the SBOM (CycloneDX, from scripts/sbom.py build), the licence
# texts, third-party notices and an install page.
# Usage: scripts/docker-dist.sh [APK]   (default: signed release APK if present, else debug APK)
set -euo pipefail
cd "$(dirname "$0")/.."
version="$(cat VERSION)"
apk="${1:-}"
kind="signed release build"
if [[ -z "${apk}" ]]; then
    apk="app/build/outputs/apk/release/app-release.apk"
    if [[ ! -f "${apk}" ]]; then
        apk="app/build/outputs/apk/debug/app-debug.apk"
        kind="debug build (no release signing key configured)"
    fi
fi
[[ -f "${apk}" ]] || { echo "APK not found: ${apk}. Run ./localPipeline.sh first." >&2; exit 1; }
out="build/docker-dist"
rm -rf "${out}"
mkdir -p "${out}/site"
name="fork-around-and-find-out-${version}.apk"
cp "${apk}" "${out}/site/${name}"
(cd "${out}/site" && sha256sum "${name}" > "${name}.sha256")
sha="$(cut -d' ' -f1 "${out}/site/${name}.sha256")"
sbom="build/sbom/fork-around-and-find-out-${version}.cdx.json"
[[ -f "${sbom}" ]] || { echo "SBOM not found: ${sbom}. Run scripts/sbom.py build first." >&2; exit 1; }
cp "${sbom}" "${out}/site/"
cp LICENSE "${out}/site/LICENSE.txt"
mkdir -p "${out}/site/licenses"
cp LICENSES/*.txt "${out}/site/licenses/"
cp NOTICES.md "${out}/site/NOTICES.txt"
sed -e "s|@APK@|${name}|g" -e "s|@VERSION@|${version}|g" -e "s|@SHA256@|${sha}|g" -e "s|@BUILD_KIND@|${kind}|g" \
    docker/index.html > "${out}/site/index.html"
cp Dockerfile "${out}/Dockerfile"
cp docker/nginx.conf "${out}/nginx.conf"
echo "${out} (${name}, ${kind}, sha256 ${sha})"
