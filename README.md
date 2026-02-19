# stremio-shell

Monorepo shell project for building a custom Stremio client with an Android/Google TV-first strategy and desktop hosts as secondary targets.

## Repo layout

- `apps/web`: Shared shell UI (React + Vite) that talks to `stremio-core` through `core-bridge`.
- `packages/core-bridge`: Typed wrapper around `@stremio/stremio-core-web` runtime APIs.
- `apps/android-tv-host`: Android/Google TV host integration notes and scripts.
- `apps/desktop-host`: Desktop host strategy (Tauri/Electron) notes and scripts.
- `docs`: ADRs, roadmap, and management conventions.

## Quick start

```powershell
pnpm install
pnpm dev
```

Open the web shell at `http://localhost:5173`.

## Android builds (mobile + TV)

Set Android SDK/AVD paths to `G:` (current shell session):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-android-env.ps1
```

```powershell
pnpm android:web-build
pnpm android:mobile:assemble
pnpm android:tv:assemble
```

Install artifacts with:

- `apps/android-tv-host\app\build\outputs\apk\mobile\debug\app-mobile-debug.apk`
- `apps/android-tv-host\app\build\outputs\apk\tv\debug\app-tv-debug.apk`

## Upstream stremio-web sync

```powershell
pnpm upstream:sync
```

This syncs pinned upstream source into `vendor/stremio-web/source` and updates `vendor/stremio-web/VENDOR_METADATA.json`.

## Using your local stremio-core fork

This repo is designed to consume either:

1. `@stremio/stremio-core-web` from npm, or
2. a tarball packed from your local `stremio-core/stremio-core-web` fork.

To switch to local fork output:

```powershell
pnpm core:use-local
```

The script packs your local fork and installs that package into `packages/core-bridge`.

## Delivery model

- Primary target: Android + Google TV (`apps/android-tv-host`).
- Secondary target: Windows + macOS (`apps/desktop-host`).
- Shared runtime contract: `packages/core-bridge`.

Read these documents for execution governance:

- `docs/roadmap.md`
- `docs/contracts/core-bridge.md`
- `docs/contracts/host-bridge.md`
- `docs/quality-gates.md`
- `docs/project-tracking.md`
- `docs/upstream-sync.md`
