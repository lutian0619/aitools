#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

bash "$ROOT/scripts/preflight-android-contract.sh"

cd "$ROOT/android/tracker-app"
bash scripts/build-apk-manual.sh

mkdir -p "$ROOT/artifacts/apk"
cp build/outputs/apk/debug/app-debug.apk "$ROOT/artifacts/apk/tracker-debug.apk"
echo "APK: $ROOT/artifacts/apk/tracker-debug.apk"
