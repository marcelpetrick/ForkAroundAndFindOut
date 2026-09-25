#!/usr/bin/env python3
# Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.
"""Summarize JUnit XML results or Kover line coverage for the pipeline summary.

Usage: scripts/report.py tests DIR...  -> "N tests, F failed, S skipped"
       scripts/report.py coverage XML  -> "line coverage 98.2% (1198/1220)"
"""
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path


def tests(*directories: Path) -> str:
    total = failed = skipped = 0
    for report in (found for directory in directories for found in directory.rglob("TEST-*.xml")):
        suite = ElementTree.parse(report).getroot()
        total += int(suite.get("tests", 0))
        failed += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
    return f"{total} tests, {failed} failed, {skipped} skipped"


def coverage(report: Path) -> str:
    for counter in ElementTree.parse(report).getroot().findall("counter"):
        if counter.get("type") == "LINE":
            missed, covered = int(counter.get("missed")), int(counter.get("covered"))
            return f"line coverage {100 * covered / (missed + covered):.1f}% ({covered}/{missed + covered})"
    return "no line counter"


if __name__ == "__main__":
    kind, targets = sys.argv[1], [Path(argument) for argument in sys.argv[2:]]
    try:
        print(tests(*targets) if kind == "tests" else coverage(targets[0]))
    except (OSError, ElementTree.ParseError, KeyError) as problem:
        print(f"no {kind} report ({problem.__class__.__name__})")
