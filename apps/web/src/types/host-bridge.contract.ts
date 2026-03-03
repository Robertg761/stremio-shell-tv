import { createHostEnvelope, type HostCommand, type HostEvent } from "./host-bridge";

export const playbackOpenContractSample: HostCommand = createHostEnvelope("playback.open", {
  streamId: "stream-1",
  url: "https://cdn.example.com/video.m3u8",
  title: "Sample title",
  subtitle: "Sample subtitle",
  artworkUrl: "https://cdn.example.com/background.jpg",
  logoUrl: "https://cdn.example.com/logo.png",
  resumePositionMs: 12_000,
  fallbackWebUrl: "#/player/sample",
  settings: {
    audioLanguage: "eng",
    surroundSound: true,
    subtitlesSize: 100,
    playbackSpeed: 1,
    hardwareDecoding: true,
    pauseOnMinimize: true
  },
  tracks: {
    selectedAudioTrackId: "audio-eng",
    selectedSubtitlesTrackId: "subs-eng",
    audioTracks: [{ id: "audio-eng", lang: "eng", label: "English" }],
    subtitlesTracks: [{ id: "subs-eng", lang: "eng", label: "English CC" }]
  },
  navigationContext: {
    routeHash: "#/meta-details/tt123/tt123:1:2",
    zone: "content" as const,
    focusKey: "stream:1",
    scrollY: 420,
    timestampMs: Date.now(),
    sessionId: "tv-nav-session-1"
  }
});

export const playbackResultContractSample: HostEvent = createHostEnvelope("playback.result", {
  status: "failed" as const,
  streamId: "stream-1",
  errorCode: "native_audio_unavailable",
  message: "No playable audio renderer.",
  fallbackTriggered: true,
  failureDomain: "native_audio" as const,
  failureDetail: "ready_without_audio_track",
  resumePositionMs: 34_000,
  exitReason: "error" as const,
  navigationContext: {
    routeHash: "#/meta-details/tt123/tt123:1:2",
    zone: "content" as const,
    focusKey: "stream:1",
    scrollY: 420,
    timestampMs: Date.now(),
    sessionId: "tv-nav-session-1"
  }
});

export const backPressedContractSample: HostEvent = createHostEnvelope("back.pressed", {
  source: "hardware" as const,
  requestId: "req-123"
});

export const backHandledContractSample: HostCommand = createHostEnvelope("back.handled", {
  requestId: "req-123",
  handled: true,
  reason: "modal_close"
});
