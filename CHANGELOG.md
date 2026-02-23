# Changelog

## [0.1.1] - 2026-02-22
- Added deterministic Android TV back-handling handshake (`back.pressed` + `back.handled`) with timeout fallback.
- Hardened TV D-pad focus recovery, route-aware initial focus, deep-link host-event routing, and visible focus ring injection in TV shortcuts overlay.
- Improved host diagnostics export with dedicated host-event and back-decision sections.
- Added native player remote ergonomics improvements (media keys, menu/info handling, controller focus behavior, unsupported-setting notice).
- Extended host bridge contract/types/docs with `requestId` on `back.pressed`, plus `back.handled` and `updates.check` command coverage.
- Added Android instrumentation smoke tests and web TV smoke tests (`pnpm test:tv-smoke`) plus balanced manual TV QA matrix documentation.

## [0.1.0] - 2026-02-21
- Initial public release baseline for Stremio Shell Android host (`mobile` and `tv` flavors).
