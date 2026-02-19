# Android + Google TV Host

This folder now contains a functional Android host app that supports both phone/tablet (`mobile`) and Android TV (`tv`) variants from one codebase.

## Host bridge contract

Use `apps/android-tv-host/host-bridge-contract.json` as the source of truth for Android-to-web host events and web-to-Android host commands.

- Contract version is pinned to `1`.
- Envelope shape is `{ type, version, payload, timestampMs }`.
- Payloads must remain JSON-serializable.

## Planned implementation

1. Create an Android app shell (`minSdk 26+`) with a full-screen WebView.
2. Load the built shell bundle from local assets for release.
3. Add Android TV focus strategy (D-pad traversal, visible focus states).
4. Map native lifecycle and connectivity events into JS host bridge.
5. Integrate playback handoff to ExoPlayer or native media APIs.

## What is implemented

- `mobile` and `tv` product flavors, producing two installable apps:
  - `com.stremioshell.host.mobile`
  - `com.stremioshell.host.tv`
- Full-screen WebView host loading:
  - Debug: `http://10.0.2.2:5173` (falls back to bundled assets if unavailable).
  - Release: bundled `apps/web/dist` assets.
- JS bridge command support:
  - `playback.open`
  - `playback.close`
  - `external.openUrl`
  - `diagnostics.export`
- Host event dispatch to web shell:
  - `lifecycle.changed`
  - `network.changed`
  - `back.pressed`
  - `deepLink.received`
  - `playback.result`
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

# 2) Build Android variants
cd apps/android-tv-host
.\gradlew.bat :app:assembleMobileDebug
.\gradlew.bat :app:assembleTvDebug
```

Install to connected device/emulator:

```powershell
.\gradlew.bat :app:installMobileDebug
.\gradlew.bat :app:installTvDebug
```

## Flavor behavior

- `mobile` flavor has standard launcher category.
- `tv` flavor has Leanback launcher category and TV banner.
