package com.stremioshell.host

import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

data class NativePlaybackTrack(
  val id: String? = null,
  val lang: String? = null,
  val label: String? = null,
  val origin: String? = null,
  val embedded: Boolean? = null,
  val mode: String? = null,
  val url: String? = null
)

data class NativePlaybackTracks(
  val audioTracks: List<NativePlaybackTrack> = emptyList(),
  val subtitlesTracks: List<NativePlaybackTrack> = emptyList(),
  val selectedAudioTrackId: String? = null,
  val selectedSubtitlesTrackId: String? = null
)

data class NativePlaybackSettings(
  val audioLanguage: String? = null,
  val surroundSound: Boolean? = null,
  val subtitlesLanguage: String? = null,
  val subtitlesSize: Int? = null,
  val subtitlesOffset: Int? = null,
  val subtitlesDelay: Long? = null,
  val subtitlesTextColor: String? = null,
  val subtitlesBackgroundColor: String? = null,
  val subtitlesOutlineColor: String? = null,
  val playbackSpeed: Float? = null,
  val hardwareDecoding: Boolean? = null,
  val assSubtitlesStyling: Boolean? = null,
  val videoMode: String? = null,
  val pauseOnMinimize: Boolean? = null,
  val nextVideoNotificationDuration: Long? = null,
  val bingeWatching: Boolean? = null,
  val raw: JSONObject = JSONObject()
)

data class NativePlaybackRequest(
  val streamId: String?,
  val url: String,
  val title: String?,
  val subtitle: String?,
  val positionMs: Long,
  val artworkUrl: String?,
  val logoUrl: String?,
  val resumePositionMs: Long?,
  val fallbackWebUrl: String?,
  val settings: NativePlaybackSettings,
  val tracks: NativePlaybackTracks,
  val sourceUrl: String?,
  val navigationContext: JSONObject? = null
)

object NativePlaybackContracts {
  fun fromPayload(payload: JSONObject): NativePlaybackRequest? {
    val url = payload.optStringOrNull("url") ?: return null
    if (url.isBlank()) {
      return null
    }

    val streamId = payload.optStringOrNull("streamId")
    val positionMs = payload.optLong("positionMs", 0L)
    val resumePositionMs = payload.optLong("resumePositionMs", -1L).takeIf { it >= 0L }

    return NativePlaybackRequest(
      streamId = streamId,
      url = url,
      title = payload.optStringOrNull("title"),
      subtitle = payload.optStringOrNull("subtitle"),
      positionMs = if (positionMs >= 0L) positionMs else 0L,
      artworkUrl = payload.optStringOrNull("artworkUrl"),
      logoUrl = payload.optStringOrNull("logoUrl"),
      resumePositionMs = resumePositionMs,
      fallbackWebUrl = payload.optStringOrNull("fallbackWebUrl"),
      settings = parseSettings(payload.optJSONObject("settings")),
      tracks = parseTracks(payload.optJSONObject("tracks")),
      sourceUrl = payload.optStringOrNull("sourceUrl"),
      navigationContext = payload.optJSONObject("navigationContext")?.copy()
    )
  }

  fun fromIntent(intent: Intent): NativePlaybackRequest? {
    val url = intent.getStringExtra(PlaybackBridge.EXTRA_URL).orEmpty().trim()
    if (url.isBlank()) {
      return null
    }

    val settingsJson = parseObject(intent.getStringExtra(PlaybackBridge.EXTRA_SETTINGS_JSON))
    val tracksJson = parseObject(intent.getStringExtra(PlaybackBridge.EXTRA_TRACKS_JSON))
    val navigationContextJson = parseObject(intent.getStringExtra(PlaybackBridge.EXTRA_NAVIGATION_CONTEXT_JSON))
    val resumePositionMs = intent.getLongExtra(PlaybackBridge.EXTRA_RESUME_POSITION_MS, -1L).takeIf { it >= 0L }
    val positionMs = intent.getLongExtra(PlaybackBridge.EXTRA_POSITION_MS, 0L)

    return NativePlaybackRequest(
      streamId = intent.getStringExtra(PlaybackBridge.EXTRA_STREAM_ID)?.ifBlank { null },
      url = url,
      title = intent.getStringExtra(PlaybackBridge.EXTRA_TITLE)?.ifBlank { null },
      subtitle = intent.getStringExtra(PlaybackBridge.EXTRA_SUBTITLE)?.ifBlank { null },
      positionMs = if (positionMs >= 0L) positionMs else 0L,
      artworkUrl = intent.getStringExtra(PlaybackBridge.EXTRA_ARTWORK_URL)?.ifBlank { null },
      logoUrl = intent.getStringExtra(PlaybackBridge.EXTRA_LOGO_URL)?.ifBlank { null },
      resumePositionMs = resumePositionMs,
      fallbackWebUrl = intent.getStringExtra(PlaybackBridge.EXTRA_FALLBACK_WEB_URL)?.ifBlank { null },
      settings = parseSettings(settingsJson),
      tracks = parseTracks(tracksJson),
      sourceUrl = intent.getStringExtra(PlaybackBridge.EXTRA_SOURCE_URL)?.ifBlank { null },
      navigationContext = navigationContextJson?.copy()
    )
  }

