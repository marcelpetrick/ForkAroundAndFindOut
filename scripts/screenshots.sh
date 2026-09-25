#!/usr/bin/env bash
# Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
# Capture genuine screenshots of the running app from an attached emulator/device.
# Usage: scripts/screenshots.sh [OUTPUT_DIR]   (default: docs/screenshots)
# Installs the debug APK, then captures welcome, synthetic demo (with its warning)
# and settings. The demo shows generated stick figures, never camera footage.
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
adb="${ANDROID_HOME}/platform-tools/adb"
out="${1:-docs/screenshots}"
package="it.marcelpetrick.fork"
mkdir -p "${out}"

# tap_text TEXT: tap the centre of the first view whose text or description matches.
tap_text() {
    "${adb}" shell uiautomator dump /sdcard/ui.xml >/dev/null
    local point
    point="$("${adb}" shell cat /sdcard/ui.xml | python3 -c '
import re, sys
xml, wanted = sys.stdin.read(), sys.argv[1]
for node in re.finditer(r"<node [^>]*>", xml):
    tag = node.group(0)
    if f"text=\"{wanted}\"" in tag or f"content-desc=\"{wanted}\"" in tag:
        a, b, c, d = map(int, re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", tag).groups())
        print((a + c) // 2, (b + d) // 2)
        break
' "$1")"
    [[ -n "${point}" ]] || { echo "Text not found on screen: $1" >&2; exit 1; }
    # shellcheck disable=SC2086
    "${adb}" shell input tap ${point}
}

"${adb}" install -r -g app/build/outputs/apk/debug/app-debug.apk >/dev/null
"${adb}" shell pm clear "${package}" >/dev/null
"${adb}" shell am start -W -n "${package}/.MainActivity" >/dev/null
sleep 2
"${adb}" exec-out screencap -p > "${out}/welcome.png"
tap_text "Try demo (synthetic)"
sleep 7 # the synthetic seat 2 rests an elbow from 3 s; the warning shows after ~5 s
"${adb}" exec-out screencap -p > "${out}/demo-synthetic-warning.png"
"${adb}" shell input keyevent KEYCODE_BACK
sleep 1
tap_text "Settings"
sleep 1
"${adb}" exec-out screencap -p > "${out}/settings.png"
"${adb}" shell input keyevent KEYCODE_BACK
echo "Screenshots written to ${out}"
