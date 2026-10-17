#!/usr/bin/env python3
"""Build a preview of NyaWorld updater inside the XDAetherium Android runtime.

This is NOT yet a fully integrated NyaWorld launcher or proven mod compatibility.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app_pojavlauncher"
gradle = app / "build.gradle"
manifest = app / "src/main/AndroidManifest.xml"
storage = app / "src/main/java/net/kdt/pojavlaunch/TestStorageActivity.java"
target = app / "src/main/java/net/kdt/pojavlaunch/NyaWorldSyncActivity.java"
source = Path(__file__).resolve().parent.parent / "android" / "NyaWorldSyncActivity.java"

for path in (gradle,manifest,storage,source):
    if not path.is_file():
        raise SystemExit(f"Required upstream/file missing: {path}")

data = gradle.read_text(encoding="utf-8")
old = '"XDAetherium (Debug)"'
if old not in data:
    raise SystemExit("Upstream debug app_name declaration changed")
gradle.write_text(data.replace(old, '"NyaWorld Sync Preview"', 1), encoding="utf-8")

data = manifest.read_text(encoding="utf-8")
marker = '        <activity\n            android:name=".TestStorageActivity"'
if marker not in data:
    raise SystemExit("Upstream AndroidManifest.xml TestStorageActivity changed")
data = data.replace(marker, '        <activity android:name=".NyaWorldSyncActivity" android:exported="false" />\n'+marker, 1)
manifest.write_text(data, encoding="utf-8")

data = storage.read_text(encoding="utf-8")
marker = 'Intent intent =  new Intent(this, LauncherActivity.class);'
if marker not in data:
    raise SystemExit("Upstream TestStorageActivity navigation changed")
data = data.replace(marker, 'Intent intent =  new Intent(this, NyaWorldSyncActivity.class);', 1)
storage.write_text(data,encoding="utf-8")

target.write_bytes(source.read_bytes())
print("NyaWorld updater preview integrated. No game/mod JARs embedded.")
