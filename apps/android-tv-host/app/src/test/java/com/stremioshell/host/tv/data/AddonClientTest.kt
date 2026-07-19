package com.stremioshell.host.tv.data

import com.stremioshell.host.tv.data.addon.AddonClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AddonClientTest {
  @Test
  fun `stream url is derived from manifest url`() {
    assertEquals(
      "https://comet.example/abc123/stream/movie/tt0111161.json",
      AddonClient.streamUrl("https://comet.example/abc123/manifest.json", "movie", "tt0111161"),
    )
  }

  @Test
  fun `episode ids use imdb season episode format`() = runBlocking {
    var requestedUrl = ""
    val client = AddonClient(fetcher = { url ->
      requestedUrl = url
      """{"streams":[{"name":"[RD+] Comet","title":"Show S01E02","url":"https://rd.example/v.mkv"}]}"""
    })

    val streams = client.episodeStreams("https://comet.example/cfg/manifest.json", "tt14688458", 1, 2)

    assertEquals("https://comet.example/cfg/stream/series/tt14688458:1:2.json", requestedUrl)
    assertEquals(1, streams.size)
    assertEquals("[RD+] Comet", streams.first().label)
    assertEquals("https://rd.example/v.mkv", streams.first().url)
  }

  @Test
  fun `streams without a url are dropped`() = runBlocking {
    val client = AddonClient(fetcher = {
      """{"streams":[
        {"name":"No url stream","infoHash":"abc"},
        {"name":"Good","url":"https://rd.example/v.mp4"}]}"""
    })

    val streams = client.movieStreams("https://comet.example/cfg/manifest.json", "tt1")
    assertEquals(listOf("Good"), streams.map { it.label })
  }

  @Test
  fun `manifest url without manifest json is rejected`() {
    assertThrows(IllegalArgumentException::class.java) {
      AddonClient.streamUrl("https://comet.example/abc123", "movie", "tt1")
    }
  }
}
