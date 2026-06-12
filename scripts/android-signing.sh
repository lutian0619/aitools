#!/usr/bin/env bash

ANDROID_DEBUG_KEYSTORE="${ANDROID_DEBUG_KEYSTORE:-${HOME:-/Users/gogogo}/.android/debug.keystore}"
ANDROID_DEBUG_KEYSTORE_SHA256="${ANDROID_DEBUG_KEYSTORE_SHA256:-9E:DB:23:63:FC:93:67:27:3E:61:35:59:F3:EC:76:35:A8:61:EB:3D:6F:D8:EC:B7:05:BA:C8:5D:A7:08:B6:A5}"

android_keystore_from_env() {
  local override="${1:-}"
  if [ -n "$override" ]; then
    printf '%s\n' "$override"
  else
    printf '%s\n' "$ANDROID_DEBUG_KEYSTORE"
  fi
}

verify_android_keystore() {
  local keystore="$1"
  if [ ! -f "$keystore" ]; then
    echo "Android signing keystore is missing: $keystore" >&2
    echo "Restore the expected keystore instead of generating a new one." >&2
    exit 1
  fi

  local actual_sha256
  actual_sha256="$(
    keytool -list -keystore "$keystore" -storepass android -alias androiddebugkey -v 2>/dev/null \
      | awk -F'SHA256: ' '/SHA256: / { print $2; exit }' \
      | tr '[:lower:]' '[:upper:]'
  )"

  if [ -z "$actual_sha256" ]; then
    echo "Unable to read Android signing certificate fingerprint from: $keystore" >&2
    exit 1
  fi

  if [ "$actual_sha256" != "$ANDROID_DEBUG_KEYSTORE_SHA256" ]; then
    echo "Android signing keystore fingerprint mismatch: $keystore" >&2
    echo "Expected SHA256: $ANDROID_DEBUG_KEYSTORE_SHA256" >&2
    echo "Actual SHA256:   $actual_sha256" >&2
    echo "Use the expected keystore, or intentionally set ANDROID_DEBUG_KEYSTORE_SHA256 with the matching fingerprint." >&2
    exit 1
  fi
}
