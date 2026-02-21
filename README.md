# stremio-shell

Monorepo shell project for building a custom Stremio client with an Android/Google TV-first strategy and desktop hosts as secondary targets.

## Repo layout

- `apps/web`: Staged upstream `stremio-web` bootstrap with shell patch overlays under `apps/web/src/patches/*`.
- `packages/core-bridge`: Typed wrapper around `@stremio/stremio-core-web` runtime APIs.
- `apps/android-tv-host`: Android/Google TV host integration notes and scripts.
- `apps/desktop-host`: Desktop host strategy (Tauri/Electron) notes and scripts.
- `docs`: ADRs, roadmap, and management conventions.

## Quick start

```powershell
pnpm install
pnpm dev
```

Open the web shell at `https://localhost:5173`.

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

## GitHub release updates (LunarLog-style)

This repo now includes `.github/workflows/release.yml`, modeled after your LunarLog release flow and adapted for `stremio-shell`.

How it works:

1. Trigger on `main` pushes when release-related files change (`apps/android-tv-host/app/build.gradle.kts`, `CHANGELOG.md`, release scripts/workflow), or run manually via `workflow_dispatch`.
2. Read app version from `apps/android-tv-host/app/build.gradle.kts`.
3. Build web assets (`pnpm android:web-build`).
4. Build signed Android release APKs for both flavors:
   - `mobile`
   - `tv`
5. Create GitHub Release `v<version>` and upload:
   - `StremioShell-mobile-<version>.apk`
   - `StremioShell-tv-<version>.apk`

Required GitHub repository secrets:

- `SS_KEYSTORE_B64`
- `SS_SIGNING_STORE_PASSWORD`
- `SS_SIGNING_KEY_ALIAS`
- `SS_SIGNING_KEY_PASSWORD`

Optional:

- `SS_SIGNING_STORE_TYPE` (defaults to `PKCS12` in CI)

In-app updater behavior:

- Android host performs a silent GitHub Releases check on startup in release builds.
- Android host also exposes a native `Check updates` button (top-right overlay) for manual checks.
- If a newer release exists, it prompts to download and then install the matching flavor APK (`mobile` or `tv`).
- Update source repo is configured in `apps/android-tv-host/app/build.gradle.kts` via:
  - `githubReleaseOwner` (default: `Robertg761`)
  - `githubReleaseRepo` (default: `stremio-shell`)
- Override at build time if needed:
  - `-PgithubReleaseOwner=<owner> -PgithubReleaseRepo=<repo>`

Before pushing a release:

1. Bump `versionCode` and `versionName` in `apps/android-tv-host/app/build.gradle.kts`.
2. Add a matching version section in `CHANGELOG.md`:
   - `## [x.y.z] - YYYY-MM-DD`
3. Push to `main` (or run workflow manually).

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
