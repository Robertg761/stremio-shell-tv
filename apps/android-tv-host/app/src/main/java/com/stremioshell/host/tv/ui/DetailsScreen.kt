package com.stremioshell.host.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.stremioshell.host.tv.TvAppViewModel
import com.stremioshell.host.tv.data.tmdb.MediaDetails
import com.stremioshell.host.tv.data.tmdb.MediaType

@Composable
fun DetailsScreen(
  viewModel: TvAppViewModel,
  type: MediaType,
  tmdbId: Int,
  onPlay: (details: MediaDetails, season: Int?, episode: Int?) -> Unit,
) {
  val details by viewModel.details.collectAsState()
  val episodes by viewModel.episodes.collectAsState()

  LaunchedEffect(type, tmdbId) { viewModel.loadDetails(type, tmdbId) }

  LoadStateContent(
    details,
    loadingText = "Loading details...",
    onRetry = { viewModel.loadDetails(type, tmdbId) },
  ) { media ->
    var selectedSeason by rememberSaveable { mutableStateOf(media.seasons.firstOrNull()?.seasonNumber ?: 1) }
    val primaryFocus = androidx.compose.runtime.remember { FocusRequester() }

    LaunchedEffect(media.item.tmdbId) {
      // Land focus on the primary action instead of leaving it in the nav rail.
      runCatching { primaryFocus.requestFocus() }
    }

    LaunchedEffect(media.item.tmdbId, selectedSeason) {
      if (media.item.type == MediaType.Show && media.seasons.isNotEmpty()) {
        viewModel.loadSeason(media.item.tmdbId, selectedSeason)
      }
    }

    Box(modifier = Modifier.fillMaxSize()) {
      if (media.item.backdropUrl != null) {
        AsyncImage(
          model = media.item.backdropUrl,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize().alpha(0.25f),
        )
      }

      LazyColumn(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        item(key = "header") {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(media.item.title, style = MaterialTheme.typography.displaySmall)
            val meta = listOfNotNull(
              media.item.year,
              media.runtimeMinutes?.let { "${it} min" },
              media.item.rating?.let { "%.1f".format(it) + " / 10" },
              media.genres.take(3).joinToString(", ").ifBlank { null },
            ).joinToString("   ")
            Text(meta, style = MaterialTheme.typography.bodyMedium)
            Text(
              media.item.overview,
              style = MaterialTheme.typography.bodyLarge,
              maxLines = 4,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.fillMaxWidth(0.7f),
            )
            if (media.imdbId == null) {
              Text(
                "No IMDb id found for this title; streams are unavailable.",
                style = MaterialTheme.typography.bodySmall,
              )
            } else if (media.item.type == MediaType.Movie) {
              val continueWatching by viewModel.continueWatching.collectAsState()
              val entry = continueWatching.firstOrNull { it.key == "movie:${media.item.tmdbId}" }
              Button(
                onClick = { onPlay(media, null, null) },
                modifier = Modifier.focusRequester(primaryFocus),
              ) {
                Text(if (entry != null) "Resume" else "Find Streams")
              }
              if (entry != null && entry.durationMs > 0) {
                val minsLeft = ((entry.durationMs - entry.positionMs) / 60000).coerceAtLeast(1)
                Text(
                  "$minsLeft min left - picks up where you stopped",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }

        if (media.item.type == MediaType.Show && media.seasons.isNotEmpty()) {
          item(key = "seasons") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              items(media.seasons, key = { it.seasonNumber }) { season ->
                Button(
                  onClick = { selectedSeason = season.seasonNumber },
                  modifier = (if (season.seasonNumber == selectedSeason) Modifier else Modifier.alpha(0.6f))
                    .then(if (season.seasonNumber == media.seasons.first().seasonNumber && media.item.type == MediaType.Show) Modifier.focusRequester(primaryFocus) else Modifier),
                ) {
                  Text("Season ${season.seasonNumber}")
                }
              }
            }
          }

          item(key = "episodes") {
            LoadStateContentInline(episodes) { list ->
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEach { episode ->
                  Card(
                    onClick = { onPlay(media, episode.seasonNumber, episode.episodeNumber) },
                    modifier = Modifier.fillMaxWidth(0.8f),
                  ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                      if (episode.stillUrl != null) {
                        AsyncImage(
                          model = episode.stillUrl,
                          contentDescription = null,
                          contentScale = ContentScale.Crop,
                          modifier = Modifier.width(160.dp).height(90.dp),
                        )
                      }
                      Column {
                        Text(
                          "E${episode.episodeNumber}  ${episode.name}",
                          style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                          episode.overview,
                          style = MaterialTheme.typography.bodySmall,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun <T> LoadStateContentInline(
  state: com.stremioshell.host.tv.LoadState<T>,
  content: @Composable (T) -> Unit,
) {
  when (state) {
    is com.stremioshell.host.tv.LoadState.Loading -> Text("Loading episodes...")
    is com.stremioshell.host.tv.LoadState.Failed -> Text(state.message)
    is com.stremioshell.host.tv.LoadState.Ready -> content(state.value)
  }
}
