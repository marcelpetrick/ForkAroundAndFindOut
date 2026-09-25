#!/usr/bin/env bash
# Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
# Evaluate recorded training sessions on the desktop (see docs/data.md).
# Usage: scripts/replay.sh replay [--trigger-ms N] [--clear-ms N] [--cooldown-ms N] [--window-ms N] LOG...
#        scripts/replay.sh demo-log OUT.jsonl.gz [LOOPS]
# Paths may be relative to the current directory.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
args=()
for argument in "$@"; do
    if [[ "${argument}" != --* && -e "${argument}" || "${argument}" == *.gz || "${argument}" == *.jsonl ]]; then
        args+=("$(realpath -m "${argument}")")
    else
        args+=("${argument}")
    fi
done
"${root}/gradlew" -q -p "${root}" :tools:installDist
"${root}/tools/build/install/tools/bin/tools" "${args[@]}"
