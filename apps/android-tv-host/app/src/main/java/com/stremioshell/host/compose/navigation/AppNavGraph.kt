package com.stremioshell.host.compose.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.stremioshell.host.compose.AppActions
import com.stremioshell.host.compose.player.ComposePlayerScreen
import com.stremioshell.host.compose.screens.AddonsScreen
import com.stremioshell.host.compose.screens.BoardScreen
import com.stremioshell.host.compose.screens.CalendarScreen
import com.stremioshell.host.compose.screens.DiscoverScreen
import com.stremioshell.host.compose.screens.IntroScreen
import com.stremioshell.host.compose.screens.LibraryScreen
import com.stremioshell.host.compose.screens.MetaDetailsScreen
import com.stremioshell.host.compose.screens.NotFoundScreen
import com.stremioshell.host.compose.screens.RouteUiState
import com.stremioshell.host.compose.screens.SearchScreen
import com.stremioshell.host.compose.screens.SettingsScreen
import com.stremioshell.host.compose.screens.StreamsScreen

private const val PLAYER_ROUTE_PATTERN = "player?url={url}"

@Composable
fun AppNavGraph(
  navController: NavHostController,
  routeUiState: RouteUiState,
  actions: AppActions,
  onPlayerProgress: (Long) -> Unit,
  onPlayerDiagnostic: (String) -> Unit
) {
  NavHost(
    navController = navController,
    startDestination = AppScreen.Intro.route
  ) {
    composable(AppScreen.Intro.route) {
      IntroScreen(routeUiState, actions)
    }
    composable(AppScreen.Board.route) {
      BoardScreen(routeUiState, actions)
    }
    composable(AppScreen.Discover.route) {
      DiscoverScreen(routeUiState, actions)
    }
    composable(AppScreen.Search.route) {
      SearchScreen(routeUiState, actions)
    }
    composable(AppScreen.MetaDetails.route) {
      MetaDetailsScreen(routeUiState, actions)
    }
    composable(AppScreen.Streams.route) {
      StreamsScreen(routeUiState, actions)
    }
    composable(
      route = PLAYER_ROUTE_PATTERN,
      arguments = listOf(navArgument("url") {
        type = NavType.StringType
        defaultValue = ""
        nullable = true
      })
    ) { backStackEntry ->
      val encodedUrl = backStackEntry.arguments?.getString("url")
      val url = encodedUrl?.let { Uri.decode(it) }
      ComposePlayerScreen(
        initialUrl = url,
        onProgress = onPlayerProgress,
        onDiagnostic = onPlayerDiagnostic
      )
    }
    composable(AppScreen.Library.route) {
      LibraryScreen(routeUiState, actions)
    }
    composable(AppScreen.Addons.route) {
      AddonsScreen(routeUiState, actions)
    }
    composable(AppScreen.Calendar.route) {
      CalendarScreen(routeUiState, actions)
    }
    composable(AppScreen.Settings.route) {
      SettingsScreen(routeUiState, actions)
    }
    composable(AppScreen.NotFound.route) {
      NotFoundScreen(routeUiState, actions)
    }
  }
}
