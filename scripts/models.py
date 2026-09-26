#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Marcel Petrick
# SPDX-License-Identifier: GPL-3.0-or-later
"""Fetch immutable MediaPipe model version 1 and verify SHA-256 before bundling."""

import hashlib
import json
from pathlib import Path
from urllib.request import urlopen

for name, expected in json.loads(Path("models/checksums.json").read_text()).items():
    target = Path("app/src/main/assets") / (name + ".task")
    if not target.exists():
        url = f"https://storage.googleapis.com/mediapipe-models/pose_landmarker/{name}/float16/1/{name}.task"
        with urlopen(url, timeout=120) as response:
            data = response.read()
        assert hashlib.sha256(data).hexdigest() == expected, f"Invalid download: {name}"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    assert hashlib.sha256(target.read_bytes()).hexdigest() == expected, f"Invalid model: {name}"
    print(f"Verified {name}")
