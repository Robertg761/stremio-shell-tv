# TV Parity Checklist

This file keeps its historical name for existing links. The current parity
surface is the WebView shell plus native Android playback, not a checked-in
Compose UI.

Use this together with `docs/tv-qa-matrix.md` and `docs/release-ui-oracle.md`.

## Route parity matrix

| Route | Automated coverage | Device signoff | Evidence |
|---|---|---|---|
| Intro | PARTIAL | PENDING | `apps/web/src/patches/tv/upstream-overrides/src/routes/Intro/Intro.js` |
| Board | PARTIAL | PENDING | `apps/web/src/patches/tv/upstream-overrides/src/common/Shortcuts/__tests__/tvHostEvents.test.js` |
| Discover | PARTIAL | PENDING | `apps/web/src/patches/tv/upstream-overrides/src/common/Shortcuts/tvHostEvents.js` |
| Search | PARTIAL | PENDING | `apps/web/src/patches/tv/upstream-overrides/src/common/Shortcuts/tvHostEvents.js` |
| Meta Details | PARTIAL | PENDING | `apps/web/src/patches/tv/upstream-overrides/src/common/Shortcuts/__tests__/tvHostEvents.test.js` |
| Streams | PARTIAL | PENDING | `apps/web/src/patches/shared/native-playback-handoff.ts` |
| Player | PARTIAL | PENDING | `apps/android-tv-host/app/src/main/java/com/stremioshell/host/PlayerActivity.kt` |
| Library | PARTIAL | PENDING | `apps/web/src/patches/tv/upstream-overrides/src/common/Shortcuts/Shortcuts.tsx` |
| Addons | NONE | PENDING | `vendor/stremio-web/source/src/routes/Addons/Addons.js` |
| Calendar | NONE | PENDING | `vendor/stremio-web/source/src/routes/Calendar/Calendar.tsx` |
| Settings | PARTIAL | PENDING | `apps/web/src/patches/shared/upstream-overrides/src/routes/Settings/General/General.tsx` |
| NotFound fallback | NONE | PENDING | `vendor/stremio-web/source/src/router/Router/Router.js` |

## Runtime and host parity

| Item | Status | Evidence |
|---|---|---|
| Runtime initializes without WebView | AUTOMATED | `apps/android-tv-host/app/src/androidTest/java/com/stremioshell/host/core/RuntimeHarnessTest.kt` |
| Envelope dispatch works | AUTOMATED | `apps/android-tv-host/app/src/test/java/com/stremioshell/host/HostBridgeContractTest.kt` |
| Session/library/player state queries | AUTOMATED | `apps/android-tv-host/app/src/androidTest/java/com/stremioshell/host/core/RuntimeHarnessTest.kt` |
| Runtime soak stability | MANUAL | `apps/android-tv-host/app/src/androidTest/java/com/stremioshell/host/core/RuntimeSoakTest.kt` |

## TV UX parity

| Item | Status | Evidence |
|---|---|---|
| Initial focus visible on launch | INSTRUMENTED | `apps/android-tv-host/app/src/androidTest/java/com/stremioshell/host/MainActivityTvSmokeTest.kt` |
| D-pad traversal complete | MANUAL | `docs/tv-qa-matrix.md` |
| Back policy modal -> route -> exit | AUTOMATED + MANUAL | `apps/android-tv-host/app/src/test/java/com/stremioshell/host/BackHandshakeControllerTest.kt` |
| Update warning/check hooks | AUTOMATED + MANUAL | `apps/android-tv-host/app/src/test/java/com/stremioshell/host/update/AutoUpdatePolicyTest.kt` |

## Signoff evidence

- Build SHA:
- Device(s):
- Android API level(s):
- Remote profile:
- Tester:
- Date:
- P0/P1 parity defects:
- Artifact location:
- Notes:
