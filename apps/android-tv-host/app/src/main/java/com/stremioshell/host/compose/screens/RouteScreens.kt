package com.stremioshell.host.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stremioshell.host.compose.AppActions
import com.stremioshell.host.core.repo.CatalogRowUiState

@Composable
fun IntroScreen(state: RouteUiState, actions: AppActions) {
  BoardScreen(state = state, actions = actions)
}

@Composable
fun BoardScreen(state: RouteUiState, actions: AppActions) {
  val rows = boardRows(state)

  LazyColumn(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(24.dp)
  ) {
    items(rows) { row ->
      if (row.items.isEmpty()) {
        MetaRowPlaceholder(title = row.title)
      } else {
        MetaRow(
          title = row.title,
          items = row.items,
          onSelect = actions.onSelectMeta
        )
      }
    }
  }
}

@Composable
fun DiscoverScreen(state: RouteUiState, actions: AppActions) {
  val rows = discoverRows(state)

  LazyColumn(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(24.dp)
  ) {
    items(rows) { row ->
      if (row.items.isEmpty()) {
        MetaRowPlaceholder(title = row.title)
      } else {
        MetaRow(title = row.title, items = row.items, onSelect = actions.onSelectMeta)
      }
    }
  }
}

@Composable
fun SearchScreen(state: RouteUiState, actions: AppActions) {
  val results = state.search.results

  LazyColumn(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      if (state.search.query.isBlank()) {
        EmptyRouteHint("Use the top search bar to find movies, series, channels, or paste a stream link.")
      } else {
        Text(
          text = "Results for \"${state.search.query}\"",
          style = MaterialTheme.typography.displaySmall,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
          modifier = Modifier.padding(horizontal = 8.dp)
        )
      }
    }

    item {
      if (results.isEmpty()) {
        MetaRowPlaceholder(title = "Search Results")
      } else {
        MetaRow(
          title = "Search Results",
          items = results,
          onSelect = actions.onSelectMeta
        )
      }
    }
  }
}

@Composable
fun MetaDetailsScreen(state: RouteUiState, actions: AppActions) {
  val title = state.meta.title ?: "Title Details"
  val subtitle = state.meta.subtitle ?: "Select a title from Board, Discover, or Search."
  val streams = listOf("${state.meta.activeMetaId ?: "stream"}-1080p", "${state.meta.activeMetaId ?: "stream"}-720p", "${state.meta.activeMetaId ?: "stream"}-480p")

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.displaySmall,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
      modifier = Modifier.padding(horizontal = 8.dp)
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
      modifier = Modifier.padding(horizontal = 8.dp)
    )
    MetaRow(title = "Available Streams", items = streams, onSelect = actions.onOpenPlayer)
  }
}

@Composable
fun StreamsScreen(state: RouteUiState, actions: AppActions) {
  val active = state.meta.activeMetaId ?: state.playback.streamId ?: "stream"
  val streams = listOf("$active-4K", "$active-1080p", "$active-720p", "$active-480p")
  MetaRow(title = "Streams", items = streams, onSelect = actions.onOpenPlayer)
}

@Composable
fun LibraryScreen(state: RouteUiState, actions: AppActions) {
  val items = state.library.changedItemIds

  LazyColumn(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      EmptyRouteHint("Library items: ${state.library.itemCount}. Last sync reason: ${state.library.reason ?: "unknown"}.")
    }
    item {
      if (items.isEmpty()) {
        MetaRowPlaceholder(title = "Continue Watching")
      } else {
        MetaRow(title = "Continue Watching", items = items, onSelect = actions.onSelectMeta)
      }
    }
  }
}

@Composable
fun AddonsScreen(state: RouteUiState, actions: AppActions) {
  val addons = if (state.addons.installed.isEmpty()) {
    listOf("catalog.base", "catalog.community", "channels.live")
  } else {
    state.addons.installed
  }

  LazyColumn(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      EmptyRouteHint("Installed addons and providers.")
    }
    item {
      MetaRow(title = "Installed Addons", items = addons, onSelect = actions.onRemoveAddon)
    }
    item {
      MetaRow(
        title = "Addon Catalog",
        items = listOf("catalog.plus", "meta.tmdb", "subtitles.community"),
        onSelect = actions.onInstallAddon
      )
    }
  }
}

@Composable
fun CalendarScreen(state: RouteUiState, actions: AppActions) {
  val upcoming = if (state.library.changedItemIds.isEmpty()) {
    listOf("Tonight", "Tomorrow", "This Week", "Next Week")
  } else {
    state.library.changedItemIds.map { "$it - Next Episode" }
  }

  MetaRow(title = "Upcoming", items = upcoming, onSelect = actions.onSelectMeta)
}

@Composable
fun SettingsScreen(state: RouteUiState, actions: AppActions) {
  val settingsItems = if (state.settings.values.isEmpty()) {
    listOf("Playback", "Subtitles", "Streaming", "Diagnostics")
  } else {
    state.settings.values.entries.map { "${it.key}: ${it.value}" }
  }

  LazyColumn(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      EmptyRouteHint("Settings and playback preferences.")
    }
    item {
      MetaRow(
        title = "Settings",
        items = settingsItems,
        onSelect = { value ->
          val key = value.substringBefore(':').trim().ifEmpty { "setting" }
          val parsed = value.substringAfter(':', "enabled").trim()
          actions.onSettingChanged(key, parsed)
        }
      )
    }
  }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun NotFoundScreen(_state: RouteUiState, _actions: AppActions) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "Route Not Found",
      style = MaterialTheme.typography.displaySmall,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    EmptyRouteHint("Use Home from the left navigation rail to recover.")
  }
}

private fun boardRows(state: RouteUiState): List<CatalogRowUiState> {
  val fallbackRows = listOf(
    CatalogRowUiState(
      id = "popular_movie",
      title = "Popular - Movie",
      items = listOf("movie-001", "movie-010", "movie-099", "movie-222", "movie-777")
    ),
    CatalogRowUiState(
      id = "popular_series",
      title = "Popular - Series",
      items = listOf("series-042", "series-111", "series-208", "series-310", "series-888")
    )
  )

  val runtimeRows = state.catalog.rows
  return if (runtimeRows.isEmpty()) fallbackRows else runtimeRows
}

private fun discoverRows(state: RouteUiState): List<CatalogRowUiState> {
  val featured = state.catalog.featuredIds
  return listOf(
    CatalogRowUiState(
      id = "discover_movies",
      title = "Recommended - Movies",
      items = if (featured.isEmpty()) listOf("movie-001", "movie-010", "movie-020") else featured
    ),
    CatalogRowUiState(
      id = "discover_series",
      title = "Recommended - Series",
      items = if (featured.isEmpty()) listOf("series-042", "series-210", "series-402") else featured.reversed()
    )
  )
}
