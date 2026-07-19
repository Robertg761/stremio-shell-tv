import test from "node:test";
import assert from "node:assert/strict";
import { createPlaybackOpenCommand } from "../host-bridge.ts";
import type { NativePlaybackSettings, NativePlaybackTracks, NavigationContext } from "../types/host-bridge.ts";

test("createPlaybackOpenCommand normalizes a partial payload with only required fields", () => {
  const command = createPlaybackOpenCommand({
    streamId: "test-stream-1",
    url: "https://example.com/video.mp4"
  });

  assert.equal(command.type, "playback.open");
  assert.deepEqual(command.payload, {
    streamId: "test-stream-1",
    url: "https://example.com/video.mp4",
    sourceUrl: undefined,
    title: undefined,
    subtitle: undefined,
    positionMs: undefined,
    artworkUrl: undefined,
    logoUrl: undefined,
    resumePositionMs: undefined,
    fallbackWebUrl: undefined
  });
});

test("createPlaybackOpenCommand normalizes a full payload with all basic optional fields", () => {
  const command = createPlaybackOpenCommand({
    streamId: "test-stream-2",
    url: "https://example.com/video.m3u8",
    sourceUrl: "https://example.com/source.m3u8",
    title: "Test Title",
    subtitle: "Test Subtitle",
    positionMs: 1000,
    artworkUrl: "https://example.com/artwork.jpg",
    logoUrl: "https://example.com/logo.png",
    resumePositionMs: 5000,
    fallbackWebUrl: "https://example.com/fallback"
  });

  assert.equal(command.type, "playback.open");
  assert.deepEqual(command.payload, {
    streamId: "test-stream-2",
    url: "https://example.com/video.m3u8",
    sourceUrl: "https://example.com/source.m3u8",
    title: "Test Title",
    subtitle: "Test Subtitle",
    positionMs: 1000,
    artworkUrl: "https://example.com/artwork.jpg",
    logoUrl: "https://example.com/logo.png",
    resumePositionMs: 5000,
    fallbackWebUrl: "https://example.com/fallback"
  });
});

test("createPlaybackOpenCommand carries settings, tracks, and navigationContext through", () => {
  const settings: NativePlaybackSettings = {
    audioLanguage: "eng",
    surroundSound: true,
    subtitlesSize: 100,
    playbackSpeed: 1,
    hardwareDecoding: true,
    pauseOnMinimize: false
  };
  const tracks: NativePlaybackTracks = {
    selectedAudioTrackId: "audio-1",
    selectedSubtitlesTrackId: "sub-1",
    audioTracks: [{ id: "audio-1", lang: "eng", label: "English" }],
    subtitlesTracks: [{ id: "sub-1", lang: "eng", label: "English (CC)" }]
  };
  const navigationContext: NavigationContext = {
    routeHash: "#/test",
    zone: "content",
    focusKey: "item-1",
    scrollY: 0,
    timestampMs: 1234567890,
    sessionId: "session-123"
  };

  const command = createPlaybackOpenCommand({
    streamId: "test-stream-3",
    url: "https://example.com/video.mp4",
    settings,
    tracks,
    navigationContext
  });

  assert.equal(command.type, "playback.open");
  assert.equal((command.payload as Record<string, unknown>).settings, settings);
  assert.equal((command.payload as Record<string, unknown>).tracks, tracks);
  assert.equal((command.payload as Record<string, unknown>).navigationContext, navigationContext);
});
