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
  - `updates.check`
  - `back.handled`
    - Payload: `{ requestId, handled, reason? }`
- Host event dispatch to web shell:
  - `lifecycle.changed`
  - `network.changed`
  - `back.pressed`
    - Payload includes `requestId` for deterministic acknowledgement.
  - `deepLink.received`
  - `playback.result`
    - Optional fallback diagnostics fields: `fallbackTriggered`, `failureDomain`, `failureDetail`, `settingsDiagnostics`.
- Native playback path via Media3 ExoPlayer.

## Build and run

Prerequisites:

- Android SDK installed and `ANDROID_HOME` set.
- JDK 17 installed and active (`java -version` should report 17).
- Web shell built (`apps/web/dist`).

Build steps:

```bash
# 0) Set up JDK 17 / Android SDK paths (Linux/macOS)
source scripts/android-env.sh

# 1) Build web assets used by Android release/debug fallback
pnpm --filter @stremio-shell/web build

# 2) Build Android TV variant
cd apps/android-tv-host
./gradlew :app:assembleDebug
./gradlew :app:assembleDebug -PwebAppUrl=http://10.0.2.2:5173
```

On Windows use `.\gradlew.bat` instead, or run `pnpm android:tv:assemble` from
the repo root on any platform.

Install to connected device/emulator:

```bash
./gradlew :app:installDebug
```

## TV-only app

The app is Android TV-only (single variant, no flavors): the manifest requires
leanback, marks touchscreen as not required, and registers the Leanback
launcher alias with the TV banner.

## Generated assets

Gradle packages web and core runtime assets from
`app/build/generated/assets/main`. The source path
`app/src/main/assets/web` is ignored and should remain uncommitted so stale
hashed web bundles do not accumulate in release APKs.
