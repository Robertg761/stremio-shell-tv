package com.stremioshell.host.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import com.stremioshell.host.compose.navigation.AppNavGraph
import com.stremioshell.host.compose.navigation.AppScreen
import com.stremioshell.host.compose.screens.RouteUiState
import com.stremioshell.host.compose.theme.StremioComposeTheme

@Composable
fun StremioTvApp(
  appState: AppState,
  routeUiState: RouteUiState,
  backPolicyManager: BackPolicyManager,
  actions: AppActions,
  onExportDiagnostics: () -> Unit,
  onPlayerProgress: (Long) -> Unit,
  exitApp: () -> Unit
) {
  var diagnosticsDialogVisible by remember { mutableStateOf(false) }
  val routeActions = remember(actions) {
    actions.copy(onOpenDiagnostics = { diagnosticsDialogVisible = true })
  }
  val backStackEntry by appState.navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route?.substringBefore("?").orEmpty()
  val focusRequesters = remember {
    (AppScreen.topLevel + AppScreen.Player).associate { it.route to FocusRequester() }
  }

  LaunchedEffect(currentRoute) {
    val normalizedRoute = AppScreen.fromRoute(currentRoute).route
    val requester = focusRequesters[normalizedRoute]
    if (requester != null) {
      requester.requestFocus()
      routeActions.onFocusRestored(normalizedRoute)
    }
  }

  BackHandler {
    backPolicyManager.handleBack(
      navController = appState.navController,
      hasOpenOverlay = diagnosticsDialogVisible,
      closeOverlay = { diagnosticsDialogVisible = false },
      exitApp = exitApp,
      onDecision = routeActions.onBackDecision
    )
  }

  StremioComposeTheme {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background
    ) {
      Scaffold(
        topBar = {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            AppScreen.topLevel.forEach { screen ->
              Button(
                onClick = { appState.navigate(screen) },
                modifier = Modifier.focusRequester(focusRequesters.getValue(screen.route))
              ) {
                Text(screen.title)
              }
            }
            Button(
              onClick = { appState.navController.navigate(AppScreen.Player.route) },
              modifier = Modifier.focusRequester(focusRequesters.getValue(AppScreen.Player.route))
            ) {
              Text("Player")
            }
            Button(onClick = actions.onCheckUpdates) {
              Text("Check updates")
            }
          }
        }
      ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
          AppNavGraph(
            navController = appState.navController,
            routeUiState = routeUiState,
            actions = routeActions,
            onPlayerProgress = onPlayerProgress,
            onPlayerDiagnostic = routeActions.onPlayerDiagnostic
          )
        }
      }
    }

    if (diagnosticsDialogVisible) {
      AlertDialog(
        onDismissRequest = { diagnosticsDialogVisible = false },
        title = { Text("Diagnostics") },
        text = {
          Text(
            routeUiState.diagnostics.take(20).joinToString(separator = "\n").ifBlank { "No diagnostics yet." }
          )
        },
        confirmButton = {
          TextButton(
            onClick = {
              onExportDiagnostics()
              diagnosticsDialogVisible = false
            }
          ) {
            Text("Export")
          }
        },
        dismissButton = {
          TextButton(onClick = { diagnosticsDialogVisible = false }) {
            Text("Close")
          }
        }
      )
    }
  }
}
