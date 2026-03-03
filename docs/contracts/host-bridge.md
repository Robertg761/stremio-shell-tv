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
  - Payload:
    - `source: "hardware" | "remote" | "gesture"`
    - `requestId: string`
- `deepLink.received`
- `playback.result`
  - Optional additive fields for diagnostics/fallback:
    - `fallbackTriggered: boolean`
    - `failureDomain: "native_audio" | "network" | "decode" | "unsupported" | "host" | "unknown"`
    - `failureDetail: string`
    - `settingsDiagnostics: Array<object>`
    - `exitReason: "user_back" | "user_exit" | "end" | "error" | "background"`
    - `navigationContext: { routeHash?, zone?, focusKey?, scrollY?, timestampMs?, sessionId? }`
  - Host behavior notes:
    - When native playback launch is skipped by host loop guard, host emits `status: "failed"` with:
      - `fallbackTriggered: true`
      - `failureDomain: "host"`
      - `failureDetail: "loop_guard_skip"`
      - passthrough `streamId` / `url` / `fallbackWebUrl` when available

## Host commands

- `playback.open`
  - Required:
    - `streamId: string`
    - `url: string`
  - Optional additive fields:
    - `artworkUrl: string`
    - `logoUrl: string`
    - `resumePositionMs: number`
    - `fallbackWebUrl: string`
    - `settings: object`
    - `tracks: object`
    - `navigationContext: { routeHash?, zone?, focusKey?, scrollY?, timestampMs?, sessionId? }`
- `playback.close`
- `external.openUrl`
- `diagnostics.export`
- `updates.check`
  - Payload:
    - `reason?: "manual" | "startup" | "background"`
- `back.handled`
  - Payload:
    - `requestId: string`
    - `handled: boolean`
    - `reason?: string`

## Versioning rule

Breaking changes require:

1. Version increment.
2. Update to both source files above.
3. ADR update with migration notes.
