#!/usr/bin/env python3
"""Copies the built HUD mod jars into the launcher resources and writes resources/hud/manifest.json."""
import glob
import json
import os
import re
import shutil

here = os.path.dirname(os.path.abspath(__file__))
out = os.path.normpath(os.path.join(here, "..", "..", "src-tauri", "resources", "hud"))
os.makedirs(out, exist_ok=True)
for old in glob.glob(os.path.join(out, "nocturnum-hud-*.jar")):
    os.remove(old)

versions = {}
for jar in sorted(glob.glob(os.path.join(here, "..", "v*", "build", "libs", "nocturnum-hud-*.jar"))):
    name = os.path.basename(jar)
    if name.endswith("-sources.jar"):
        continue
    m = re.match(r"nocturnum-hud-[\d.]+\+(.+)\.jar", name)
    if not m:
        continue
    mc = m.group(1)
    dest = f"nocturnum-hud-{mc}.jar"
    shutil.copy(jar, os.path.join(out, dest))
    versions[mc] = dest

with open(os.path.join(out, "manifest.json"), "w", encoding="utf-8") as f:
    json.dump({"versions": versions}, f, indent=2)
print("synced", versions)
