#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-/Users/gogogo/Library/Android/sdk}"
BUILD_TOOLS="$SDK_ROOT/build-tools/35.0.0"
ANDROID_JAR="$SDK_ROOT/platforms/android-35/android.jar"
SIGNING_HELPER="$ROOT/../../scripts/android-signing.sh"
source "$SIGNING_HELPER"
KEYSTORE="$(android_keystore_from_env "${DIARY_KEYSTORE:-}")"

cd "$ROOT"

if [ ! -x "$BUILD_TOOLS/aapt2" ] || [ ! -f "$ANDROID_JAR" ]; then
  echo "Android SDK not found at: $SDK_ROOT" >&2
  echo "Set ANDROID_SDK_ROOT to an SDK that has build-tools/35.0.0 and platforms/android-35." >&2
  exit 1
fi

rm -rf build/manual
mkdir -p build/manual/gen build/manual/classes build/manual/dex build/outputs/apk/debug

cp app/src/main/AndroidManifest.xml build/manual/AndroidManifest.xml
perl -0pi -e 's/<manifest xmlns:android="http:\/\/schemas\.android\.com\/apk\/res\/android">/<manifest xmlns:android="http:\/\/schemas.android.com\/apk\/res\/android" package="com.minidiary">/' build/manual/AndroidManifest.xml

"$BUILD_TOOLS/aapt2" compile --dir app/src/main/res -o build/manual/compiled.zip
"$BUILD_TOOLS/aapt2" link \
  -o build/manual/resources.apk \
  -I "$ANDROID_JAR" \
  --manifest build/manual/AndroidManifest.xml \
  --java build/manual/gen \
  --min-sdk-version 23 \
  --target-sdk-version 35 \
  --version-code 22 \
  --version-name 3.1 \
  build/manual/compiled.zip \
  --auto-add-overlay

javac -encoding UTF-8 -source 8 -target 8 \
  -classpath "$ANDROID_JAR" \
  -d build/manual/classes \
  $(find app/src/main/java build/manual/gen -name '*.java')

"$BUILD_TOOLS/d8" \
  --lib "$ANDROID_JAR" \
  --output build/manual/dex \
  $(find build/manual/classes -name '*.class')

cp build/manual/resources.apk build/manual/unsigned.apk
zip -q -j build/manual/unsigned.apk build/manual/dex/classes.dex
"$BUILD_TOOLS/zipalign" -f 4 build/manual/unsigned.apk build/manual/aligned.apk

verify_android_keystore "$KEYSTORE"

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out build/outputs/apk/debug/app-debug.apk \
  build/manual/aligned.apk

"$BUILD_TOOLS/apksigner" verify --verbose build/outputs/apk/debug/app-debug.apk
echo "APK: $ROOT/build/outputs/apk/debug/app-debug.apk"
