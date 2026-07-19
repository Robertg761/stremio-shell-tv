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

```bash
pnpm upstream:sync
```

Optional overrides:

```bash
node scripts/sync-upstream-stremio-web.mjs \
  --repository "https://github.com/Stremio/stremio-web.git" \
  --commit "<commit-sha>"
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
- Android packages web assets from generated Gradle assets under
  `apps/android-tv-host/app/build/generated/assets/main/web`.
- Do not commit generated Android web assets under
  `apps/android-tv-host/app/src/main/assets/web`; that path is ignored to avoid
  stale hashed bundles and oversized APK inputs.
