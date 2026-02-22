# Android TV Host

This folder contains the Android TV host app for Stremio Shell TV.

## Host bridge contract

Use `apps/android-tv-host/host-bridge-contract.json` as the source of truth for Android-to-web host events and web-to-Android host commands.

- Contract version is pinned to `1`.
- Envelope shape is `{ type, version, payload, timestampMs }`.
- Payloads must remain JSON-serializable.

## What is implemented

- `tv` flavor app package:
  - `com.stremioshell.host.tv`
- Full-screen WebView host loading:
  - Debug default: bundled `apps/web/dist` shell (override with `-PwebAppUrl=...` when needed).
  - Debug override: pass `-PwebAppUrl=http://10.0.2.2:5173` to target local Vite dev shell.
  - Release: bundled `apps/web/dist` assets.
  - Startup watchdog + diagnostics overlay prevents silent black screens and offers retry/export actions.
  - Remote fallback URL (`https://web.stremio.com/`) is used only when local shell startup fails.
- JS bridge command support:
  - `playback.open`
    - Optional metadata/settings payloads: artwork/logo/resume/fallback URL/settings/tracks.
  - `playback.close`
  - `external.openUrl`
  - `diagnostics.export`
- Host event dispatch to web shell:
  - `lifecycle.changed`
  - `network.changed`
  - `back.pressed`
  - `deepLink.received`
  - `playback.result`
    - Optional fallback diagnostics fields: `fallbackTriggered`, `failureDomain`, `failureDetail`, `settingsDiagnostics`.
- Native playback path via Media3 ExoPlayer.

## Build and run

Prerequisites:

- Android SDK installed and `ANDROID_HOME` set.
- JDK 17 installed.
- Web shell built (`apps/web/dist`).

Build steps:

```powershell
# 1) Build web assets used by Android release/debug fallback
pnpm --filter @stremio-shell/web build

# 2) Build Android TV variant
cd apps/android-tv-host
.\gradlew.bat :app:assembleTvDebug
.\gradlew.bat :app:assembleTvDebug -PwebAppUrl=http://10.0.2.2:5173
```

Install to connected device/emulator:

```powershell
.\gradlew.bat :app:installTvDebug
```

## Flavor behavior

- `tv` flavor has Leanback launcher category and TV banner.
