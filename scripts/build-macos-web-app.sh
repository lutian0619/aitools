#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="$ROOT/macos/AIToolsWebStatus"
BUILD_DIR="$ROOT/build/macos"
APP="$BUILD_DIR/AITools Web.app"
CONTENTS="$APP/Contents"
MACOS="$CONTENTS/MacOS"

if ! command -v clang >/dev/null 2>&1; then
  echo "Missing clang. Install Xcode Command Line Tools first."
  exit 1
fi

mkdir -p "$MACOS"

clang -fobjc-arc "$SRC_DIR/main.m" \
  -framework Cocoa \
  -o "$MACOS/AIToolsWeb"

sed "s#__REPO_ROOT__#$ROOT#g" "$SRC_DIR/Info.plist" > "$CONTENTS/Info.plist"
chmod +x "$MACOS/AIToolsWeb"
printf "APPL????" > "$CONTENTS/PkgInfo"

if command -v codesign >/dev/null 2>&1; then
  codesign --force --deep --sign - "$APP" >/dev/null
fi

echo "Built: $APP"
echo "Open it with: open -g \"$APP\""
