package com.stremioshell.host.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRuntimeModelsTest {
  @Test
  fun `create envelope keeps expected shape`() {
    val action = RuntimeInitializeAction(source = "host")
    val envelope = action.toEnvelope().toJson()

    assertEquals("runtime.initialize", envelope.getString("type"))
    assertEquals(CORE_CONTRACT_VERSION, envelope.getInt("version"))
    assertTrue(envelope.getLong("timestampMs") > 0L)
    assertEquals("host", envelope.getJSONObject("payload").getString("source"))
  }

  @Test
  fun `parser maps playback progress event`() {
    val json = JSONObject()
      .put("type", "playback.progress")
      .put("version", CORE_CONTRACT_VERSION)
      .put("payload", JSONObject().put("streamId", "s1").put("progressMs", 1200L))
      .put("timestampMs", System.currentTimeMillis())

    val event = CoreEnvelopeParser.parseEvent(json)
    assertTrue(event is PlaybackProgressEvent)
    val progress = event as PlaybackProgressEvent
    assertEquals("s1", progress.streamId)
    assertEquals(1200L, progress.progressMs)
  }

  @Test
  fun `parser maps library changed array ids`() {
    val payload = JSONObject()
      .put("itemCount", 4)
      .put("changedItemIds", JSONArray().put("a").put("b"))
      .put("reason", "sync")

    val json = JSONObject()
      .put("type", "library.changed")
      .put("version", CORE_CONTRACT_VERSION)
      .put("payload", payload)
      .put("timestampMs", System.currentTimeMillis())

    val event = CoreEnvelopeParser.parseEvent(json)
    assertTrue(event is LibraryChangedEvent)
    val library = event as LibraryChangedEvent
    assertEquals(4, library.itemCount)
    assertEquals(listOf("a", "b"), library.changedItemIds)
    assertEquals("sync", library.reason)
  }
}
