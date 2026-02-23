package com.stremioshell.host.compose.screens

import com.stremioshell.host.core.repo.AddonsUiState
import com.stremioshell.host.core.repo.CatalogUiState
import com.stremioshell.host.core.repo.LibraryUiState
import com.stremioshell.host.core.repo.MetaUiState
import com.stremioshell.host.core.repo.PlaybackUiState
import com.stremioshell.host.core.repo.SearchUiState
import com.stremioshell.host.core.repo.SessionUiState
import com.stremioshell.host.core.repo.SettingsUiState

data class RouteUiState(
  val session: SessionUiState,
  val catalog: CatalogUiState,
  val meta: MetaUiState,
  val search: SearchUiState,
  val library: LibraryUiState,
  val addons: AddonsUiState,
  val settings: SettingsUiState,
  val playback: PlaybackUiState,
  val diagnostics: List<String>
)
