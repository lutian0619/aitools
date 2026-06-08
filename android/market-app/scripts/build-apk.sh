#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ -x ./gradlew ]; then
  ./gradlew assembleDebug
else
  bash scripts/build-apk-manual.sh
fi
