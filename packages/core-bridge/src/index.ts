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
} from "./types.js";

export type {
  ContractEnvelope,
  CoreAction,
  CoreEvent,
  CoreStateQuery,
  CoreStateSnapshot,
  JsonObject,
  JsonValue,
  TelemetryEvent
} from "./types.js";

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

type CoreModuleLoader = () => Promise<Record<string, unknown>>;

let coreModule: CoreWebModule | null = null;
const defaultCoreModuleLoader: CoreModuleLoader = async () => (await import("@stremio/stremio-core-web")) as Record<string, unknown>;
let coreModuleLoader: CoreModuleLoader = defaultCoreModuleLoader;

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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isEnvelopeLike(value: unknown): value is Record<string, unknown> {
  if (!isRecord(value)) {
    return false;
  }
  return (
    "type" in value &&
    "version" in value &&
    "payload" in value &&
    "timestampMs" in value
  );
}

function isStateSnapshotLike(value: unknown): value is Record<string, unknown> {
  if (!isRecord(value)) {
    return false;
  }
  return (
    "scope" in value &&
    "version" in value &&
    "updatedAtMs" in value &&
    "data" in value
  );
}

function requireCoreFunction<T>(
  imported: Record<string, unknown>,
  exportName: keyof CoreWebModule
): T {
  const candidate = imported[exportName];
  if (typeof candidate !== "function") {
    throw new CoreBridgeContractError(`@stremio/stremio-core-web export "${exportName}" must be a function.`);
  }
  return candidate as T;
}

function normalizeCoreEvent(event: unknown): CoreEvent {
  if (isContractEnvelope(event)) {
    return event as CoreEvent;
  }

  if (isEnvelopeLike(event)) {
    throw new CoreBridgeContractError("Runtime event resembles a contract envelope but failed validation.");
  }

  const payload = isJsonObject(event) ? { rawEvent: event } : { rawEvent: normalizeJsonValue(event) };
  return createEnvelope("runtime.raw", payload);
}

function normalizeStateSnapshot(query: CoreStateQuery, state: unknown): CoreStateSnapshot {
  if (isCoreStateSnapshot(state)) {
    return state;
  }

  if (isStateSnapshotLike(state)) {
    throw new CoreBridgeContractError("getState result resembles a CoreStateSnapshot but failed validation.");
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

  const imported = await coreModuleLoader();

  coreModule = {
    initialize_runtime: requireCoreFunction<CoreWebModule["initialize_runtime"]>(imported, "initialize_runtime"),
    dispatch: requireCoreFunction<CoreWebModule["dispatch"]>(imported, "dispatch"),
    get_state: requireCoreFunction<CoreWebModule["get_state"]>(imported, "get_state"),
    analytics: requireCoreFunction<CoreWebModule["analytics"]>(imported, "analytics"),
    decode_stream: requireCoreFunction<CoreWebModule["decode_stream"]>(imported, "decode_stream")
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

export function __setCoreModuleLoaderForTests(loader: CoreModuleLoader): void {
  coreModuleLoader = loader;
  coreModule = null;
}

export function __resetCoreModuleLoaderForTests(): void {
  coreModuleLoader = defaultCoreModuleLoader;
  coreModule = null;
}
