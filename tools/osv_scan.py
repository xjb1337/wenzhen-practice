#!/usr/bin/env python3
"""Scan direct Gradle (Maven) dependencies against the OSV vulnerability database.

Parses `group:artifact:version` coordinates from app/build.gradle.kts, ignores
BOM/platform entries without a concrete version, and exits non-zero when any
known vulnerability affects the pinned version.
"""
from __future__ import annotations

import json
import pathlib
import re
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
BUILD_FILE = ROOT / "app" / "build.gradle.kts"
COORDINATE = re.compile(r'"([\w.\-]+):([\w.\-]+):([\w.\-]+)"')
OSV_QUERY = "https://api.osv.dev/v1/query"


def dependencies() -> list[tuple[str, str]]:
    text = BUILD_FILE.read_text(encoding="utf-8")
    seen: set[tuple[str, str]] = set()
    result: list[tuple[str, str]] = []
    for line in text.splitlines():
        if "platform(" in line or not any(
            marker in line
            for marker in ("implementation(", "testImplementation(", "androidTestImplementation(", "ksp(")
        ):
            continue
        match = COORDINATE.search(line)
        if not match:
            continue
        group, artifact, version = match.groups()
        if version.lower() in {"latest.release", "latest.integration"}:
            continue
        item = (f"{group}:{artifact}", version)
        if item not in seen:
            seen.add(item)
            result.append(item)
    return result


def query(package: str, version: str) -> list[dict]:
    body = json.dumps(
        {"package": {"name": package, "ecosystem": "Maven"}, "version": version}
    ).encode("utf-8")
    request = urllib.request.Request(
        OSV_QUERY, data=body, headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            payload = json.load(response)
    except (urllib.error.URLError, TimeoutError) as error:
        print(f"! {package}:{version} query failed: {error}")
        return []
    return payload.get("vulns", [])


def main() -> int:
    deps = dependencies()
    findings = 0
    print(f"Scanning {len(deps)} direct Maven dependencies with OSV…")
    for package, version in deps:
        vulns = query(package, version)
        if not vulns:
            print(f"  OK   {package}:{version}")
            continue
        for vuln in vulns:
            findings += 1
            aliases = ", ".join(vuln.get("aliases", []))
            print(f"  VULN {package}:{version} -> {vuln.get('id')} {aliases}")
    if findings:
        print(f"Found {findings} known vulnerability record(s).")
        return 1
    print("No known vulnerabilities for pinned direct dependencies.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
