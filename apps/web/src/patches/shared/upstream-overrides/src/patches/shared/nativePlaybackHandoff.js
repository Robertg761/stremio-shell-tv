const NATIVE_HANDOFF_DEDUP_MS = 1200;

let lastHandoffKey = null;
let lastHandoffAtMs = 0;

function firstString(values) {
  for (const value of values) {
    if (typeof value !== "string") {
      continue;
    }
    const trimmed = value.trim();
    if (trimmed.length > 0) {
      return trimmed;
    }
  }
  return null;
}

function isPlainObject(value) {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function toMilliseconds(seconds) {
  if (typeof seconds !== "number" || !Number.isFinite(seconds) || seconds < 0) {
    return undefined;
  }
  return Math.round(seconds * 1000);
}

function formatSeasonEpisode(seriesInfo) {
  if (!seriesInfo || typeof seriesInfo !== "object") {
    return null;
  }
  const season = typeof seriesInfo.season === "number" ? seriesInfo.season : null;
  const episode = typeof seriesInfo.episode === "number" ? seriesInfo.episode : null;
  if (season === null || episode === null) {
    return null;
  }
  return `S${season}E${episode}`;
}

function resolvePlaybackUrl(player) {
  const externalPlayer = player?.selected?.stream?.deepLinks?.externalPlayer;
  const openPlayer = externalPlayer?.openPlayer;
  const readyStream = player?.stream?.type === "Ready" ? player.stream.content : null;

  const candidate = firstString([
    externalPlayer?.streaming,
    openPlayer?.android,
    openPlayer?.androidtv,
    openPlayer?.androidTv,
    externalPlayer?.href,
    readyStream?.url,
    player?.selected?.stream?.externalUrl,
    player?.selected?.stream?.url
  ]);

  if (!candidate) {
    return null;
  }

  return /^https?:\/\//i.test(candidate) ? candidate : null;
}

function resolveStreamId(player, url) {
  const path = player?.selected?.streamRequest?.path;
  const resource = firstString([path?.resource, "stream"]);
  const type = firstString([path?.type, "video"]);
  const id = firstString([path?.id, player?.selected?.stream?.name, url]);
  return `${resource}:${type}:${id}`;
}

function resolveTitle(player, metaItem) {
  return firstString([player?.title, metaItem?.name, player?.selected?.stream?.name, "Stremio"]);
}

function resolveSubtitle(player, metaItem) {
  const seasonEpisode = formatSeasonEpisode(player?.seriesInfo);
  if (seasonEpisode && typeof metaItem?.name === "string" && metaItem.name.trim().length > 0) {
    return `${metaItem.name} ${seasonEpisode}`;
  }
  return firstString([player?.selected?.stream?.description, seasonEpisode]);
}

function normalizeTrack(track, embedded) {
  if (!isPlainObject(track)) {
    return null;
  }

  const id = firstString([track.id, track.url]);
  if (!id) {
    return null;
  }

  return {
    id,
    lang: firstString([track.lang]),
    label: firstString([track.label, track.title, track.name, track.url]),
    origin: firstString([track.origin]),
    embedded,
    mode: firstString([track.mode]),
    url: firstString([track.url])
  };
}

function createTracksSnapshot(player) {
  const selectedAudioTrackId = firstString([player?.streamState?.audioTrack?.id]);
  const selectedSubtitlesTrackId = firstString([player?.streamState?.subtitleTrack?.id]);

  const audioTracks = [];
  const embeddedAudioTracks = Array.isArray(player?.selected?.stream?.audioTracks)
    ? player.selected.stream.audioTracks
    : [];

  for (const track of embeddedAudioTracks) {
    const normalized = normalizeTrack(track, true);
    if (normalized) {
      audioTracks.push(normalized);
    }
  }

  const subtitlesTracks = [];
  const seenSubtitleIds = new Set();

  const embeddedSubtitles = Array.isArray(player?.selected?.stream?.subtitles) ? player.selected.stream.subtitles : [];
  const extraSubtitles = Array.isArray(player?.subtitles) ? player.subtitles : [];

  for (const track of embeddedSubtitles) {
    const normalized = normalizeTrack(track, true);
    if (!normalized || seenSubtitleIds.has(normalized.id)) {
      continue;
    }
    seenSubtitleIds.add(normalized.id);
    subtitlesTracks.push(normalized);
  }

  for (const track of extraSubtitles) {
    const normalized = normalizeTrack(track, false);
    if (!normalized || seenSubtitleIds.has(normalized.id)) {
      continue;
    }
    seenSubtitleIds.add(normalized.id);
    subtitlesTracks.push(normalized);
  }

  const payload = {};
  if (audioTracks.length > 0) {
    payload.audioTracks = audioTracks;
  }
  if (subtitlesTracks.length > 0) {
    payload.subtitlesTracks = subtitlesTracks;
  }
  if (selectedAudioTrackId) {
    payload.selectedAudioTrackId = selectedAudioTrackId;
  }
  if (selectedSubtitlesTrackId) {
    payload.selectedSubtitlesTrackId = selectedSubtitlesTrackId;
  }
  return payload;
}

function createSettingsSnapshot(settings, forceTranscoding, streamingServer) {
  const snapshot = isPlainObject(settings) ? { ...settings } : {};
  if (typeof snapshot.playbackSpeed !== "number") {
    snapshot.playbackSpeed = 1;
  }
  snapshot.forceTranscoding = forceTranscoding === true;
  const streamingServerUrl = firstString([streamingServer?.selected?.transportUrl, streamingServer?.baseUrl]);
  if (streamingServerUrl) {
    snapshot.streamingServerUrl = streamingServerUrl;
  }
  return snapshot;
}

function shouldSkipNativeForUrl(url) {
  const skipMap = globalThis.__stremioNativeSkipUrls;
  if (!isPlainObject(skipMap)) {
    return false;
  }

  const skipUntil = skipMap[url];
  return typeof skipUntil === "number" && Number.isFinite(skipUntil) && Date.now() < skipUntil;
}

function shouldDedup(key) {
  const now = Date.now();
  if (lastHandoffKey === key && now - lastHandoffAtMs < NATIVE_HANDOFF_DEDUP_MS) {
    return true;
  }
  lastHandoffKey = key;
  lastHandoffAtMs = now;
  return false;
}

function openNativePlaybackForStream({ player, settings, forceTranscoding, streamingServer } = {}) {
  const nativePlaybackBridge = globalThis?.stremioShellNativePlayback;
  if (!nativePlaybackBridge || typeof nativePlaybackBridge.open !== "function") {
    return false;
  }

  const mediaUrl = resolvePlaybackUrl(player);
  if (!mediaUrl) {
    return false;
  }

  if (shouldSkipNativeForUrl(mediaUrl)) {
    return false;
  }

  const streamId = resolveStreamId(player, mediaUrl);
  const dedupKey = `${streamId}|${mediaUrl}`;
  if (shouldDedup(dedupKey)) {
    return true;
  }

  const metaItem = player?.metaItem?.type === "Ready" ? player.metaItem.content : null;
  const resumePositionMs = toMilliseconds(player?.libraryItem?.state?.timeOffset);
  const fallbackWebUrl = typeof window !== "undefined" ? window.location.href : undefined;
  const sourceUrl = firstString([player?.selected?.stream?.deepLinks?.player]);

  const payload = {
    streamId,
    url: mediaUrl,
    sourceUrl: sourceUrl ?? undefined,
    title: resolveTitle(player, metaItem),
    subtitle: resolveSubtitle(player, metaItem) ?? undefined,
    positionMs: resumePositionMs,
    resumePositionMs,
    artworkUrl: firstString([metaItem?.background]) ?? undefined,
    logoUrl: firstString([metaItem?.logo]) ?? undefined,
    fallbackWebUrl,
    settings: createSettingsSnapshot(settings, forceTranscoding, streamingServer),
    tracks: createTracksSnapshot(player)
  };

  try {
    nativePlaybackBridge.open(payload);
    return true;
  } catch (error) {
    console.error("nativePlaybackHandoff", error);
    return false;
  }
}

module.exports = {
  openNativePlaybackForStream
};
