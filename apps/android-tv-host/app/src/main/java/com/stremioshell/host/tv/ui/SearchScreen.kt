package com.stremioshell.host.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stremioshell.host.tv.TvAppViewModel
import com.stremioshell.host.tv.data.tmdb.MediaType

@Composable
fun SearchScreen(viewModel: TvAppViewModel, onItemClick: (MediaType, Int) -> Unit) {
  val results by viewModel.searchResults.collectAsState()
  var query by rememberSaveable { mutableStateOf("") }

  Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
    OutlinedTextField(
      value = query,
      onValueChange = {
        query = it
        viewModel.search(it)
      },
      singleLine = true,
      placeholder = { Text("Search movies and shows") },
      colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
      ),
      modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
    )

    LoadStateContent(results, loadingText = "Searching...") { items ->
      if (items.isEmpty() && query.isNotBlank()) {
        CenteredMessage("No results for \"$query\"")
      } else {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 140.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalArrangement = Arrangement.spacedBy(20.dp),
          contentPadding = PaddingValues(bottom = 32.dp),
        ) {
          items(items, key = { "${it.type}:${it.tmdbId}" }) { item ->
            MediaCard(item = item, onClick = { onItemClick(item.type, item.tmdbId) })
          }
        }
      }
    }
  }
}
