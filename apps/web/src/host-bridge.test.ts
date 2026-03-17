import { describe, it, expect } from 'vitest';
import { createPlaybackOpenCommand } from './host-bridge';
import { type NativePlaybackSettings, type NativePlaybackTracks, type NavigationContext } from './types/host-bridge';

describe('createPlaybackOpenCommand', () => {
  it('normalizes a partial payload with only required fields', () => {
    const payload = {
      streamId: 'test-stream-1',
      url: 'https://example.com/video.mp4'
    };

    const command = createPlaybackOpenCommand(payload);

    expect(command.type).toBe('playback.open');
    expect(command.payload).toEqual({
      streamId: 'test-stream-1',
      url: 'https://example.com/video.mp4',
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

  it('normalizes a full payload with all basic optional fields', () => {
    const payload = {
      streamId: 'test-stream-2',
      url: 'https://example.com/video.m3u8',
      sourceUrl: 'https://example.com/source.m3u8',
      title: 'Test Title',
      subtitle: 'Test Subtitle',
      positionMs: 1000,
      artworkUrl: 'https://example.com/artwork.jpg',
      logoUrl: 'https://example.com/logo.png',
      resumePositionMs: 5000,
      fallbackWebUrl: 'https://example.com/fallback'
    };

    const command = createPlaybackOpenCommand(payload);

    expect(command.type).toBe('playback.open');
    expect(command.payload).toEqual({
      streamId: 'test-stream-2',
      url: 'https://example.com/video.m3u8',
      sourceUrl: 'https://example.com/source.m3u8',
      title: 'Test Title',
      subtitle: 'Test Subtitle',
      positionMs: 1000,
      artworkUrl: 'https://example.com/artwork.jpg',
      logoUrl: 'https://example.com/logo.png',
      resumePositionMs: 5000,
      fallbackWebUrl: 'https://example.com/fallback'
    });
  });

  it('normalizes a payload with settings, tracks, and navigationContext', () => {
    const settings: NativePlaybackSettings = {
      audioLanguage: 'eng',
      surroundSound: true,
      subtitlesSize: 100,
      playbackSpeed: 1,
      hardwareDecoding: true,
      pauseOnMinimize: false
    };

    const tracks: NativePlaybackTracks = {
      selectedAudioTrackId: 'audio-1',
      selectedSubtitlesTrackId: 'sub-1',
      audioTracks: [{ id: 'audio-1', lang: 'eng', label: 'English' }],
      subtitlesTracks: [{ id: 'sub-1', lang: 'eng', label: 'English (CC)' }]
    };

    const navigationContext: NavigationContext = {
      routeHash: '#/test',
      zone: 'content',
      focusKey: 'item-1',
      scrollY: 0,
      timestampMs: 1234567890,
      sessionId: 'session-123'
    };

    const payload = {
      streamId: 'test-stream-3',
      url: 'https://example.com/video.mp4',
      settings,
      tracks,
      navigationContext
    };

    const command = createPlaybackOpenCommand(payload);

    expect(command.type).toBe('playback.open');
    expect(command.payload).toEqual({
      streamId: 'test-stream-3',
      url: 'https://example.com/video.mp4',
      sourceUrl: undefined,
      title: undefined,
      subtitle: undefined,
      positionMs: undefined,
      artworkUrl: undefined,
      logoUrl: undefined,
      resumePositionMs: undefined,
      fallbackWebUrl: undefined,
      settings: settings as any,
      tracks: tracks as any,
      navigationContext: navigationContext as any
    });
  });
});
