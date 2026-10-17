#!/usr/bin/env python3
"""Apply minimal test branding to upstream engine (not full NyaWorld integration)."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
gradle = root / "app_pojavlauncher" / "build.gradle"
if not gradle.is_file():
    raise SystemExit(f"Missing expected Gradle file: {gradle}")
data = gradle.read_text(encoding="utf-8")
old = '"XDAetherium (Debug)"'
if old not in data:
    raise SystemExit("Upstream test app_name declaration changed; review branding patch")
data = data.replace(old, '"NyaWorld Build Test"', 1)
gradle.write_text(data, encoding="utf-8")
print("Test branding applied. No NyaWorld game files included.")
