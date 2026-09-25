#!/usr/bin/env python3
# Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
"""Bump patch (or minor plus patch) and the independent Android build number."""
import sys
from pathlib import Path

assert sys.argv[1:] in (["patch"], ["minor"]), "Usage: scripts/version.py patch|minor"
major, minor, patch = map(int, Path("VERSION").read_text().strip().split("."))
minor += sys.argv[1] == "minor"
version = f"{major}.{minor}.{patch + 1}"
Path("VERSION").write_text(version + "\n")
build = Path("BUILD_NUMBER")
build.write_text(str(int(build.read_text()) + 1) + "\n")
print(version)
