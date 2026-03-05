import {
  createHostEnvelope,
  isHostEnvelope,
  type HostCommand,
  type HostEvent,
  type JsonObject,
  type NavigationContext,
  type NativePlaybackSettings,
  type NativePlaybackTracks
} from "./types/host-bridge";

type HostEventListener = (event: HostEvent) => void;

declare global {
  interface Window {
    stremioHost?: {
      sendCommand: (commandJson: string) => void;
    };
  }
}

const HOST_EVENT_NAME = "stremio:host-event";
const HOST_COMMAND_NAME = "stremio:host-command";

export function subscribeToHostEvents(listener: HostEventListener): () => void {
  const handler = (event: Event) => {
    const customEvent = event as CustomEvent<unknown>;
    if (!isHostEnvelope(customEvent.detail)) {
      return;
    }
    listener(customEvent.detail as HostEvent);
  };

  window.addEventListener(HOST_EVENT_NAME, handler as EventListener);
  return () => window.removeEventListener(HOST_EVENT_NAME, handler as EventListener);
}

export function sendHostCommand(command: HostCommand): void {
  const commandJson = JSON.stringify(command);

  if (window.stremioHost) {
    window.stremioHost.sendCommand(commandJson);
    return;
  }

  // Fallback for browser-only development before native host wiring.
  window.dispatchEvent(new CustomEvent(HOST_COMMAND_NAME, { detail: command }));
}

export function createDiagnosticsExportCommand(reason: "manual" | "error" | "support" = "manual"): HostCommand {
  return createHostEnvelope("diagnostics.export", { reason });
}

export function createBackHandledCommand(
  requestId: string,
  handled: boolean,
  reason?: string
): HostCommand {
  const payload: {
    requestId: string;
    handled: boolean;
    reason?: string;
  } = {
    requestId,
    handled
  };
  if (typeof reason === "string" && reason.length > 0) {
    payload.reason = reason;
  }

  return createHostEnvelope("back.handled", payload);
}

type PlaybackOpenPayload = {
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
  settings?: NativePlaybackSettings;
  tracks?: NativePlaybackTracks;
  navigationContext?: NavigationContext;
};

export function createPlaybackOpenCommand(payload: PlaybackOpenPayload): HostCommand {
  const normalizedPayload: {
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
  } = {
    streamId: payload.streamId,
    url: payload.url,
    sourceUrl: payload.sourceUrl,
    title: payload.title,
    subtitle: payload.subtitle,
    positionMs: payload.positionMs,
    artworkUrl: payload.artworkUrl,
    logoUrl: payload.logoUrl,
    resumePositionMs: payload.resumePositionMs,
    fallbackWebUrl: payload.fallbackWebUrl
  };

  if (payload.settings) {
    normalizedPayload.settings = payload.settings as unknown as JsonObject;
  }

  if (payload.tracks) {
    normalizedPayload.tracks = payload.tracks as unknown as JsonObject;
  }

  if (payload.navigationContext) {
    normalizedPayload.navigationContext = payload.navigationContext as unknown as JsonObject;
  }

  return createHostEnvelope("playback.open", normalizedPayload);
}
