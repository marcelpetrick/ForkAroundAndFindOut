#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Marcel Petrick
# SPDX-License-Identifier: GPL-3.0-or-later
# Build the distribution image and verify it: install page, APK download with the right
# MIME type and checksum, license, health endpoint, and no directory listing.
# Usage: scripts/docker-smoke.sh [IMAGE_TAG]   (default: fork-around-and-find-out:local)
set -euo pipefail
cd "$(dirname "$0")/.."
tag="${1:-fork-around-and-find-out:local}"
scripts/docker-dist.sh
docker build --quiet --build-arg "VERSION=$(cat VERSION)" -t "${tag}" build/docker-dist >/dev/null
container="$(docker run -d --rm -p 127.0.0.1::8080 "${tag}")"
trap 'docker stop "${container}" >/dev/null 2>&1 || true' EXIT
port="$(docker port "${container}" 8080/tcp | head -n 1 | sed 's/.*://')"
base="http://127.0.0.1:${port}"
for _ in $(seq 50); do curl -fsS "${base}/healthz" >/dev/null 2>&1 && break; sleep 0.2; done
curl -fsS "${base}/healthz" | grep -qx ok
name="fork-around-and-find-out-$(cat VERSION).apk"
curl -fsS "${base}/" | grep -q "${name}"
curl -fsS "${base}/LICENSE.txt" | grep -q "GNU GENERAL PUBLIC LICENSE"
curl -fsS "${base}/NOTICES.txt" | grep -q "MediaPipe Tasks"
curl -fsS "${base}/licenses/Apache-2.0.txt" | grep -q "Apache License"
version="${name#fork-around-and-find-out-}"
version="${version%.apk}"
curl -fsS "${base}/fork-around-and-find-out-${version}.cdx.json" |
    python3 -c 'import json, sys; bom = json.load(sys.stdin); assert bom["bomFormat"] == "CycloneDX" and bom["components"]'
curl -fsS "${base}/" | grep -q "tree/v${version}"
type="$(curl -fsS -o /dev/null -w '%{content_type}' "${base}/${name}")"
[[ "${type}" == "application/vnd.android.package-archive" ]] || { echo "Wrong APK content type: ${type}" >&2; exit 1; }
tmp="$(mktemp -d)"
curl -fsS -o "${tmp}/${name}" "${base}/${name}"
curl -fsS -o "${tmp}/${name}.sha256" "${base}/${name}.sha256"
(cd "${tmp}" && sha256sum -c --quiet "${name}.sha256")
cmp -s "${tmp}/${name}" build/docker-dist/site/"${name}"
rm -rf "${tmp}"
status="$(curl -s -o /dev/null -w '%{http_code}' "${base}/missing/")"
[[ "${status}" == "404" ]] || { echo "Unexpected status for missing path: ${status}" >&2; exit 1; }
echo "Docker image ${tag} serves ${name} with a valid checksum on port ${port}"
