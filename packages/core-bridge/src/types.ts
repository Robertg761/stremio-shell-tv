export const CONTRACT_VERSION = 1 as const;

export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonValue[] | JsonObject;
export type JsonObject = { [key: string]: JsonValue };

export interface ContractEnvelope<TType extends string, TPayload extends JsonObject = JsonObject> {
  type: TType;
  version: typeof CONTRACT_VERSION;
  payload: TPayload;
  timestampMs: number;
}

export type RuntimeInitializedEvent = ContractEnvelope<"runtime.initialized", { source: "core-bridge" | "stremio-core" }>;
export type RuntimeErrorEvent = ContractEnvelope<
  "runtime.error",
  { code: string; message: string; recoverable: boolean; details?: JsonObject }
>;
export type RuntimeRawEvent = ContractEnvelope<"runtime.raw", { rawEvent: JsonValue }>;
export type AuthChangedEvent = ContractEnvelope<"auth.changed", { isAuthenticated: boolean; userId?: string | null }>;
export type LibraryChangedEvent = ContractEnvelope<
  "library.changed",
  { itemCount?: number; changedItemIds?: string[]; reason?: "sync" | "mutation" | "unknown" }
>;
export type PlaybackProgressEvent = ContractEnvelope<"playback.progress", { streamId: string; progressMs: number }>;
export type TelemetryEvent = ContractEnvelope<
  "telemetry.event",
  { name: string; level: "debug" | "info" | "warn" | "error"; context?: JsonObject }
>;

export type CoreEvent =
  | RuntimeInitializedEvent
  | RuntimeErrorEvent
  | RuntimeRawEvent
  | AuthChangedEvent
  | LibraryChangedEvent
  | PlaybackProgressEvent
  | TelemetryEvent;

export type RuntimeInitializeAction = ContractEnvelope<"runtime.initialize", { source: "web-shell" | "host" }>;
export type AuthLoginAction = ContractEnvelope<
  "auth.login",
  { method: "email_password" | "oauth" | "token"; token?: string; email?: string }
>;
export type AuthLogoutAction = ContractEnvelope<"auth.logout", { reason?: "user" | "expired" | "forced" }>;
export type LibrarySyncAction = ContractEnvelope<"library.sync", { force?: boolean }>;
export type PlaybackSelectStreamAction = ContractEnvelope<
  "playback.selectStream",
  { streamId: string; streamBase64: string; autoPlay?: boolean }
>;
export type PlaybackReportProgressAction = ContractEnvelope<
  "playback.reportProgress",
  { streamId: string; progressMs: number; durationMs?: number }
>;

export type CoreAction =
  | RuntimeInitializeAction
  | AuthLoginAction
  | AuthLogoutAction
  | LibrarySyncAction
  | PlaybackSelectStreamAction
  | PlaybackReportProgressAction
  | ContractEnvelope<`custom.${string}`, JsonObject>;

export interface CoreStateQuery {
  scope: "session" | "library" | "player" | "addons" | "custom";
  key?: string;
  params?: JsonObject;
}

export interface CoreStateSnapshot {
  scope: CoreStateQuery["scope"];
  version: typeof CONTRACT_VERSION;
  updatedAtMs: number;
  data: JsonValue;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

export function isJsonValue(value: unknown): value is JsonValue {
  if (value === null) {
    return true;
  }

  const type = typeof value;
  if (type === "string" || type === "boolean") {
    return true;
  }
  if (type === "number") {
    return Number.isFinite(value);
  }

  if (Array.isArray(value)) {
    return value.every((item) => isJsonValue(item));
  }

  if (isRecord(value)) {
    return Object.values(value).every((item) => isJsonValue(item));
  }

  return false;
}

export function isJsonObject(value: unknown): value is JsonObject {
  return isRecord(value) && !Array.isArray(value) && isJsonValue(value);
}

export function isContractEnvelope(value: unknown): value is ContractEnvelope<string, JsonObject> {
  if (!isRecord(value)) {
    return false;
  }

  return (
    typeof value.type === "string" &&
    value.version === CONTRACT_VERSION &&
    isJsonObject(value.payload) &&
    typeof value.timestampMs === "number" &&
    Number.isFinite(value.timestampMs)
  );
}

export function isCoreStateQuery(value: unknown): value is CoreStateQuery {
  if (!isRecord(value) || typeof value.scope !== "string") {
    return false;
  }

  const allowed = new Set<CoreStateQuery["scope"]>(["session", "library", "player", "addons", "custom"]);
  if (!allowed.has(value.scope as CoreStateQuery["scope"])) {
    return false;
  }

  if (value.key !== undefined && typeof value.key !== "string") {
    return false;
  }

  if (value.params !== undefined && !isJsonObject(value.params)) {
    return false;
  }

  return true;
}

export function isCoreStateSnapshot(value: unknown): value is CoreStateSnapshot {
  if (!isRecord(value)) {
    return false;
  }

  return (
    typeof value.scope === "string" &&
    value.version === CONTRACT_VERSION &&
    typeof value.updatedAtMs === "number" &&
    Number.isFinite(value.updatedAtMs) &&
    isJsonValue(value.data)
  );
}

export function createEnvelope<TType extends string, TPayload extends JsonObject>(
  type: TType,
  payload: TPayload,
  timestampMs = Date.now()
): ContractEnvelope<TType, TPayload> {
  return {
    type,
    version: CONTRACT_VERSION,
    payload,
    timestampMs
  };
}

export function normalizeJsonValue(value: unknown): JsonValue {
  if (isJsonValue(value)) {
    return value;
  }

  return String(value);
}
