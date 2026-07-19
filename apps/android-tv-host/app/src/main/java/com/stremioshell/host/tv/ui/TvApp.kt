package com.stremioshell.host.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.stremioshell.host.tv.TvAppViewModel
import com.stremioshell.host.tv.data.WatchEntry
import com.stremioshell.host.tv.data.addon.AddonStream
import com.stremioshell.host.tv.data.tmdb.MediaType

/** Launches playback of a resolved stream; provided by the hosting activity. */
fun interface StreamLauncher {
  fun play(screen: Screen.Streams, stream: AddonStream)
}

@Composable
fun TvApp(streamLauncher: StreamLauncher = StreamLauncher { _, _ -> }) {
  val viewModel: TvAppViewModel = viewModel()
  val backstack = remember { mutableStateListOf<Screen>(Screen.Home) }
  val current = backstack.last()

  fun push(screen: Screen) = backstack.add(screen)
  fun setRoot(screen: Screen) {
    backstack.clear()
    backstack.add(screen)
  }

  BackHandler(enabled = backstack.size > 1) { backstack.removeAt(backstack.lastIndex) }

  val openDetails: (MediaType, Int) -> Unit = { type, id -> push(Screen.Details(type, id)) }
  val openResume: (WatchEntry) -> Unit = { entry ->
    val type = if (entry.mediaType == "show") MediaType.Show else MediaType.Movie
    push(Screen.Details(type, entry.tmdbId))
  }

  Surface(modifier = Modifier.fillMaxSize()) {
    Row(modifier = Modifier.fillMaxSize()) {
      NavigationDrawer(
        drawerContent = {
          Column(
            modifier = Modifier
              .fillMaxHeight()
              .background(MaterialTheme.colorScheme.surface)
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
          ) {
            NavigationDrawerItem(
              selected = current is Screen.Home,
              onClick = { setRoot(Screen.Home) },
              leadingContent = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            ) { Text("Home") }
            NavigationDrawerItem(
              selected = current is Screen.Search,
              onClick = { setRoot(Screen.Search) },
              leadingContent = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            ) { Text("Search") }
            NavigationDrawerItem(
              selected = current is Screen.Settings,
              onClick = { setRoot(Screen.Settings) },
              leadingContent = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
            ) { Text("Settings") }
          }
        },
      ) {
        Box(modifier = Modifier.fillMaxSize()) {
          when (val screen = current) {
            is Screen.Home -> HomeScreen(viewModel, onItemClick = openDetails, onResumeClick = openResume, onOpenSettings = { setRoot(Screen.Settings) })
            is Screen.Search -> SearchScreen(viewModel, onItemClick = openDetails)
            is Screen.Settings -> SettingsScreen(viewModel)
            is Screen.Details -> DetailsScreen(viewModel, screen.type, screen.tmdbId) { media, season, episode ->
              val imdbId = media.imdbId ?: return@DetailsScreen
              push(
                Screen.Streams(
                  imdbId = imdbId,
                  title = media.item.title,
                  tmdbId = media.item.tmdbId,
                  mediaType = media.item.type,
                  posterUrl = media.item.posterUrl,
                  season = season,
                  episode = episode,
                )
              )
            }
            is Screen.Streams -> StreamsScreen(viewModel, screen) { stream ->
              streamLauncher.play(screen, stream)
            }
          }
        }
      }
    }
  }
}
