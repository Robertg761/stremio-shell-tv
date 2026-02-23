package com.stremioshell.host.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.stremioshell.host.compose.navigation.AppScreen

@Stable
class AppState(
  val navController: NavHostController,
  private val diagnosticsStore: DiagnosticsStore
) {
  fun navigate(screen: AppScreen) {
    navController.navigate(screen.route) {
      launchSingleTop = true
      restoreState = true
      popUpTo(navController.graph.startDestinationId) {
        saveState = true
      }
    }
    diagnosticsStore.record("nav", "navigate route=${screen.route}")
  }

  fun currentScreen(): AppScreen {
    val route = navController.currentBackStackEntry?.destination?.route
    return AppScreen.fromRoute(route)
  }
}

@Composable
fun rememberAppState(
  diagnosticsStore: DiagnosticsStore,
  navController: NavHostController = rememberNavController()
): AppState {
  return remember(navController, diagnosticsStore) {
    AppState(navController, diagnosticsStore)
  }
}
