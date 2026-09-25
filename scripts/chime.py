#!/usr/bin/env python3
# Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
"""Generate the reminder chime deterministically (no third-party audio).

Two soft bell-like notes (G5 then C6) with gentle attack and exponential decay,
22.05 kHz mono 16-bit. Usage:
  scripts/chime.py           write app/src/main/res/raw/chime.wav
  scripts/chime.py --check   fail if the committed file differs from the generator
"""
import io
import math
import struct
import sys
import wave
from pathlib import Path

RATE = 22050
TARGET = Path("app/src/main/res/raw/chime.wav")


def render() -> bytes:
    samples = []
    notes = [(783.99, 0.0), (1046.5, 0.28)]  # G5, C6 (seconds offset)
    length = int(RATE * 1.1)
    for i in range(length):
        t = i / RATE
        value = 0.0
        for frequency, start in notes:
            local = t - start
            if local < 0:
                continue
            envelope = min(1.0, local / 0.01) * math.exp(-local * 4.5)
            value += envelope * (math.sin(2 * math.pi * frequency * local) + 0.25 * math.sin(4 * math.pi * frequency * local))
        samples.append(int(max(-1.0, min(1.0, value * 0.35)) * 32767))
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(struct.pack(f"<{len(samples)}h", *samples))
    return buffer.getvalue()


if __name__ == "__main__":
    data = render()
    if sys.argv[1:] == ["--check"]:
        if not TARGET.is_file() or TARGET.read_bytes() != data:
            sys.exit(f"{TARGET} is missing or differs from scripts/chime.py output; run scripts/chime.py")
        print(f"Verified {TARGET}")
    else:
        TARGET.write_bytes(data)
        print(f"Wrote {TARGET} ({len(data)} bytes)")
