# Release UI Oracle (`v0.1.1`)

This document defines the source of truth for Compose TV parity.

## Canonical reference build

- Version: `v0.1.1`
- Published: `2026-02-23T00:14:04Z`
- APK reference: `/Users/robert/Documents/Projects/stremio-shell-tv/docs/ui-compare/StremioShell-tv-0.1.1-release.apk`

## Oracle screenshots

- Release home oracle: `/Users/robert/Documents/Projects/stremio-shell-tv/docs/ui-compare/release-build-home.png`
- Current local comparison: `/Users/robert/Documents/Projects/stremio-shell-tv/docs/ui-compare/current-local-home.png`

## Visual tokens extracted from release CSS

Source: `assets/web/c02a2bcaaeb259511c1fb07bb61414da3abea528/styles/main.css` inside the release APK.

- `--primary-background-color`: `#0c0b11`
- `--secondary-background-color`: `#1a173e`
- `--primary-accent-color`: `#7b5bf5`
- `--secondary-accent-color`: `#22b365`
- `--overlay-color`: `rgba(255,255,255,0.05)`
- `--modal-background-color`: `#0f0d20`
- `--focus-outline-size`: `2px`
- `--border-radius`: `0.75rem`
- `--horizontal-nav-bar-size`: `5.5rem`
- `--vertical-nav-bar-size`: `6rem`
- Search bar size: `3.25rem` height, `30rem` top-bar target width
- Poster shape ratio (`--poster-shape-ratio`): `1.464`

## Required home-screen structure

- Left icon-first vertical navigation rail.
- Top horizontal bar with search field and utility actions.
- Main content region with horizontal media rows and skeleton placeholders.
- Bottom warning/update banner surface with action chips.

## Parity validation rule

A route is considered parity-complete only when both checks pass:

1. Visual parity check against oracle screenshots.
2. Behavioral parity check against release interactions (focus/back/remote/actions).
