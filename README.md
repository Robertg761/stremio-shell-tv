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
