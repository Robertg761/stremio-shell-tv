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
  resumePositionMs: 34_000
});
