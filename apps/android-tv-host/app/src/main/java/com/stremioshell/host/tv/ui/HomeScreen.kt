package com.stremioshell.host.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.stremioshell.host.tv.TvAppViewModel
import com.stremioshell.host.tv.data.WatchEntry
import com.stremioshell.host.tv.data.tmdb.MediaType

@Composable
fun HomeScreen(
  viewModel: TvAppViewModel,
  onItemClick: (MediaType, Int) -> Unit,
  onResumeClick: (WatchEntry) -> Unit,
) {
  val rails by viewModel.homeRails.collectAsState()
  val continueWatching by viewModel.continueWatching.collectAsState()
  val apiKey by viewModel.tmdbApiKey.collectAsState()

  LaunchedEffect(apiKey) { viewModel.loadHomeRails() }

  if (apiKey != null && apiKey!!.isBlank()) {
    CenteredMessage("Welcome! Open Settings to add your TMDB API key and Comet addon URL.")
    return
  }

  LoadStateContent(rails, loadingText = "Loading catalogs...") { railList ->
    LazyColumn(
      verticalArrangement = Arrangement.spacedBy(28.dp),
      contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
      modifier = Modifier.fillMaxSize(),
    ) {
      if (continueWatching.isNotEmpty()) {
        item(key = "continue") {
          ContinueWatchingRow(continueWatching, onResumeClick)
        }
      }
      items(railList.size, key = { railList[it].title }) { index ->
        val rail = railList[index]
        MediaRow(rail.title, rail.items) { item -> onItemClick(item.type, item.tmdbId) }
      }
    }
  }
}

@Composable
private fun ContinueWatchingRow(entries: List<WatchEntry>, onResumeClick: (WatchEntry) -> Unit) {
  Column {
    Text(
      text = "Continue Watching",
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
    )
    LazyRow(
      contentPadding = PaddingValues(horizontal = 48.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      items(entries, key = { it.key }) { entry ->
        Column(modifier = Modifier.width(140.dp)) {
          Card(onClick = { onResumeClick(entry) }, modifier = Modifier.width(140.dp).height(200.dp)) {
            if (entry.posterUrl != null) {
              AsyncImage(
                model = entry.posterUrl,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            } else {
              Box(modifier = Modifier.fillMaxSize()) {
                Text(entry.title, modifier = Modifier.padding(8.dp))
              }
            }
          }
          val suffix = if (entry.season != null) " S${entry.season}E${entry.episode}" else ""
          Text(
            text = entry.title + suffix,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
          )
        }
      }
    }
  }
}
