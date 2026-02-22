# Upstream Sync Workflow

## Purpose

This repository tracks upstream `stremio-web` at a pinned commit and applies local shell patches in a separate layer.

## Source of truth

- Vendored source root: `vendor/stremio-web/source`
- Vendor metadata file: `vendor/stremio-web/VENDOR_METADATA.json`
- Local patch roots:
  - `apps/web/src/patches/shared`
  - `apps/web/src/patches/tv`

## Sync command

Run from repo root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/sync-upstream-stremio-web.ps1
```

Optional overrides:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/sync-upstream-stremio-web.ps1 `
  -Repository "https://github.com/Stremio/stremio-web.git" `
  -Commit "<commit-sha>"
```

## Rules

1. Never edit files inside `vendor/stremio-web/source` directly.
2. Keep all shell-specific changes inside `apps/web/src/patches/*`.
3. Breaking bridge or host contract changes require:
   - contract version bump,
   - contract doc update,
   - ADR update with migration notes.

## Web bootstrap build flow

- `apps/web` build/dev now stages `vendor/stremio-web/source` into `apps/web/.upstream-build/source`.
- Overlay patches from:
  - `apps/web/src/patches/shared/upstream-overrides`
  - `apps/web/src/patches/tv/upstream-overrides`
- Staged output is built with upstream webpack, then copied to `apps/web/dist`.
