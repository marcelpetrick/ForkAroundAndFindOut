#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Marcel Petrick
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate the reminder chimes deterministically (no third-party audio).

Three soft sounds, 22.05 kHz mono 16-bit, gentle attack and exponential decay:
  chime.wav          Bell     two bell-like notes, G5 then C6
  chime_marimba.wav  Marimba  two warm wooden notes, E5 then A5, short decay
  chime_glass.wav    Glass    one high C6 with a soft fifth, long shimmer
Usage:
  scripts/chime.py           write the files into app/src/main/res/raw/
  scripts/chime.py --check   fail if a committed file differs from the generator
"""

import io
import math
import struct
import sys
import wave
from pathlib import Path

RATE = 22050
RAW = Path("app/src/main/res/raw")

# name: (notes as (frequency Hz, start s, level), decay per s, second-harmonic share, length s)
CHIMES = {
    "chime.wav": ([(783.99, 0.0, 1.0), (1046.5, 0.28, 1.0)], 4.5, 0.25, 1.1),
    "chime_marimba.wav": ([(659.25, 0.0, 1.0), (880.0, 0.18, 1.0)], 9.0, 0.08, 0.8),
    "chime_glass.wav": ([(1046.5, 0.0, 1.0), (1567.98, 0.0, 0.35)], 2.6, 0.12, 1.6),
}


def render(notes, decay: float, harmonic: float, seconds: float) -> bytes:
    samples = []
    for i in range(int(RATE * seconds)):
        t = i / RATE
        value = 0.0
        for frequency, start, level in notes:
            local = t - start
            if local < 0:
                continue
            envelope = level * min(1.0, local / 0.01) * math.exp(-local * decay)
            value += envelope * (
                math.sin(2 * math.pi * frequency * local) + harmonic * math.sin(4 * math.pi * frequency * local)
            )
        samples.append(int(max(-1.0, min(1.0, value * 0.35)) * 32767))
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(struct.pack(f"<{len(samples)}h", *samples))
    return buffer.getvalue()


if __name__ == "__main__":
    check = sys.argv[1:] == ["--check"]
    for name, spec in CHIMES.items():
        target = RAW / name
        data = render(*spec)
        if check:
            if not target.is_file() or target.read_bytes() != data:
                sys.exit(f"{target} is missing or differs from scripts/chime.py output; run scripts/chime.py")
            print(f"Verified {target}")
        else:
            target.write_bytes(data)
            print(f"Wrote {target} ({len(data)} bytes)")
