#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ "${SKIP_DESIGN_CONTRACT:-}" = "1" ]; then
  exit 0
fi

node "$ROOT/scripts/validate-design-contract.js"
