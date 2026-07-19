# TV Route Parity Map

This file keeps its historical name for existing links. The current Android TV
host is a WebView shell with native playback handoff, not a checked-in Compose
route tree.

Reference baseline: `docs/release-ui-oracle.md`.

## Route implementation map

| Route | Current implementation | Status | Evidence |
|---|---|---|---|
| Intro | Upstream Stremio Web route with TV intro overlay patches | NEEDS DEVICE SIGNOFF | `apps/web/src/patches/tv/upstream-overrides/src/routes/Intro/Intro.js` |
| Board | Upstream Board route with TV shortcut/focus patch layer | NEEDS DEVICE SIGNOFF | `apps/web/src/patches/tv/upstream-overrides/src/common/Shortcuts/Shortcuts.tsx` |
| Discover | Upstream route rendered in WebView with route-aware focus selectors | NEEDS DEVICE SIGNOFF | `apps/web/src/patches/tv/upstream-overrides/src/common/Shortcuts/tvHostEvents.js` |
| Search | Upstream route rendered in WebView with TV keyboard/D-pad handling | NEEDS DEVICE SIGNOFF | `apps/web/src/patches/tv/upstream-overrides/src/common/Shortcuts/Shortcuts.tsx` |
| Meta Details | Upstream route rendered in WebView with stream focus selectors | NEEDS DEVICE SIGNOFF | `apps/web/src/patches/tv/upstream-overrides/src/common/Shortcuts/__tests__/tvHostEvents.test.js` |
| Streams | Upstream player-route handoff patch opens native playback when possible | NEEDS DEVICE SIGNOFF | `apps/web/src/patches/shared/native-playback-handoff.ts` |
| Player | Native Media3 player activity handles remote/media keys | NEEDS DEVICE SIGNOFF | `apps/android-tv-host/app/src/main/java/com/stremioshell/host/PlayerActivity.kt` |
| Library | Upstream route rendered in WebView with TV focus handling | NEEDS DEVICE SIGNOFF | `apps/web/src/patches/tv/upstream-overrides/src/common/Shortcuts/Shortcuts.tsx` |
| Addons | Upstream route rendered in WebView | NEEDS DEVICE SIGNOFF | `vendor/stremio-web/source/src/routes/Addons/Addons.js` |
| Calendar | Upstream route rendered in WebView | NEEDS DEVICE SIGNOFF | `vendor/stremio-web/source/src/routes/Calendar/Calendar.tsx` |
| Settings | Upstream route plus host update-check hooks | NEEDS DEVICE SIGNOFF | `apps/web/src/patches/shared/upstream-overrides/src/routes/Settings/General/General.tsx` |
| NotFound | Upstream router fallback in WebView | NEEDS DEVICE SIGNOFF | `vendor/stremio-web/source/src/router/Router/Router.js` |

## Cross-route parity behaviors

| Behavior | Status | Evidence |
|---|---|---|
| Bundled WebView shell loads current web build assets | AUTOMATED | `apps/android-tv-host/app/build.gradle.kts` |
| Host bridge command/event contract | AUTOMATED | `apps/android-tv-host/app/src/test/java/com/stremioshell/host/HostBridgeContractTest.kt` |
| D-pad focus selectors and route classification | AUTOMATED | `apps/web/src/patches/tv/upstream-overrides/src/common/Shortcuts/__tests__/tvHostEvents.test.js` |
| Back policy handshake and fallback | AUTOMATED | `apps/android-tv-host/app/src/test/java/com/stremioshell/host/BackHandshakeControllerTest.kt` |
| Deep-link host event dispatch | NEEDS DEVICE SIGNOFF | `apps/android-tv-host/app/src/main/java/com/stremioshell/host/MainActivity.kt` |
| Native playback loop guard/fallback | AUTOMATED | `apps/android-tv-host/app/src/test/java/com/stremioshell/host/NativePlaybackLoopGuardTest.kt` |
| Diagnostics export | NEEDS DEVICE SIGNOFF | `apps/android-tv-host/app/src/main/java/com/stremioshell/host/MainActivity.kt` |

## Remaining release gates

1. Run Android JVM tests with JDK 17: `./gradlew :app:testDebugUnitTest`.
2. Run Android instrumentation on API 26 and API 34 TV emulators.
3. Execute the manual TV QA matrix on at least one physical Google TV class device.
4. Attach QA screenshots/logs as CI or release artifacts, not committed repo files.
