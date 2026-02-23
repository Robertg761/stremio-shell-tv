package com.stremioshell.host.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stremioshell.host.compose.AppActions

@Composable
fun IntroScreen(state: RouteUiState, actions: AppActions) {
  RouteScaffold(
    title = "Intro",
    subtitle = "Account bootstrap and host diagnostics.",
    diagnostics = state.diagnostics,
    topActions = {
      PrimaryAction("Diagnostics", actions.onOpenDiagnostics)
      PrimaryAction(if (state.session.isAuthenticated) "Logout" else "Login demo", actions.onLoginToggle)
      PrimaryAction("Updates", actions.onCheckUpdates)
    }
  ) {
    Card {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Authenticated: ${state.session.isAuthenticated}")
        Text("User: ${state.session.userId ?: "none"}")
      }
    }
  }
}

@Composable
fun BoardScreen(state: RouteUiState, actions: AppActions) {
  RouteScaffold(
    title = "Board",
    subtitle = "Featured rows and quick launch shortcuts.",
    diagnostics = state.diagnostics,
    topActions = {
      PrimaryAction("Diagnostics", actions.onOpenDiagnostics)
      PrimaryAction("Updates", actions.onCheckUpdates)
    }
  ) {
    Card {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Featured IDs", style = MaterialTheme.typography.titleSmall)
        if (state.catalog.featuredIds.isEmpty()) {
          Text("No featured items yet.")
        } else {
          state.catalog.featuredIds.forEach { id ->
            PrimaryAction(label = "Open $id") { actions.onSelectMeta(id) }
          }
        }
      }
    }
  }
}

@Composable
fun DiscoverScreen(state: RouteUiState, actions: AppActions) {
  RouteScaffold(
    title = "Discover",
    subtitle = "Browse metadata and stream choices.",
    diagnostics = state.diagnostics,
    topActions = {
      PrimaryAction("Diagnostics", actions.onOpenDiagnostics)
      PrimaryAction("Open stream", actions.onOpenDemoPlayer)
    }
  ) {
    Card {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Active meta: ${state.meta.activeMetaId ?: "none"}")
        Text("Title: ${state.meta.title ?: "n/a"}")
        Text("Subtitle: ${state.meta.subtitle ?: "n/a"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          PrimaryAction("Meta movie-001") { actions.onSelectMeta("movie-001") }
          PrimaryAction("Meta series-042") { actions.onSelectMeta("series-042") }
        }
      }
    }
  }
}

@Composable
fun SearchScreen(state: RouteUiState, actions: AppActions) {
  var query by remember(state.search.query) { mutableStateOf(state.search.query) }

  RouteScaffold(
    title = "Search",
    subtitle = "Native query path backed by runtime repository.",
    diagnostics = state.diagnostics,
    topActions = {
      PrimaryAction("Diagnostics", actions.onOpenDiagnostics)
      PrimaryAction("Run") { actions.onSearchQueryChanged(query) }
    }
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = query,
        onValueChange = {
          query = it
        },
        label = { Text("Search query") }
      )

      Card {
        LazyColumn(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          if (state.search.results.isEmpty()) {
            item { Text("No results.") }
          } else {
            items(state.search.results) { result ->
              PrimaryAction(label = result) { actions.onSelectMeta(result) }
            }
          }
        }
      }
    }
  }
}

@Composable
fun MetaDetailsScreen(state: RouteUiState, actions: AppActions) {
  RouteScaffold(
    title = "Meta Details",
    subtitle = "Selected title metadata.",
    diagnostics = state.diagnostics,
    topActions = {
      PrimaryAction("Diagnostics", actions.onOpenDiagnostics)
      PrimaryAction("Open stream", actions.onOpenDemoPlayer)
    }
  ) {
    Card {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Meta ID: ${state.meta.activeMetaId ?: "none"}")
        Text("Title: ${state.meta.title ?: "n/a"}")
        Text("Subtitle: ${state.meta.subtitle ?: "n/a"}")
      }
    }
  }
}

