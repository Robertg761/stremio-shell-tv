package com.stremioshell.host.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.stremioshell.host.tv.LoadState
import com.stremioshell.host.tv.TvAppViewModel
import com.stremioshell.host.tv.data.WatchEntry
import com.stremioshell.host.tv.data.tmdb.MediaType

@Composable
fun HomeScreen(
  viewModel: TvAppViewModel,
  onItemClick: (MediaType, Int) -> Unit,
  onResumeClick: (WatchEntry) -> Unit,
  onPairWithPhone: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val rails by viewModel.homeRails.collectAsState()
  val continueWatching by viewModel.continueWatching.collectAsState()
  val apiKey by viewModel.tmdbApiKey.collectAsState()
  val firstContentFocus = remember { FocusRequester() }

  LaunchedEffect(apiKey) { viewModel.loadHomeRails() }

  // Land focus on content (not the nav rail) once something focusable exists.
  LaunchedEffect(rails, apiKey) {
    if (rails is LoadState.Ready || (apiKey != null && apiKey!!.isBlank())) {
      runCatching { firstContentFocus.requestFocus() }
    }
  }

  if (apiKey != null && apiKey!!.isBlank()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Welcome", style = MaterialTheme.typography.displaySmall)
        Text(
          "Connect your TMDB account and Comet addon to start streaming.",
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
        )
        Button(onClick = onPairWithPhone, modifier = Modifier.focusRequester(firstContentFocus)) {
          Text("Set up with phone")
        }
        Button(
          onClick = onOpenSettings,
          modifier = Modifier.padding(top = 12.dp),
        ) {
          Text("Enter manually")
        }
      }
    }
    return
  }

  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  // Settle a newly focused row at a stable "focus line" ~18% down the viewport.
  // Index-based (not offset-based) so it doesn't race the default bring-into-
  // view: a negative scrollOffset anchors the row that far below the top.
  fun scrollRowToFocusLine(lazyIndex: Int) {
    val target = (listState.layoutInfo.viewportSize.height * 0.18f).toInt()
    scope.launch {
      // Let the default bring-into-view settle, then take over as the final
      // scroll so the row lands on the focus line rather than the edge.
      kotlinx.coroutines.delay(60)
      listState.animateScrollToItem(lazyIndex, -target)
    }
  }

  LoadStateContent(
    rails,
    loadingText = "Loading catalogs...",
    onRetry = { viewModel.loadHomeRails(force = true) },
  ) { railList ->
    val continueOffset = if (continueWatching.isNotEmpty()) 1 else 0
    LazyColumn(
      state = listState,
      verticalArrangement = Arrangement.spacedBy(28.dp),
      contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
      modifier = Modifier.fillMaxSize(),
    ) {
      if (continueWatching.isNotEmpty()) {
        item(key = "continue") {
          ContinueWatchingRow(
            continueWatching,
            onResumeClick,
            firstContentFocus,
            onRowFocused = { scrollRowToFocusLine(0) },
          )
        }
      }
      items(railList.size, key = { railList[it].title }) { index ->
        val rail = railList[index]
        MediaRowFocusable(
          title = rail.title,
          items = rail.items,
          firstCardFocus = if (index == 0 && continueWatching.isEmpty()) firstContentFocus else null,
          onItemClick = { item -> onItemClick(item.type, item.tmdbId) },
          onRowFocused = { scrollRowToFocusLine(continueOffset + index) },
        )
      }
    }
  }
}

@Composable
private fun MediaRowFocusable(
  title: String,
  items: List<com.stremioshell.host.tv.data.tmdb.MediaItem>,
  firstCardFocus: FocusRequester?,
  onItemClick: (com.stremioshell.host.tv.data.tmdb.MediaItem) -> Unit,
  onRowFocused: () -> Unit,
) {
  if (items.isEmpty()) return
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .onFocusChanged { if (it.hasFocus) onRowFocused() },
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
    )
    LazyRow(
      contentPadding = PaddingValues(horizontal = 48.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      items(items.size, key = { "${items[it].type}:${items[it].tmdbId}" }) { index ->
        val item = items[index]
        MediaCard(
          item = item,
          onClick = { onItemClick(item) },
          modifier = if (index == 0 && firstCardFocus != null) {
            Modifier.focusRequester(firstCardFocus)
          } else {
            Modifier
          },
        )
      }
    }
  }
}

@Composable
private fun ContinueWatchingRow(
  entries: List<WatchEntry>,
  onResumeClick: (WatchEntry) -> Unit,
  firstCardFocus: FocusRequester,
  onRowFocused: () -> Unit,
) {
  Column(modifier = Modifier.onFocusChanged { if (it.hasFocus) onRowFocused() }) {
    Text(
      text = "Continue Watching",
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
    )
    LazyRow(
      contentPadding = PaddingValues(horizontal = 48.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      items(entries.size, key = { entries[it].key }) { index ->
        val entry = entries[index]
        Column(modifier = Modifier.width(140.dp)) {
          Card(
            onClick = { onResumeClick(entry) },
            scale = CardDefaults.scale(focusedScale = 1.08f),
            modifier = Modifier.width(140.dp).height(200.dp)
              .then(if (index == 0) Modifier.focusRequester(firstCardFocus) else Modifier),
          ) {
            Box(modifier = Modifier.fillMaxSize()) {
              if (entry.posterUrl != null) {
                AsyncImage(
                  model = entry.posterUrl,
                  contentDescription = entry.title,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize(),
                )
              } else {
                Text(entry.title, modifier = Modifier.padding(8.dp))
              }
              // Watched-progress bar pinned to the card bottom.
              Box(
                modifier = Modifier
                  .align(Alignment.BottomStart)
                  .fillMaxWidth()
                  .height(5.dp)
                  .background(MaterialTheme.colorScheme.surfaceVariant),
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth(entry.progress)
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.primary),
                )
              }
            }
          }
          val suffix = if (entry.season != null) " S${entry.season}E${entry.episode}" else ""
          Text(
            text = entry.title + suffix,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 14.dp),
          )
        }
      }
    }
  }
}
