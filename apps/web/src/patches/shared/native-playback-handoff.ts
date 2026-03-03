import { createPlaybackOpenCommand, sendHostCommand } from "../../host-bridge";
import type { NavigationContext, NativePlaybackSettings, NativePlaybackTracks } from "../../types/host-bridge";

type NativePlaybackOpenPayload = {
  streamId?: string;
  url: string;
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

declare global {
  interface Window {
    stremioShellNativePlayback?: {
      open: (payload: NativePlaybackOpenPayload) => void;
    };
    __stremioTvNavigationContext?: NavigationContext;
  }
}

const OPEN_EVENT_NAME = "stremio:native-playback-open";

function createStreamId(payload: NativePlaybackOpenPayload): string {
  if (payload.streamId && payload.streamId.trim().length > 0) {
    return payload.streamId;
  }

  return `${payload.url}:${Date.now()}`;
}

function dispatchNativePlayback(payload: NativePlaybackOpenPayload): void {
  const streamId = createStreamId(payload);
  const navigationContext = payload.navigationContext ?? window.__stremioTvNavigationContext;
  sendHostCommand(
    createPlaybackOpenCommand({
      streamId,
      url: payload.url,
      title: payload.title,
      subtitle: payload.subtitle,
      positionMs: payload.positionMs,
      artworkUrl: payload.artworkUrl,
      logoUrl: payload.logoUrl,
      resumePositionMs: payload.resumePositionMs,
      fallbackWebUrl: payload.fallbackWebUrl,
      settings: payload.settings,
      tracks: payload.tracks,
      navigationContext
    })
  );
}

export function installNativePlaybackHandoffBridge(): void {
  if (window.stremioShellNativePlayback) {
    return;
  }

  const open = (payload: NativePlaybackOpenPayload) => {
    if (!payload || typeof payload.url !== "string" || payload.url.trim().length === 0) {
      return;
    }
    dispatchNativePlayback(payload);
  };

  window.stremioShellNativePlayback = { open };

  window.addEventListener(OPEN_EVENT_NAME, (event: Event) => {
    const customEvent = event as CustomEvent<NativePlaybackOpenPayload>;
    if (!customEvent.detail) {
      return;
    }
    open(customEvent.detail);
  });
}