@Composable
fun StreamsScreen(state: RouteUiState, actions: AppActions) {
  RouteScaffold(
    title = "Streams",
    subtitle = "Choose stream and jump into player.",
    diagnostics = state.diagnostics,
    topActions = {
      PrimaryAction("Diagnostics", actions.onOpenDiagnostics)
      PrimaryAction("Play demo", actions.onOpenDemoPlayer)
    }
  ) {
    Card {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Current stream: ${state.playback.streamId ?: "none"}")
        Text("Progress: ${state.playback.progressMs}ms")
      }
    }
  }
}

@Composable
fun LibraryScreen(state: RouteUiState, actions: AppActions) {
  RouteScaffold(
    title = "Library",
    subtitle = "Collection sync and watch progress surface.",
    diagnostics = state.diagnostics,
    topActions = {
      PrimaryAction("Diagnostics", actions.onOpenDiagnostics)
      PrimaryAction("Sync", actions.onLibrarySync)
    }
  ) {
    Card {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Items: ${state.library.itemCount}")
        Text("Reason: ${state.library.reason ?: "n/a"}")
        Text("Changed IDs: ${state.library.changedItemIds.joinToString().ifBlank { "none" }}")
      }
    }
  }
}

@Composable
fun AddonsScreen(state: RouteUiState, actions: AppActions) {
  RouteScaffold(
    title = "Addons",
    subtitle = "Install/remove addon stubs through runtime custom actions.",
    diagnostics = state.diagnostics,
    topActions = {
      PrimaryAction("Diagnostics", actions.onOpenDiagnostics)
      PrimaryAction("Install catalog.plus") { actions.onInstallAddon("catalog.plus") }
      PrimaryAction("Remove catalog.plus") { actions.onRemoveAddon("catalog.plus") }
    }
  ) {
    Card {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Installed: ${state.addons.installed.joinToString().ifBlank { "none" }}")
      }
    }
  }
}

@Composable
fun CalendarScreen(state: RouteUiState, actions: AppActions) {
  RouteScaffold(
    title = "Calendar",
    subtitle = "Upcoming media hooks and date-oriented browse placeholder.",
    diagnostics = state.diagnostics,
    topActions = {
      PrimaryAction("Diagnostics", actions.onOpenDiagnostics)
      PrimaryAction("Refresh library", actions.onLibrarySync)
    }
  ) {
    Card {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Calendar data source uses library/custom runtime scopes.")
      }
    }
  }
}

@Composable
fun SettingsScreen(state: RouteUiState, actions: AppActions) {
  RouteScaffold(
    title = "Settings",
    subtitle = "Native settings backed by runtime custom scope.",
    diagnostics = state.diagnostics,
    topActions = {
      PrimaryAction("Diagnostics", actions.onOpenDiagnostics)
      PrimaryAction("Theme: Emerald") { actions.onSettingChanged("theme", "emerald") }
      PrimaryAction("Subs: Large") { actions.onSettingChanged("subtitlesSize", "large") }
    }
  ) {
    Card {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.settings.values.isEmpty()) {
          Text("No settings values yet.")
        } else {
          state.settings.values.forEach { (key, value) ->
            Text("$key = $value")
          }
        }
      }
    }
  }
}

@Composable
fun NotFoundScreen(state: RouteUiState, actions: AppActions) {
  RouteScaffold(
    title = "Not Found",
    subtitle = "Unknown route fallback.",
    diagnostics = state.diagnostics,
    topActions = {
      PrimaryAction("Diagnostics", actions.onOpenDiagnostics)
      PrimaryAction("Go Intro") { actions.onSelectMeta("home") }
    }
  ) {
    Card {
      Column(modifier = Modifier.padding(12.dp)) {
        Text("Route not found.")
      }
    }
  }
}
