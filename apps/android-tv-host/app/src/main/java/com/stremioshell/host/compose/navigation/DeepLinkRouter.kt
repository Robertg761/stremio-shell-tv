package com.stremioshell.host.compose.navigation

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class DeepLinkDestination(
  val route: String,
  val searchQuery: String? = null,
  val metaId: String? = null
)

object DeepLinkRouter {
  fun parse(rawUri: String): DeepLinkDestination {
    val uri = runCatching { URI(rawUri) }.getOrNull()
      ?: return DeepLinkDestination(AppScreen.NotFound.route)
    val host = uri.host?.trim()?.lowercase().orEmpty()
    val segments = uri.path
      ?.split("/")
      ?.filter { it.isNotBlank() }
      ?: emptyList()
    val queryParams = parseQuery(uri.rawQuery)
    val routeKey: String
    val detailSegment: String?

    if (host.isNotBlank()) {
      routeKey = host
      detailSegment = segments.firstOrNull()
    } else {
      routeKey = segments.firstOrNull()?.trim()?.lowercase().orEmpty()
      detailSegment = segments.getOrNull(1)
    }

    return when {
      routeKey.isBlank() || routeKey == "board" || routeKey == "home" -> {
        DeepLinkDestination(AppScreen.Board.route)
      }

      routeKey == "discover" -> {
        DeepLinkDestination(AppScreen.Discover.route)
      }

      routeKey == "search" -> {
        val query = queryParams["q"] ?: queryParams["query"]
        DeepLinkDestination(
          route = AppScreen.Search.route,
          searchQuery = query?.trim().orEmpty().ifBlank { null }
        )
      }

      routeKey == "library" -> {
        DeepLinkDestination(AppScreen.Library.route)
      }

      routeKey == "addons" -> {
        DeepLinkDestination(AppScreen.Addons.route)
      }

      routeKey == "calendar" -> {
        DeepLinkDestination(AppScreen.Calendar.route)
      }

      routeKey == "settings" -> {
        DeepLinkDestination(AppScreen.Settings.route)
      }

      routeKey == "streams" -> {
        DeepLinkDestination(AppScreen.Streams.route)
      }

      routeKey == "meta" || routeKey == "details" -> {
        val metaId = queryParams["id"]
          ?.trim()
          .orEmpty()
          .ifBlank { detailSegment?.trim()?.ifBlank { null } }
        DeepLinkDestination(
          route = AppScreen.MetaDetails.route,
          metaId = metaId
        )
      }

      routeKey == "player" -> {
        val url = queryParams["url"]
        val route = if (url.isNullOrBlank()) {
          AppScreen.Player.route
        } else {
          "${AppScreen.Player.route}?url=${encode(url)}"
        }
        DeepLinkDestination(route)
      }

      else -> {
        DeepLinkDestination(AppScreen.NotFound.route)
      }
    }
  }

  private fun parseQuery(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) {
      return emptyMap()
    }

    return rawQuery
      .split("&")
      .mapNotNull { pair ->
        val separator = pair.indexOf('=')
        val key = if (separator >= 0) pair.substring(0, separator) else pair
        if (key.isBlank()) {
          null
        } else {
          val value = if (separator >= 0) pair.substring(separator + 1) else ""
          decode(key) to decode(value)
        }
      }
      .toMap()
  }

  private fun decode(value: String): String {
    return runCatching {
      URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
  }

  private fun encode(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
  }
}
