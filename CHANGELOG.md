# Changelog

## [0.2.0] - 2026-07-19
- Fixed TV D-pad navigation dead ends on Board: overlay detection no longer lets substring selectors (e.g. the 49x49 nav-menu button matching `[class*="popup"]`) trap focus in an empty container.
- Made the sidebar reachable by D-pad: upstream nav tabs carry `tabindex="-1"` and were excluded from the focus candidate pool entirely.
- Promoted the zone-aware `tv_nav_v2` navigation engine to default (validated on an Android TV API 34 emulator); `tv_nav_v2=0` falls back to the legacy engine.
- Fixed zone transfers landing on the wrong element: transfers now only target elements that actually resolve to the destination zone.
- Made the app TV-only: removed the phantom `device` flavor dimension; Gradle tasks lose the `Tv` infix and the leanback launcher moved to the main manifest (applicationId keeps the `.tv` suffix so self-updates keep working).
- Hardened security: external URLs open as browsable-only intents; synthesized local streaming-server responses use an origin allowlist instead of wildcard CORS.
- Expanded tests: host-bridge envelope and `playback.open` normalization suites (web), navigation-core route/zone-transfer suite, `BackgroundUpdateWorker` retry policy, `NativePlaybackContracts` edge cases; Android instrumentation suite green on an API 34 TV emulator.
- Replaced Windows/macOS-only scripts with cross-platform Node tooling (`upstream:sync`, `core:use-local`, `android:tv:assemble`) and a Linux/macOS `scripts/android-env.sh`.
- CI now runs lint, web unit tests, TV smoke tests, Android JVM tests, and API 26/34 TV-emulator instrumentation; releases verify APK signatures before upload.
- Removed committed build outputs, QA captures, and release APKs from the repo (~100MB) with ignore rules to keep them out.

## [0.1.1] - 2026-02-22
- Added deterministic Android TV back-handling handshake (`back.pressed` + `back.handled`) with timeout fallback.
- Hardened TV D-pad focus recovery, route-aware initial focus, deep-link host-event routing, and visible focus ring injection in TV shortcuts overlay.
- Improved host diagnostics export with dedicated host-event and back-decision sections.
- Added native player remote ergonomics improvements (media keys, menu/info handling, controller focus behavior, unsupported-setting notice).
- Extended host bridge contract/types/docs with `requestId` on `back.pressed`, plus `back.handled` and `updates.check` command coverage.
- Added Android instrumentation smoke tests and web TV smoke tests (`pnpm test:tv-smoke`) plus balanced manual TV QA matrix documentation.

## [0.1.0] - 2026-02-21
- Initial public release baseline for Stremio Shell Android host (`mobile` and `tv` flavors).
