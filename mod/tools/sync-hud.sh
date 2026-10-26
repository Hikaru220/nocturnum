#!/usr/bin/env bash
# Builds the HUD mod for every supported game version and copies the jars into the launcher resources.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${NOCTURNUM_JDK25:-$HOME/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2}"
./gradlew ${TASKS:-buildAll} --no-daemon -q
OUT=../src-tauri/resources/hud
mkdir -p "$OUT"
rm -f "$OUT"/nocturnum-hud-*.jar
${PYTHON:-python} - <<'PY'
import glob, json, os, shutil, re
out = "../src-tauri/resources/hud"
versions = {}
for proj in sorted(glob.glob("v*/build/libs/nocturnum-hud-*.jar")):
    name = os.path.basename(proj)
    if name.endswith("-sources.jar"):
        continue
    m = re.match(r"nocturnum-hud-[\d.]+\+(.+)\.jar", name)
    if not m:
        continue
    mc = m.group(1)
    dest = f"nocturnum-hud-{mc}.jar"
    shutil.copy(proj, os.path.join(out, dest))
    versions[mc] = dest
json.dump({"versions": versions}, open(os.path.join(out, "manifest.json"), "w"), indent=2)
print("synced", versions)
PY
