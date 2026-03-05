export const HOST_BRIDGE_VERSION = 1 as const;

export type JsonPrimitive = string | number | boolean | null;
export type JsonValue = JsonPrimitive | JsonValue[] | JsonObject;
export type JsonObject = { [key: string]: JsonValue };

export interface HostEnvelope<TType extends string, TPayload extends JsonObject> {
  type: TType;
  version: typeof HOST_BRIDGE_VERSION;
  payload: TPayload;
  timestampMs: number;
}

export interface PlaybackTrack {
  id?: string;
  lang?: string;
  label?: string;
  origin?: string;
  embedded?: boolean;
  mode?: string;
  url?: string;
}

export interface NativePlaybackSettings {
  audioLanguage?: string | null;
  surroundSound?: boolean;
  subtitlesLanguage?: string | null;
  subtitlesSize?: number;
  subtitlesOffset?: number;
  subtitlesDelay?: number;
  subtitlesTextColor?: string | null;
  subtitlesBackgroundColor?: string | null;
  subtitlesOutlineColor?: string | null;
  playbackSpeed?: number;
  hardwareDecoding?: boolean;
  assSubtitlesStyling?: boolean;
  videoMode?: string | null;
  pauseOnMinimize?: boolean;
  nextVideoNotificationDuration?: number;
  bingeWatching?: boolean;
}

export interface NativePlaybackTracks {
  audioTracks?: PlaybackTrack[];
  subtitlesTracks?: PlaybackTrack[];
  selectedAudioTrackId?: string | null;
  selectedSubtitlesTrackId?: string | null;
}

export interface NavigationContext {
  routeHash?: string;
  zone?: "sidebar" | "topbar" | "content" | "overlay" | "unknown";
  focusKey?: string;
  scrollY?: number;
  timestampMs?: number;
  sessionId?: string;
}

export type HostEvent =
  | HostEnvelope<"lifecycle.changed", { state: "created" | "started" | "resumed" | "paused" | "stopped" | "destroyed" }>
  | HostEnvelope<"network.changed", { connected: boolean; transport?: "wifi" | "ethernet" | "cellular" | "unknown" }>
  | HostEnvelope<"back.pressed", { source: "hardware" | "remote" | "gesture"; requestId: string }>
  | HostEnvelope<"deepLink.received", { url: string }>
  | HostEnvelope<
      "playback.result",
      {
        status: "started" | "paused" | "resumed" | "completed" | "failed";
        streamId?: string;
        errorCode?: string;
        message?: string;
        url?: string;
        fallbackWebUrl?: string;
        resumePositionMs?: number;
        fallbackTriggered?: boolean;
        failureDomain?: "native_audio" | "network" | "decode" | "unsupported" | "host" | "unknown";
        failureDetail?: string;
        settingsDiagnostics?: JsonValue[];
        exitReason?: "user_back" | "user_exit" | "end" | "error" | "background";
        navigationContext?: JsonObject;
      }
    >;

export type HostCommand =
  | HostEnvelope<
      "playback.open",
      {
        streamId: string;
        url: string;
        sourceUrl?: string;
        title?: string;
        subtitle?: string;
        positionMs?: number;
        artworkUrl?: string;
        logoUrl?: string;
        resumePositionMs?: number;
        fallbackWebUrl?: string;
        settings?: JsonObject;
        tracks?: JsonObject;
        navigationContext?: JsonObject;
      }
    >
  | HostEnvelope<"playback.close", { reason?: "user" | "end" | "error" }>
  | HostEnvelope<"external.openUrl", { url: string }>
  | HostEnvelope<"diagnostics.export", { reason?: "manual" | "error" | "support" }>
  | HostEnvelope<"updates.check", { reason?: "manual" | "startup" | "background" }>
  | HostEnvelope<"back.handled", { requestId: string; handled: boolean; reason?: string }>;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isJsonObject(value: unknown): value is JsonObject {
  return isRecord(value) && !Array.isArray(value) && isJsonValue(value);
}

function isJsonValue(value: unknown): value is JsonValue {
  if (value === null) {
    return true;
  }

  const valueType = typeof value;
  if (valueType === "string" || valueType === "number" || valueType === "boolean") {
    return true;
  }

  if (Array.isArray(value)) {
    return value.every((item) => isJsonValue(item));
  }

  if (isRecord(value)) {
    return Object.values(value).every((item) => isJsonValue(item));
  }

  return false;
}

export function isHostEnvelope(value: unknown): value is HostEnvelope<string, JsonObject> {
  if (!isRecord(value)) {
    return false;
  }

  return (
    typeof value.type === "string" &&
    value.version === HOST_BRIDGE_VERSION &&
    isJsonObject(value.payload) &&
    typeof value.timestampMs === "number" &&
    Number.isFinite(value.timestampMs)
  );
}

export function createHostEnvelope<TType extends string, TPayload extends JsonObject>(
  type: TType,
  payload: TPayload,
  timestampMs = Date.now()
): HostEnvelope<TType, TPayload> {
  return {
    type,
    version: HOST_BRIDGE_VERSION,
    payload,
    timestampMs
  };
}
