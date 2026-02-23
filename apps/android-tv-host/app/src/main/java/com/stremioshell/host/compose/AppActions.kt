package com.stremioshell.host.compose

data class AppActions(
  val onOpenDiagnostics: () -> Unit,
  val onCheckUpdates: () -> Unit,
  val onLoginToggle: () -> Unit,
  val onLibrarySync: () -> Unit,
  val onOpenDemoPlayer: () -> Unit,
  val onSearchQueryChanged: (String) -> Unit,
  val onSelectMeta: (String) -> Unit,
  val onInstallAddon: (String) -> Unit,
  val onRemoveAddon: (String) -> Unit,
  val onSettingChanged: (String, String) -> Unit,
  val onBackDecision: (String) -> Unit,
  val onFocusRestored: (String) -> Unit,
  val onPlayerDiagnostic: (String) -> Unit
)
