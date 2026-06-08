#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/Users/gogogo/Library/Android/sdk}"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"

if ! command -v gradle >/dev/null 2>&1; then
  echo "缺少 Gradle。可以用 Android Studio 打开本目录构建，或安装 Gradle 后重试。"
  exit 1
fi

if [ ! -f "$ANDROID_SDK_ROOT/platforms/android-35/android.jar" ]; then
  echo "缺少 Android SDK: $ANDROID_SDK_ROOT/platforms/android-35/android.jar"
  exit 1
fi

gradle assembleDebug
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
