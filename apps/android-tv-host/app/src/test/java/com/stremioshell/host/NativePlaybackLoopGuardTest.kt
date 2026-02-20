package com.stremioshell.host

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePlaybackLoopGuardTest {
  @Test
  fun `marks urls and stream ids for temporary skip`() {
    val guard = NativePlaybackLoopGuard(ttlMs = 5_000L)
    val now = 1_000L

    guard.mark(mediaUrl = "https://cdn.example.com/video.m3u8", streamId = "stream-1", nowMs = now)

    assertTrue(guard.shouldSkip("https://cdn.example.com/video.m3u8", null, nowMs = now + 1_000L))
    assertTrue(guard.shouldSkip(null, "stream-1", nowMs = now + 1_000L))
    assertFalse(guard.shouldSkip("https://cdn.example.com/video.m3u8", null, nowMs = now + 6_000L))
  }
}
