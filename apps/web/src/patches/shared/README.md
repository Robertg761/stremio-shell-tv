# Shared Patch Layer

Place shell-specific upstream overrides used by both Android phone and Android TV in this folder.

Examples:

- bridge adapters
- telemetry wrappers
- diagnostics hooks
- native playback handoff adapters

Use `upstream-overrides/` for file overlays that are copied into staged
`vendor/stremio-web/source` during `apps/web` build/dev bootstrap.
