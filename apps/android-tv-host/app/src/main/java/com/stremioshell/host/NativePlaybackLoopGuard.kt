package com.stremioshell.host

class NativePlaybackLoopGuard(
  private val ttlMs: Long = 15_000L
) {
  private data class Entry(
    val expiresAtMs: Long
  )

  private val byUrl = mutableMapOf<String, Entry>()
  private val byStreamId = mutableMapOf<String, Entry>()

  fun mark(
    mediaUrl: String?,
    streamId: String?,
    nowMs: Long = System.currentTimeMillis()
  ) {
    val expiresAtMs = nowMs + ttlMs
    mediaUrl?.takeIf { it.isNotBlank() }?.let {
      byUrl[it] = Entry(expiresAtMs)
    }
    streamId?.takeIf { it.isNotBlank() }?.let {
      byStreamId[it] = Entry(expiresAtMs)
    }
  }

  fun shouldSkip(
    mediaUrl: String?,
    streamId: String?,
    nowMs: Long = System.currentTimeMillis()
  ): Boolean {
    evictExpired(nowMs)
    val urlSkip = mediaUrl?.let { byUrl[it]?.expiresAtMs?.let { until -> nowMs <= until } } ?: false
    if (urlSkip) {
      return true
    }
    val streamSkip = streamId?.let { byStreamId[it]?.expiresAtMs?.let { until -> nowMs <= until } } ?: false
    return streamSkip
  }

  private fun evictExpired(nowMs: Long) {
    val urlIterator = byUrl.entries.iterator()
    while (urlIterator.hasNext()) {
      val entry = urlIterator.next()
      if (nowMs > entry.value.expiresAtMs) {
        urlIterator.remove()
      }
    }

    val streamIterator = byStreamId.entries.iterator()
    while (streamIterator.hasNext()) {
      val entry = streamIterator.next()
      if (nowMs > entry.value.expiresAtMs) {
        streamIterator.remove()
      }
    }
  }
}
