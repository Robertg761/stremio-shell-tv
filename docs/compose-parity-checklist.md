# Compose TV Parity Checklist

Use this together with `/Users/robert/Documents/Projects/stremio-shell-tv/docs/tv-qa-matrix.md` and `/Users/robert/Documents/Projects/stremio-shell-tv/docs/release-ui-oracle.md`.

## Route parity matrix

| Route | Visual parity | Behavior parity | Evidence |
|---|---|---|---|
| Intro | PENDING | PENDING | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Board | IN PROGRESS | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Discover | IN PROGRESS | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Search | IN PROGRESS | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Meta Details | IN PROGRESS | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Streams | IN PROGRESS | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Player | IN PROGRESS | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/player/ComposePlayerScreen.kt` |
| Library | IN PROGRESS | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Addons | IN PROGRESS | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Calendar | IN PROGRESS | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Settings | IN PROGRESS | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| NotFound fallback | IN PROGRESS | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |

## Runtime and core parity

| Item | Status | Evidence |
|---|---|---|
| Runtime initializes without WebView | PASS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/core/runtime/JsSandboxRuntimeHost.kt` |
| Envelope dispatch works | PASS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/core/CoreRuntimeModels.kt` |
| Session/library/player state queries | PASS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/core/repo/CoreRepositories.kt` |
| Runtime soak stability | PENDING | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/androidTest/java/com/stremioshell/host/core/RuntimeSoakTest.kt` |

## TV UX parity

| Item | Status | Evidence |
|---|---|---|
| Icon rail + top search shell | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/StremioTvApp.kt` |
| Focus visible + deterministic | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/StremioTvApp.kt` |
| D-pad traversal complete | PENDING | Manual QA matrix |
| Back policy modal -> route -> exit | PASS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/BackPolicyManager.kt` |
| Update warning banner parity | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScaffold.kt` |

## Screenshot parity regression

| Scenario | Status | Evidence |
|---|---|---|
| Board home capture and compare | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/androidTest/java/com/stremioshell/host/compose/UiParityScreenshotTest.kt` |
| API 26 run | PENDING | CI/device evidence |
| API 34 run | PENDING | CI/device evidence |

## Signoff evidence

- Build SHA:
- Device(s):
- Tester:
- Date:
- P0/P1 parity defects:
- Notes:
