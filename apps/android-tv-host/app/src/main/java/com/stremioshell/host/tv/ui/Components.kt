package com.stremioshell.host.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.stremioshell.host.tv.LoadState
import com.stremioshell.host.tv.data.tmdb.MediaItem

@Composable
fun MediaCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier = modifier.width(140.dp)) {
    Card(onClick = onClick, modifier = Modifier.width(140.dp).height(200.dp)) {
      if (item.posterUrl != null) {
        AsyncImage(
          model = item.posterUrl,
          contentDescription = item.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(item.title, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
    Text(
      text = item.title,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.padding(top = 6.dp).width(140.dp),
    )
  }
}

@Composable
fun MediaRow(title: String, items: List<MediaItem>, onItemClick: (MediaItem) -> Unit) {
  if (items.isEmpty()) return
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
    )
    LazyRow(
      contentPadding = PaddingValues(horizontal = 48.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      items(items, key = { "${it.type}:${it.tmdbId}" }) { item ->
        MediaCard(item = item, onClick = { onItemClick(item) })
      }
    }
  }
}

@Composable
fun CenteredMessage(text: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text, style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
fun CenteredLoading(text: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 14.dp))
    }
  }
}

@Composable
fun <T> LoadStateContent(
  state: LoadState<T>,
  loadingText: String = "Loading...",
  onRetry: (() -> Unit)? = null,
  content: @Composable (T) -> Unit,
) {
  when (state) {
    is LoadState.Loading -> CenteredLoading(loadingText)
    is LoadState.Failed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(state.message, style = MaterialTheme.typography.titleMedium)
        if (onRetry != null) {
          androidx.tv.material3.Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("Retry")
          }
        }
      }
    }
    is LoadState.Ready -> content(state.value)
  }
}
