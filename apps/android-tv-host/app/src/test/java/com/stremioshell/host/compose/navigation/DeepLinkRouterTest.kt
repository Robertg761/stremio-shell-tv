package com.stremioshell.host.compose.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkRouterTest {
  @Test
  fun `routes board by default`() {
    val destination = DeepLinkRouter.parse("stremio-shell://board")
    assertEquals(AppScreen.Board.route, destination.route)
  }

  @Test
  fun `routes search and extracts query`() {
    val destination = DeepLinkRouter.parse("stremio-shell://search?q=matrix")
    assertEquals(AppScreen.Search.route, destination.route)
    assertEquals("matrix", destination.searchQuery)
  }

  @Test
  fun `routes meta and extracts id from query`() {
    val destination = DeepLinkRouter.parse("stremio-shell://meta?id=movie-001")
    assertEquals(AppScreen.MetaDetails.route, destination.route)
    assertEquals("movie-001", destination.metaId)
  }

  @Test
  fun `routes player and preserves url query`() {
    val destination = DeepLinkRouter.parse("stremio-shell://player?url=https%3A%2F%2Fcdn.example%2Fstream.m3u8")
    assertEquals(
      "player?url=https%3A%2F%2Fcdn.example%2Fstream.m3u8",
      destination.route
    )
  }

  @Test
  fun `unknown route falls back to not found`() {
    val destination = DeepLinkRouter.parse("stremio-shell://unknown/path")
    assertEquals(AppScreen.NotFound.route, destination.route)
    assertNull(destination.searchQuery)
    assertNull(destination.metaId)
  }
}
