import {
  CONTRACT_VERSION,
  createEnvelope,
  isContractEnvelope,
  isCoreStateQuery,
  isCoreStateSnapshot,
  isJsonObject,
  normalizeJsonValue,
  type CoreAction,
  type CoreEvent,
  type CoreStateQuery,
  type CoreStateSnapshot,
  type JsonObject,
  type JsonValue,
  type TelemetryEvent
} from "./types";

export type {
  ContractEnvelope,
  CoreAction,
  CoreEvent,
  CoreStateQuery,
  CoreStateSnapshot,
  JsonObject,
  JsonValue,
  TelemetryEvent
} from "./types";

export class CoreBridgeContractError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "CoreBridgeContractError";
  }
}

export interface CoreBridge {
  initializeRuntime(onEvent: (event: CoreEvent) => void): Promise<void>;
  dispatch(action: CoreAction, field?: JsonObject | null, locationHash?: string): void;
  getState(field: CoreStateQuery): CoreStateSnapshot;
  analytics(event: TelemetryEvent, locationHash?: string): void;
  decodeStream(streamBase64: string): JsonValue;
}

type CoreWebModule = {
  initialize_runtime: (emitToUi: (event: unknown) => void) => Promise<void>;
  dispatch: (action: unknown, field: unknown, locationHash: unknown) => void;
  get_state: (field: unknown) => unknown;
  analytics: (event: unknown, locationHash: unknown) => void;
  decode_stream: (stream: unknown) => unknown;
};

let coreModule: CoreWebModule | null = null;

function requireLocationHash(locationHash: unknown): string {
  if (locationHash === undefined) {
    return "";
  }

  if (typeof locationHash !== "string") {
    throw new CoreBridgeContractError("locationHash must be a string.");
  }

  return locationHash;
}

function requireJsonObject(value: unknown, label: string): JsonObject {
  if (!isJsonObject(value)) {
    throw new CoreBridgeContractError(`${label} must be a JSON object.`);
  }

  return value;
}

function requireEnvelope(value: unknown, label: string): void {
  if (!isContractEnvelope(value)) {
    throw new CoreBridgeContractError(
      `${label} must use envelope format { type, version, payload, timestampMs } with contract version ${CONTRACT_VERSION}.`
    );
  }
}

function normalizeCoreEvent(event: unknown): CoreEvent {
  if (isContractEnvelope(event)) {
    return event as CoreEvent;
  }

  const payload = isJsonObject(event) ? { rawEvent: event } : { rawEvent: normalizeJsonValue(event) };
  return createEnvelope("runtime.raw", payload);
}

function normalizeStateSnapshot(query: CoreStateQuery, state: unknown): CoreStateSnapshot {
  if (isCoreStateSnapshot(state)) {
    return state;
  }

  return {
    scope: query.scope,
    version: CONTRACT_VERSION,
    updatedAtMs: Date.now(),
    data: normalizeJsonValue(state)
  };
}

async function loadCoreModule(): Promise<CoreWebModule> {
  if (coreModule) {
    return coreModule;
  }

  const imported = (await import("@stremio/stremio-core-web")) as Record<string, unknown>;

  coreModule = {
    initialize_runtime: imported.initialize_runtime as CoreWebModule["initialize_runtime"],
    dispatch: imported.dispatch as CoreWebModule["dispatch"],
    get_state: imported.get_state as CoreWebModule["get_state"],
    analytics: imported.analytics as CoreWebModule["analytics"],
    decode_stream: imported.decode_stream as CoreWebModule["decode_stream"]
  };

  return coreModule;
}

export async function createCoreBridge(): Promise<CoreBridge> {
  const mod = await loadCoreModule();

  return {
    initializeRuntime: async (onEvent) => {
      await mod.initialize_runtime((event) => {
        onEvent(normalizeCoreEvent(event));
      });
    },
    dispatch: (action, field = null, locationHash = "") => {
      requireEnvelope(action, "action");
      if (field !== null) {
        requireJsonObject(field, "field");
      }
      const validatedLocationHash = requireLocationHash(locationHash);
      mod.dispatch(action, field, validatedLocationHash);
    },
    getState: (field) => {
      if (!isCoreStateQuery(field)) {
        throw new CoreBridgeContractError("getState field must match CoreStateQuery.");
      }
      const state = mod.get_state(field);
      return normalizeStateSnapshot(field, state);
    },
    analytics: (event, locationHash = "") => {
      requireEnvelope(event, "analytics event");
      const validatedLocationHash = requireLocationHash(locationHash);
      mod.analytics(event, validatedLocationHash);
    },
    decodeStream: (streamBase64) => {
      if (typeof streamBase64 !== "string" || streamBase64.length === 0) {
        throw new CoreBridgeContractError("streamBase64 must be a non-empty string.");
      }
      const decoded = mod.decode_stream(streamBase64);
      return normalizeJsonValue(decoded);
    }
  };
}
