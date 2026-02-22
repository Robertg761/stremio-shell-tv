# stremio-shell-tv

TV-focused monorepo for building a custom Stremio client with an Android/Google TV-first strategy.

## Repo layout

- `apps/web`: Staged upstream `stremio-web` bootstrap with shell patch overlays under `apps/web/src/patches/{shared,tv}`.
- `packages/core-bridge`: Typed wrapper around `@stremio/stremio-core-web` runtime APIs.
- `apps/android-tv-host`: Android/Google TV host integration code.
- `docs`: Contracts and quality gate docs.

## Quick start

```powershell
pnpm install
pnpm dev
```

Open the web shell at `https://localhost:5173`.

## Android TV builds

Set Android SDK/AVD paths to `G:` (current shell session):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-android-env.ps1
```

```powershell
pnpm android:web-build
pnpm android:tv:assemble
```

Install artifact:

- `apps/android-tv-host\app\build\outputs\apk\tv\debug\app-tv-debug.apk`

## GitHub release updates

This repo includes `.github/workflows/release.yml` for TV-only releases.

How it works:

1. Trigger on `main` pushes when release files change, or run manually via `workflow_dispatch`.
2. Read app version from `apps/android-tv-host/app/build.gradle.kts`.
3. Build web assets (`pnpm android:web-build`).
4. Build Android release APK for `tv`.
5. Create GitHub Release `v<version>` and upload `StremioShell-tv-<version>.apk`.

In-app updater behavior:

- Android host performs silent GitHub Releases checks on startup and via hourly background work in release builds.
- If a newer release exists, it auto-downloads the TV APK in the background.
- Debug builds do not perform automatic update checks.
- Update source defaults in `apps/android-tv-host/app/build.gradle.kts`:
  - `githubReleaseOwner` (default: `Robertg761`)
  - `githubReleaseRepo` (default: `stremio-shell-tv`)
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
- Shared runtime contract: `packages/core-bridge`.

Read these documents for execution governance:

- `docs/contracts/core-bridge.md`
- `docs/contracts/host-bridge.md`
- `docs/quality-gates.md`
- `docs/upstream-sync.md`
