# Host Bridge Contract

## Purpose

Defines web-to-host commands and host-to-web events for native wrappers.

## Version

- Current version: `1`.
- Envelope shape:
  - `type: string`
  - `version: 1`
  - `payload: object` (JSON-serializable)
  - `timestampMs: number`

## Source files

- Web types: `apps/web/src/types/host-bridge.ts`
- Android mirror: `apps/android-tv-host/host-bridge-contract.json`

## Host events

- `lifecycle.changed`
- `network.changed`
- `back.pressed`
- `deepLink.received`
- `playback.result`

## Host commands

- `playback.open`
- `playback.close`
- `external.openUrl`
- `diagnostics.export`

## Versioning rule

Breaking changes require:

1. Version increment.
2. Update to both source files above.
3. ADR update with migration notes.
