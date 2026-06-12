# Repository Guidelines

## Project Structure

This repository combines personal tools under one LAN service.

- `server/server.js`: shared Node HTTP service for Web entrypoints, APK downloads, UDP discovery, and sync APIs.
- `server/config/tools.json`: tool registry.
- `web/`: computer-side Web console, shared Web shell, and per-tool Web clients.
- `web/tools/tracker/`: daily-grid Web client.
- `web/tools/diary/`: diary Web client.
- `android/tracker-app/`: standalone Android daily-grid app.
- `android/diary-app/`: standalone Android diary app.
- `android/print-app/`: standalone Android quick print app.
- `android/metronome-app/`: standalone Android metronome app.
- `android/market-app/`: standalone Android tool market app.
- `artifacts/apk/`: generated APKs served to phones.
- `sync/`: runtime sync data. Treat it as private local data, not source.

## Commands

- `scripts/start-server.sh`: start the shared LAN service at `http://127.0.0.1:8788/web/`.
- `scripts/build-tracker-apk.sh`: build daily-grid debug APK and copy it to `artifacts/apk/tracker-debug.apk`.
- `scripts/build-diary-apk.sh`: build diary debug APK and copy it to `artifacts/apk/diary-debug.apk`.
- `scripts/build-print-apk.sh`: build print debug APK and copy it to `artifacts/apk/print-debug.apk`.
- `scripts/build-metronome-apk.sh`: build metronome debug APK and copy it to `artifacts/apk/metronome-debug.apk`.
- `scripts/build-market-apk.sh`: build tool market debug APK and copy it to `artifacts/apk/market-debug.apk`.
- `scripts/build-all-apks.sh`: build all registered Android APKs.

## Android Signing

All Android APK builds must use the same debug signing key so installed apps can
be upgraded in place.

- Default keystore: `~/.android/debug.keystore`.
- Shared signing helper: `scripts/android-signing.sh`.
- Required SHA-256 fingerprint:

```text
9E:DB:23:63:FC:93:67:27:3E:61:35:59:F3:EC:76:35:A8:61:EB:3D:6F:D8:EC:B7:05:BA:C8:5D:A7:08:B6:A5
```

- Every `android/*-app/scripts/build-apk-manual.sh` must call
  `verify_android_keystore "$KEYSTORE"` before `apksigner sign`.
- Do not default to `/private/tmp/*.keystore`.
- Do not auto-generate a new keystore during APK builds. If the keystore is
  missing or the fingerprint differs, fail the build and restore the expected
  key.
- App-specific override env vars such as `TRACKER_KEYSTORE`, `DIARY_KEYSTORE`,
  and `MARKET_KEYSTORE` are allowed only when the resulting certificate
  fingerprint still matches the expected fingerprint, unless the signing
  migration is intentional and documented.

## Coding Style

Use plain JavaScript for the Node service and Web clients. Use the existing plain Java Android style in each app. Keep UI copy in Chinese and keep tool behavior independent unless explicitly sharing platform functionality.

## UI / Design Contract

When changing Web or Android UI, style, layout, navigation, or product pages:

- Read and follow `web/DESIGN_SYSTEM.md` before editing. Treat it as a contract, not a suggestion.
- Web pages must reuse `/web/app-shell.css`, `/web/product-switcher.css`, `/web/product-switcher.js`, and the shared shell structure: `.shellbar`, `.shellbrand`, `.shelltitle`, `.shellactions`.
- Do not create product-specific top bars, product switchers, or core palette tokens outside the contract.
- Run `node scripts/validate-design-contract.js` before finishing UI work and fix any failures.
- If Android UI colors or shared visual rules change, update `web/DESIGN_SYSTEM.md` and rebuild the affected APKs.

## Cross-Client Parity

When changing sync, APK update, discovery, or tool registration behavior, consider both Web and Android clients. If only one side is changed, document the reason and follow-up.

Tool market centralizes APK install/update/open behavior. Business apps should not add their own APK update flows. Only apps that sync data with the Web service should keep computer-service discovery, and only for sync.

## Security

The server is intended for trusted LAN use. Do not expose it directly to the public internet. Do not commit APKs, private sync ZIPs, keystores, local SDK paths, or generated build output.
