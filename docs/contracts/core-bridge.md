# Core Bridge Contract

## Purpose

`packages/core-bridge` is the typed runtime contract between UI and `@stremio/stremio-core-web`.

## Contract versioning

- Current contract version: `1`.
- Envelope shape (required):
  - `type: string`
  - `version: 1`
  - `payload: object` (JSON-serializable)
  - `timestampMs: number`
- Breaking changes require:
  - Version increment.
  - Update to this document.
  - ADR update describing migration path.

## Type sources

Source of truth is `packages/core-bridge/src/types.ts`.

Primary exported types:

- `CoreAction`
- `CoreEvent`
- `CoreStateQuery`
- `CoreStateSnapshot`
- `TelemetryEvent`
- `ContractEnvelope`

## CoreAction (v1)

Supported action envelope types:

- `runtime.initialize`
- `auth.login`
- `auth.logout`
- `library.sync`
- `playback.selectStream`
- `playback.reportProgress`
- `custom.*` extension namespace for temporary/experimental actions

## CoreEvent (v1)

Supported event envelope types:

- `runtime.initialized`
- `runtime.error`
- `runtime.raw`
- `auth.changed`
- `library.changed`
- `playback.progress`
- `telemetry.event`

`runtime.raw` is a compatibility event used when incoming runtime data is not already envelope-shaped.

## CoreState contracts (v1)

`CoreStateQuery`:

- `scope`: one of `session | library | player | addons | custom`
- `key` optional string
- `params` optional JSON object

`CoreStateSnapshot`:

- `scope`: same domain as query scope
- `version`: `1`
- `updatedAtMs`: number timestamp
- `data`: JSON value snapshot payload

## Runtime guardrails

`core-bridge` rejects:

- Actions or telemetry events that do not follow envelope shape.
- Envelopes with invalid version.
- Non-JSON payloads.
- Invalid `getState` queries.

Guardrail failures raise `CoreBridgeContractError`.
