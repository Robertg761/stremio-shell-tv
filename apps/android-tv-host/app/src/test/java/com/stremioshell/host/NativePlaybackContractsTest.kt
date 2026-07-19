package com.stremioshell.host

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePlaybackContractsTest {
  @Test
  fun `fromPayload parses extended playback open payload`() {
    val payload = JSONObject()
      .put("streamId", "stream-123")
      .put("url", "https://cdn.example.com/master.m3u8")
      .put("title", "Sample")
      .put("subtitle", "Episode 1")
      .put("sourceUrl", "https://127.0.0.1:11470/hlsv2/stream-123/index.m3u8")
      .put("artworkUrl", "https://cdn.example.com/bg.jpg")
      .put("logoUrl", "https://cdn.example.com/logo.png")
      .put("resumePositionMs", 22_000L)
      .put("fallbackWebUrl", "#/player/example")
      .put("settings", JSONObject().put("audioLanguage", "eng").put("surroundSound", true))
      .put("tracks", JSONObject().put("selectedAudioTrackId", "audio-eng"))
      .put(
        "navigationContext",
        JSONObject()
          .put("routeHash", "#/meta-details/tt123")
          .put("zone", "content")
          .put("focusKey", "stream-card:3")
          .put("scrollY", 318)
          .put("sessionId", "sess-1")
      )

    val request = NativePlaybackContracts.fromPayload(payload)

    assertNotNull(request)
    assertEquals("stream-123", request?.streamId)
    assertEquals("https://cdn.example.com/master.m3u8", request?.url)
    assertEquals("https://127.0.0.1:11470/hlsv2/stream-123/index.m3u8", request?.sourceUrl)
    assertEquals("eng", request?.settings?.audioLanguage)
    assertEquals(true, request?.settings?.surroundSound)
    assertEquals("audio-eng", request?.tracks?.selectedAudioTrackId)
    assertEquals("#/player/example", request?.fallbackWebUrl)
    assertEquals("#/meta-details/tt123", request?.navigationContext?.optString("routeHash"))
    assertEquals("stream-card:3", request?.navigationContext?.optString("focusKey"))
  }

  @Test
  fun `fromPayload returns null when url is blank`() {
    val payload = JSONObject().put("streamId", "stream-123").put("url", "   ")
    assertNull(NativePlaybackContracts.fromPayload(payload))
  }

  @Test
  fun `fromPayload with minimal fields applies defaults`() {
    val payload = JSONObject().put("streamId", "stream-123").put("url", "https://cdn.example.com/master.m3u8")
    val request = NativePlaybackContracts.fromPayload(payload)
    assertNotNull(request)
    assertEquals("https://cdn.example.com/master.m3u8", request?.url)
    assertEquals("stream-123", request?.streamId)
    assertEquals(0L, request?.positionMs)
    assertNull(request?.resumePositionMs)
  }

  @Test
  fun `fromPayload bounds negative positions correctly`() {
    val payload = JSONObject().put("streamId", "stream-123").put("url", "https://cdn.example.com/master.m3u8").put("positionMs", -100L).put("resumePositionMs", -200L)
    val request = NativePlaybackContracts.fromPayload(payload)
    assertNotNull(request)
    assertEquals(0L, request?.positionMs)
    assertNull(request?.resumePositionMs)
  }

  @Test
  fun `fromPayload returns null when url missing`() {
    val payload = JSONObject().put("streamId", "stream-123")
    assertNull(NativePlaybackContracts.fromPayload(payload))
  }

  @Test
  fun `settingsDiagnostics marks unapplied settings`() {
    val settings = NativePlaybackSettings(
      audioLanguage = "eng",
      surroundSound = true,
      raw = JSONObject().put("audioLanguage", "eng").put("surroundSound", true)
    )

    val diagnostics = NativePlaybackContracts.settingsDiagnostics(
      settings = settings,
      applied = mapOf("audioLanguage" to true, "surroundSound" to false)
    )

    val first = diagnostics.getJSONObject(0)
    val second = diagnostics.getJSONObject(1)
    val firstApplied = first.getBoolean("applied")
    val secondApplied = second.getBoolean("applied")

    assertTrue(firstApplied || secondApplied)
    assertFalse(firstApplied && secondApplied)
  }
}
