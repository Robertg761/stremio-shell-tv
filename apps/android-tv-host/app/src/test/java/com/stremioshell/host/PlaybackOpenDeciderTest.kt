package com.stremioshell.host

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackOpenDeciderTest {
  @Test
  fun `classify returns invalid payload when request is null`() {
    val payload = JSONObject()
      .put("streamId", "stream-1")
      .put("url", "https://cdn.example.com/master.m3u8")
      .put("fallbackWebUrl", "#/player/test")

    val outcome = PlaybackOpenDecider.classify(
      payload = payload,
      request = null,
      shouldSkipNativePlayback = false
    )

    assertEquals(PlaybackOpenDecision.INVALID_PAYLOAD, outcome.decision)
    assertEquals("stream-1", outcome.streamId)
    assertEquals("https://cdn.example.com/master.m3u8", outcome.url)
    assertEquals("#/player/test", outcome.fallbackWebUrl)
  }

  @Test
  fun `classify returns blocked by loop guard when skip flag is true`() {
    val request = NativePlaybackRequest(
      streamId = "stream-2",
      url = "https://cdn.example.com/movie.m3u8",
      title = null,
      subtitle = null,
      positionMs = 0L,
      artworkUrl = null,
      logoUrl = null,
      resumePositionMs = null,
      fallbackWebUrl = "#/player/fallback",
      settings = NativePlaybackSettings(),
      tracks = NativePlaybackTracks(),
      sourceUrl = null
    )

    val outcome = PlaybackOpenDecider.classify(
      payload = JSONObject(),
      request = request,
      shouldSkipNativePlayback = true
    )

    assertEquals(PlaybackOpenDecision.BLOCKED_BY_LOOP_GUARD, outcome.decision)
    assertEquals("stream-2", outcome.streamId)
    assertEquals("https://cdn.example.com/movie.m3u8", outcome.url)
    assertEquals("#/player/fallback", outcome.fallbackWebUrl)
  }

  @Test
  fun `classify returns opened when request is valid and skip flag is false`() {
    val request = NativePlaybackRequest(
      streamId = "stream-3",
      url = "https://cdn.example.com/episode.m3u8",
      title = null,
      subtitle = null,
      positionMs = 0L,
      artworkUrl = null,
      logoUrl = null,
      resumePositionMs = null,
      fallbackWebUrl = null,
      settings = NativePlaybackSettings(),
      tracks = NativePlaybackTracks(),
      sourceUrl = null
    )

    val outcome = PlaybackOpenDecider.classify(
      payload = JSONObject(),
      request = request,
      shouldSkipNativePlayback = false
    )

    assertEquals(PlaybackOpenDecision.OPENED, outcome.decision)
    assertEquals("stream-3", outcome.streamId)
    assertEquals("https://cdn.example.com/episode.m3u8", outcome.url)
    assertNull(outcome.fallbackWebUrl)
  }
}
