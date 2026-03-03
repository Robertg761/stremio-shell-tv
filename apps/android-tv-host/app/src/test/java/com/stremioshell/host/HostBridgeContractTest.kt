package com.stremioshell.host

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostBridgeContractTest {
  @Test
  fun `createPlaybackPayload includes additive navigation fields when provided`() {
    val navigationContext = JSONObject()
      .put("routeHash", "#/meta-details/tt123")
      .put("zone", "content")
      .put("focusKey", "stream-card:7")
      .put("scrollY", 512)
      .put("sessionId", "session-abc")

    val payload = HostBridgeContract.createPlaybackPayload(
      status = "paused",
      streamId = "stream-1",
      resumePositionMs = 1234L,
      exitReason = "user_back",
      navigationContext = navigationContext
    )

    assertEquals("paused", payload.getString("status"))
    assertEquals("stream-1", payload.getString("streamId"))
    assertEquals("user_back", payload.getString("exitReason"))
    assertTrue(payload.has("navigationContext"))
    assertEquals("stream-card:7", payload.getJSONObject("navigationContext").getString("focusKey"))
  }
}
