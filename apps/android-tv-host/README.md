# Android + Google TV Host

This folder defines the Android/Google TV host track. It intentionally starts as a thin host because the shared shell logic lives in `apps/web`.

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

## Suggested first execution step

Generate the native project once Android SDK/JDK paths are finalized:

```powershell
# example next step (not run by default)
# npx @capacitor/cli create apps/android-tv-host com.yourorg.stremioshell "Stremio Shell"
```

Track delivery tasks in `docs/roadmap.md` Milestone 1 and 2.
