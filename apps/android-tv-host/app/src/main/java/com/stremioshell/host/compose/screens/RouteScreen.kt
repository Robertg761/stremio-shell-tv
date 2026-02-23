package com.stremioshell.host.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.stremioshell.host.compose.navigation.AppScreen
import com.stremioshell.host.core.repo.LibraryUiState
import com.stremioshell.host.core.repo.PlaybackUiState
import com.stremioshell.host.core.repo.SessionUiState

@Composable
fun RouteScreen(
  screen: AppScreen,
  sessionUiState: SessionUiState,
  libraryUiState: LibraryUiState,
  playbackUiState: PlaybackUiState,
  diagnostics: List<String>,
  onLoginToggle: () -> Unit,
  onLibrarySync: () -> Unit,
  onOpenDemoPlayer: () -> Unit,
  onOpenDiagnostics: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface
          )
        )
      )
      .padding(28.dp)
  ) {
    Text(
      text = screen.title,
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Button(
        onClick = onOpenDiagnostics,
        modifier = Modifier.focusable()
      ) {
        Text("Export diagnostics")
      }
      if (screen == AppScreen.Intro) {
        Button(onClick = onLoginToggle) {
          Text(if (sessionUiState.isAuthenticated) "Logout" else "Login demo")
        }
      }
      if (screen == AppScreen.Library) {
        Button(onClick = onLibrarySync) {
          Text("Sync library")
        }
      }
      if (screen == AppScreen.Streams || screen == AppScreen.Player) {
        Button(onClick = onOpenDemoPlayer) {
          Text("Open demo stream")
        }
      }
    }
    Spacer(modifier = Modifier.height(20.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Session: auth=${sessionUiState.isAuthenticated} user=${sessionUiState.userId ?: "none"}")
        Text(
          "Library: items=${libraryUiState.itemCount} changed=${libraryUiState.changedItemIds.size} reason=${libraryUiState.reason ?: "n/a"}"
        )
        Text("Playback: stream=${playbackUiState.streamId ?: "none"} progressMs=${playbackUiState.progressMs}")
      }
    }
    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Recent runtime diagnostics",
      style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(10.dp))

    LazyColumn(
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(diagnostics.take(20)) { line ->
        Card(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = line,
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall
          )
        }
      }
    }
  }
}
