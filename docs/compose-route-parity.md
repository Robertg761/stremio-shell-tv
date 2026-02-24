# Compose Route Parity Map

Reference baseline: `/Users/robert/Documents/Projects/stremio-shell-tv/docs/release-ui-oracle.md`.

## Route implementation map

| Route | Compose implementation | Current status | Evidence |
|---|---|---|---|
| Intro | Routed to Board-style home surface while preserving route contract | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Board | Release-like rails and row placeholders (`Popular - Movie`, `Popular - Series`) | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Discover | Release-like recommendation rails and row navigation | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Search | Top-bar query surface + results route content | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Meta Details | Meta title/subtitle plus stream cards | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Streams | Stream cards wired to player open action | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Player | Compose Media3 player path with key handling and diagnostics | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/player/ComposePlayerScreen.kt` |
| Library | Continue-watching row and library sync metadata | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Addons | Installed/catalog rows + install/remove actions | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Calendar | Upcoming row content | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| Settings | Settings row and update hooks | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |
| NotFound | Recovery hint to return home | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScreens.kt` |

## Cross-route parity behaviors

| Behavior | Status | Evidence |
|---|---|---|
| Top bar search + left icon rail shell | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/StremioTvApp.kt` |
| Bottom warning/update banner | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/screens/RouteScaffold.kt` |
| Focus restore and tracing | IN PROGRESS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/StremioTvApp.kt` |
| Back policy ordering | PASS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/BackPolicyManager.kt` |
| Deep-link routing | PASS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/navigation/DeepLinkRouter.kt` |
| Diagnostics export sections | PASS | `/Users/robert/Documents/Projects/stremio-shell-tv/apps/android-tv-host/app/src/main/java/com/stremioshell/host/compose/DiagnosticsStore.kt` |

## Remaining release gates

1. Run screenshot parity tests on API 26 and API 34 devices/emulators.
2. Execute full TV QA matrix for D-pad traversal and player key behavior.
3. Resolve all P0/P1 parity defects before cutover.