  fun settingsDiagnostics(settings: NativePlaybackSettings, applied: Map<String, Boolean>): JSONArray {
    val diagnostics = JSONArray()
    val keys = settings.raw.keys().asSequence().toList().sorted()
    keys.forEach { key ->
      val entry = JSONObject()
        .put("name", key)
        .put("applied", applied[key] == true)

      val value = settings.raw.opt(key)
      if (value != null && value != JSONObject.NULL) {
        entry.put("value", value)
      }

      if (applied[key] != true) {
        entry.put("reason", "unsupported_or_unavailable")
      }
      diagnostics.put(entry)
    }
    return diagnostics
  }

  private fun parseSettings(settings: JSONObject?): NativePlaybackSettings {
    val source = settings ?: JSONObject()
    return NativePlaybackSettings(
      audioLanguage = source.optString("audioLanguage").ifBlank { null },
      surroundSound = source.optBooleanOrNull("surroundSound"),
      subtitlesLanguage = source.optString("subtitlesLanguage").ifBlank { null },
      subtitlesSize = source.optIntOrNull("subtitlesSize"),
      subtitlesOffset = source.optIntOrNull("subtitlesOffset"),
      subtitlesDelay = source.optLongOrNull("subtitlesDelay"),
      subtitlesTextColor = source.optString("subtitlesTextColor").ifBlank { null },
      subtitlesBackgroundColor = source.optString("subtitlesBackgroundColor").ifBlank { null },
      subtitlesOutlineColor = source.optString("subtitlesOutlineColor").ifBlank { null },
      playbackSpeed = source.optFloatOrNull("playbackSpeed"),
      hardwareDecoding = source.optBooleanOrNull("hardwareDecoding"),
      assSubtitlesStyling = source.optBooleanOrNull("assSubtitlesStyling"),
      videoMode = source.optString("videoMode").ifBlank { null },
      pauseOnMinimize = source.optBooleanOrNull("pauseOnMinimize"),
      nextVideoNotificationDuration = source.optLongOrNull("nextVideoNotificationDuration"),
      bingeWatching = source.optBooleanOrNull("bingeWatching"),
      raw = source
    )
  }

  private fun parseTracks(tracks: JSONObject?): NativePlaybackTracks {
    val source = tracks ?: JSONObject()
    return NativePlaybackTracks(
      audioTracks = parseTrackList(source.optJSONArray("audioTracks")),
      subtitlesTracks = parseTrackList(source.optJSONArray("subtitlesTracks")),
      selectedAudioTrackId = source.optStringOrNull("selectedAudioTrackId"),
      selectedSubtitlesTrackId = source.optStringOrNull("selectedSubtitlesTrackId")
    )
  }

  private fun parseTrackList(items: JSONArray?): List<NativePlaybackTrack> {
    if (items == null) {
      return emptyList()
    }

    val tracks = mutableListOf<NativePlaybackTrack>()
    for (index in 0 until items.length()) {
      val track = items.optJSONObject(index) ?: continue
      tracks += NativePlaybackTrack(
        id = track.optStringOrNull("id"),
        lang = track.optStringOrNull("lang"),
        label = track.optStringOrNull("label"),
        origin = track.optStringOrNull("origin"),
        embedded = track.optBooleanOrNull("embedded"),
        mode = track.optStringOrNull("mode"),
        url = track.optStringOrNull("url")
      )
    }
    return tracks
  }

  private fun parseObject(value: String?): JSONObject? {
    if (value.isNullOrBlank()) {
      return null
    }
    return runCatching { JSONObject(value) }.getOrNull()
  }
}

private fun JSONObject.copy(): JSONObject {
  return JSONObject(this.toString())
}

private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
  if (!has(key) || isNull(key)) {
    return null
  }
  return optBoolean(key)
}

private fun JSONObject.optIntOrNull(key: String): Int? {
  if (!has(key) || isNull(key)) {
    return null
  }
  return optInt(key)
}

private fun JSONObject.optLongOrNull(key: String): Long? {
  if (!has(key) || isNull(key)) {
    return null
  }
  return optLong(key)
}

private fun JSONObject.optFloatOrNull(key: String): Float? {
  if (!has(key) || isNull(key)) {
    return null
  }
  return optDouble(key).toFloat()
}

private fun JSONObject.optStringOrNull(key: String): String? {
  if (!has(key) || isNull(key)) {
    return null
  }
  val value = optString(key).trim()
  if (value.isBlank() || value.equals("null", ignoreCase = true)) {
    return null
  }
  return value
}
