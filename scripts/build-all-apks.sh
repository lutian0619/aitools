#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

bash "$ROOT/scripts/preflight-android-contract.sh"

SKIP_DESIGN_CONTRACT=1 bash "$ROOT/scripts/build-diary-apk.sh"
SKIP_DESIGN_CONTRACT=1 bash "$ROOT/scripts/build-tracker-apk.sh"
SKIP_DESIGN_CONTRACT=1 bash "$ROOT/scripts/build-print-apk.sh"
SKIP_DESIGN_CONTRACT=1 bash "$ROOT/scripts/build-metronome-apk.sh"
SKIP_DESIGN_CONTRACT=1 bash "$ROOT/scripts/build-word-duel-apk.sh"
SKIP_DESIGN_CONTRACT=1 bash "$ROOT/scripts/build-street-duel-apk.sh"
SKIP_DESIGN_CONTRACT=1 bash "$ROOT/scripts/build-egg-friends-apk.sh"
SKIP_DESIGN_CONTRACT=1 bash "$ROOT/scripts/build-market-apk.sh"
