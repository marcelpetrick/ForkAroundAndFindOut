#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Marcel Petrick
# SPDX-License-Identifier: GPL-3.0-or-later
# Boot an Android emulator headless and wait until it is ready for tests.
# Usage: scripts/emulator.sh [AVD_NAME]   (default: ForkApi34, or $FORK_AVD)
set -euo pipefail
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
adb="${ANDROID_HOME}/platform-tools/adb"
avd="${1:-${FORK_AVD:-ForkApi34}}"
if "${adb}" devices | awk 'NR > 1 && $2 == "device"' | grep -q .; then
    echo "A device is already attached."
    exit 0
fi
nohup "${ANDROID_HOME}/emulator/emulator" -avd "${avd}" -no-window -no-audio -no-boot-anim \
    -no-snapshot-save -gpu swiftshader_indirect > "${TMPDIR:-/tmp}/emulator-${avd}.log" 2>&1 &
timeout 300 "${adb}" wait-for-device
until [[ "$("${adb}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do sleep 2; done
echo "Emulator ${avd} booted."
