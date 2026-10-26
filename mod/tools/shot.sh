#!/usr/bin/env bash
# Dev helper: launches a throw-away Fabric instance with the HUD mod and prints the newest screenshot path.
# usage: tools/shot.sh <mc-version> <debug-flags> [hold-seconds]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TASKS="${TASKS:-:v1_21_11:build}" bash "$HERE/sync-hud.sh" | tail -1
cd "$HERE/../../src-tauri"
export PATH="$HOME/.cargo/bin:$PATH"
export NOCTURNUM_HOME="$(cd .. && pwd)/.testdata"
export NOCTURNUM_DEBUG="${2:-clickgui,shot}"
export NOCTURNUM_HOLD="${3:-30}"
cargo build -q 2>&1 | grep -E "^error" -A6 || true
LOG="../.shot.log"; : > "$LOG"
./target/debug/nocturnum-launcher.exe --selftest "$1" fabric hud > "$LOG" 2>&1 &
timeout 400 bash -c "until grep -q 'SELFTEST' '$LOG'; do sleep 3; done"
grep -E "SELFTEST|LAUNCH ERROR" "$LOG" | head -3
ls -t "$NOCTURNUM_HOME"/instances/*/minecraft/screenshots/*.png | head -1
