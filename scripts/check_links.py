#!/usr/bin/env python3
# Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
"""Check that relative Markdown links and image paths point to existing files, and that
#anchors exist as headings in the target. External (http/https/mailto) links are skipped.

Usage: scripts/check_links.py [FILE.md ...]   (default: all tracked *.md files)
"""
import re
import subprocess
import sys
from pathlib import Path

LINK = re.compile(r"!?\[[^\]]*\]\(([^)\s]+)\)")


def anchors(path: Path) -> set:
    result = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("#"):
            title = line.lstrip("#").strip().lower()
            result.add(re.sub(r"[^\w\- ]", "", title).replace(" ", "-"))
    return result


def check(markdown: Path) -> list:
    problems = []
    for target in LINK.findall(markdown.read_text(encoding="utf-8")):
        if re.match(r"^(https?:|mailto:)", target):
            continue
        file_part, _, anchor = target.partition("#")
        resolved = (markdown.parent / file_part).resolve() if file_part else markdown.resolve()
        if not resolved.exists():
            problems.append(f"{markdown}: missing {target}")
        elif anchor and resolved.suffix == ".md" and anchor not in anchors(resolved):
            problems.append(f"{markdown}: missing anchor {target}")
    return problems


if __name__ == "__main__":
    files = [Path(f) for f in sys.argv[1:]] or [
        Path(f) for f in subprocess.run(["git", "ls-files", "*.md"], capture_output=True, text=True, check=True).stdout.split()
    ]
    issues = [problem for markdown in files for problem in check(markdown)]
    print("\n".join(issues) or f"{len(files)} Markdown files: all relative links resolve")
    sys.exit(1 if issues else 0)
