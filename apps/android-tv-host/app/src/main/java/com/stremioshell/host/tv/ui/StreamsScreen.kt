package com.stremioshell.host.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stremioshell.host.tv.TvAppViewModel
import com.stremioshell.host.tv.data.addon.AddonStream

@Composable
fun StreamsScreen(
  viewModel: TvAppViewModel,
  screen: Screen.Streams,
  onStreamClick: (AddonStream) -> Unit,
) {
  val streams by viewModel.streams.collectAsState()
  val firstStreamFocus = remember { FocusRequester() }

  LaunchedEffect(screen) {
    viewModel.loadStreams(screen.imdbId, screen.season, screen.episode)
  }

  LaunchedEffect(streams) {
    if (streams is com.stremioshell.host.tv.LoadState.Ready) {
      runCatching { firstStreamFocus.requestFocus() }
    }
  }

  Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
    val suffix = if (screen.season != null) "  S${screen.season}E${screen.episode}" else ""
    Text(
      text = "Streams - ${screen.title}$suffix",
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.padding(bottom = 16.dp),
    )

    LoadStateContent(
      streams,
      loadingText = "Asking the addon for streams...",
      onRetry = { viewModel.loadStreams(screen.imdbId, screen.season, screen.episode) },
    ) { list ->
      if (list.isEmpty()) {
        CenteredMessage("The addon returned no playable streams for this title.")
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(bottom = 32.dp),
        ) {
          itemsIndexed(list, key = { _, s -> s.url ?: s.hashCode().toString() }) { index, stream ->
            Card(
              onClick = { onStreamClick(stream) },
              modifier = Modifier.fillMaxWidth(0.85f)
                .then(if (index == 0) Modifier.focusRequester(firstStreamFocus) else Modifier),
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text(stream.label, style = MaterialTheme.typography.titleMedium)
                if (stream.detail.isNotBlank()) {
                  Text(
                    stream.detail,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
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
