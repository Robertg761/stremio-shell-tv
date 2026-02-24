package com.stremioshell.host.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.navigation.compose.currentBackStackEntryAsState
import com.stremioshell.host.compose.navigation.AppNavGraph
import com.stremioshell.host.compose.navigation.AppScreen
import com.stremioshell.host.compose.screens.MainNavBars
import com.stremioshell.host.compose.screens.RouteUiState
import com.stremioshell.host.compose.screens.contentPaddings
import com.stremioshell.host.compose.theme.StremioComposeTheme
import com.stremioshell.host.compose.theme.StremioPrimaryBackground
import com.stremioshell.host.compose.theme.StremioSecondaryBackground

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
  val currentScreen = AppScreen.fromRoute(currentRoute)

  val navScreens = remember {
    listOf(
      AppScreen.Board,
      AppScreen.Discover,
      AppScreen.Library,
      AppScreen.Calendar,
      AppScreen.Addons,
      AppScreen.Settings
    )
  }

  val focusRequesters = remember {
    navScreens.associate { it.route to FocusRequester() }
  }

  LaunchedEffect(currentScreen.route) {
    focusRequesters[currentScreen.route]?.requestFocus()
    routeActions.onFocusRestored(currentScreen.route)
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
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.radialGradient(
              colors = listOf(
                StremioSecondaryBackground,
                StremioPrimaryBackground,
                StremioPrimaryBackground
              ),
              radius = 1600f
            )
          )
      ) {
        MainNavBars(
          currentScreen = currentScreen,
          searchQuery = routeUiState.search.query,
          onSearchQueryChanged = routeActions.onSearchQueryChanged,
          onSubmitSearch = { appState.navigate(AppScreen.Search) },
          onNavigate = appState::navigate,
          focusRequesters = focusRequesters
        )

        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(contentPaddings(showStreamingWarning = false))
        ) {
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
