package com.stremioshell.host.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings

enum class TvSection(val label: String) {
  Home("Home"),
  Search("Search"),
  Settings("Settings"),
}

@Composable
fun TvApp() {
  var section by rememberSaveable { mutableStateOf(TvSection.Home) }

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
            TvSection.entries.forEach { candidate ->
              NavigationDrawerItem(
                selected = section == candidate,
                onClick = { section = candidate },
                leadingContent = {
                  Icon(
                    imageVector = when (candidate) {
                      TvSection.Home -> Icons.Filled.Home
                      TvSection.Search -> Icons.Filled.Search
                      TvSection.Settings -> Icons.Filled.Settings
                    },
                    contentDescription = candidate.label,
                  )
                },
              ) {
                Text(candidate.label)
              }
            }
          }
        },
      ) {
        Box(modifier = Modifier.fillMaxSize()) {
          when (section) {
            TvSection.Home -> PlaceholderScreen("Home")
            TvSection.Search -> PlaceholderScreen("Search")
            TvSection.Settings -> PlaceholderScreen("Settings")
          }
        }
      }
    }
  }
}

@Composable
private fun PlaceholderScreen(name: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      text = "$name - coming soon",
      style = MaterialTheme.typography.headlineMedium,
    )
  }
}
