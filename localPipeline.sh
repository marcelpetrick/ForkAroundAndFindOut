#!/usr/bin/env bash
# Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
# Shared fail-fast quality gate; run from any directory.
set -euo pipefail
cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
shellcheck localPipeline.sh
python3 -m compileall -q scripts
./gradlew --console=plain spotlessCheck :app:lintDebug :app:testDebugUnitTest :app:koverXmlReportDebug :app:koverVerifyDebug :app:assembleDebug :app:assembleRelease
git diff --check
