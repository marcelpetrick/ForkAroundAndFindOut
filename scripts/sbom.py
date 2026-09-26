#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Marcel Petrick
# SPDX-License-Identifier: GPL-3.0-or-later
"""Release SBOM and the in-app third-party list, from one source of truth.

scripts/sbom.py build [OUT]     read the Gradle CycloneDX SBOM of the release runtime classpath
                                (./gradlew :app:cyclonedxDirectBom), describe the app itself,
                                add the bundled MediaPipe models, normalise licence choices and
                                write the release SBOM (default build/sbom/<name>-<version>.cdx.json)
scripts/sbom.py notices [--check]
                                write app/src/main/assets/third_party.json (read by the About
                                screen) from that SBOM; --check fails when the checked-in list
                                differs, so the screen can never drift from what is shipped
"""

import json
import sys
from pathlib import Path

GRADLE_SBOM = Path("app/build/reports/cyclonedx-direct/bom.json")
NOTICES = Path("app/src/main/assets/third_party.json")
NAME = "fork-around-and-find-out"
SOURCE = "https://github.com/marcelpetrick/ForkAroundAndFindOut"
MODEL_URL = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/{0}/float16/1/{0}.task"
MODEL_CARD = "https://storage.googleapis.com/mediapipe-assets/Model%20Card%20BlazePose%20GHUM%203D.pdf"
# Dual-licensed components: the licence this project uses them under (GPLv3-compatible).
CHOICES = {"org.checkerframework:checker-compat-qual": "MIT"}
# Licence names some POMs use instead of SPDX ids.
NAMES = {
    "The Apache Software License, Version 2.0": "Apache-2.0",
    "The MIT License": "MIT",
    "BSD-3-Clause": "BSD-3-Clause",
}
# Where the notices a licence asks to pass on (Apache NOTICE, BSD/MIT copyright) are shown in the app.
NOTICE_FILES = {
    "com.google.mediapipe:tasks-core": "notices/mediapipe.txt",
    "com.google.mediapipe:tasks-vision": "notices/mediapipe.txt",
    "jakarta.inject:jakarta.inject-api": "notices/jakarta-inject.txt",
    "com.google.protobuf:protobuf-javalite": "notices/protobuf.txt",
    "org.checkerframework:checker-qual": "notices/checker-framework.txt",
    "org.checkerframework:checker-compat-qual": "notices/checker-framework.txt",
}


def version() -> str:
    return Path("VERSION").read_text(encoding="utf-8").strip()


def spdx(component: dict) -> str:
    key = f"{component.get('group', '')}:{component['name']}"
    if key in CHOICES:
        return CHOICES[key]
    ids = []
    for entry in component.get("licenses", []):
        lic = entry.get("license", {})
        ids.append(lic.get("id") or NAMES.get(lic.get("name", ""), lic.get("name", "")) or entry.get("expression", ""))
    if not ids:
        raise SystemExit(f"No licence for {key}; add it to scripts/sbom.py before shipping it")
    return " AND ".join(sorted(set(ids)))


def model(name: str, sha256: str) -> dict:
    return {
        "type": "machine-learning-model",
        "bom-ref": f"model:{name}",
        "group": "com.google.mediapipe",
        "name": name,
        "version": "1",
        "description": "MediaPipe Pose Landmarker model (float16), bundled as an app asset",
        "hashes": [{"alg": "SHA-256", "content": sha256}],
        "licenses": [{"license": {"id": "Apache-2.0"}}],
        "externalReferences": [
            {"type": "distribution", "url": MODEL_URL.format(name)},
            {"type": "documentation", "url": MODEL_CARD},
        ],
    }


def build(out: Path) -> Path:
    bom = json.loads(GRADLE_SBOM.read_text(encoding="utf-8"))
    app = bom["metadata"]["component"]
    app.update(
        {
            "name": NAME,
            "version": version(),
            "description": "Offline Android app that gently reminds a family to keep elbows off the dinner table",
            "licenses": [{"license": {"id": "GPL-3.0-or-later"}}],
            "authors": [{"name": "Marcel Petrick"}],
            "externalReferences": [{"type": "vcs", "url": SOURCE + ".git"}, {"type": "website", "url": SOURCE}],
        }
    )
    bom["metadata"]["licenses"] = [{"license": {"id": "GPL-3.0-or-later"}}]
    for component in bom["components"]:
        if component.get("group") == "it.marcelpetrick.fork":
            component["licenses"] = [{"license": {"id": "GPL-3.0-or-later"}}]
            continue
        chosen = spdx(component)
        declared = " AND ".join(sorted({lic.get("license", {}).get("id", "") for lic in component.get("licenses", [])}))
        if chosen != declared:
            component["licenses"] = [{"expression": chosen}]
    checksums = json.loads(Path("models/checksums.json").read_text(encoding="utf-8"))
    bom["components"] += [model(name, sha) for name, sha in sorted(checksums.items())]
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(bom, indent=2) + "\n", encoding="utf-8")
    return out


def notices(bom: dict) -> str:
    items = []
    for component in bom["components"]:
        if component.get("group") == "it.marcelpetrick.fork":
            continue
        refs = {r["type"]: r["url"] for r in component.get("externalReferences", [])}
        key = f"{component.get('group', '')}:{component['name']}"
        entry = {
            "group": component.get("group", ""),
            "name": component["name"],
            "version": component["version"],
            "license": spdx(component),
            "url": refs.get("website") or refs.get("vcs") or refs.get("distribution", ""),
        }
        if key in NOTICE_FILES:
            entry["notice"] = NOTICE_FILES[key]
        items.append(entry)
    items.sort(key=lambda e: (e["group"], e["name"]))
    return json.dumps({"components": items}, indent=1, ensure_ascii=False) + "\n"


def main(args: list[str]) -> int:
    default = Path(f"build/sbom/{NAME}-{version()}.cdx.json")
    if args[:1] == ["build"]:
        out = build(Path(args[1]) if len(args) > 1 else default)
        print(f"Wrote {out}")
        return 0
    if args[:1] == ["notices"]:
        source = default if default.is_file() else build(default)
        text = notices(json.loads(source.read_text(encoding="utf-8")))
        if args[1:] == ["--check"]:
            if not NOTICES.is_file() or NOTICES.read_text(encoding="utf-8") != text:
                print(f"{NOTICES} differs from the SBOM; run scripts/sbom.py notices", file=sys.stderr)
                return 1
            count = len(json.loads(text)["components"])
            print(f"Verified {NOTICES} ({count} components)")
            return 0
        NOTICES.write_text(text, encoding="utf-8")
        print(f"Wrote {NOTICES}")
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
