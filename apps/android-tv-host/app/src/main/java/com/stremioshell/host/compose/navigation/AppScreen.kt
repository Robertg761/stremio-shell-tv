package com.stremioshell.host.compose.navigation

sealed class AppScreen(
  val route: String,
  val title: String
) {
  data object Intro : AppScreen("intro", "Intro")
  data object Board : AppScreen("board", "Board")
  data object Discover : AppScreen("discover", "Discover")
  data object Search : AppScreen("search", "Search")
  data object MetaDetails : AppScreen("meta-details", "Meta Details")
  data object Streams : AppScreen("streams", "Streams")
  data object Player : AppScreen("player", "Player")
  data object Library : AppScreen("library", "Library")
  data object Addons : AppScreen("addons", "Addons")
  data object Calendar : AppScreen("calendar", "Calendar")
  data object Settings : AppScreen("settings", "Settings")
  data object NotFound : AppScreen("not-found", "Not Found")

  companion object {
    val topLevel = listOf(
      Board,
      Discover,
      Library,
      Addons,
      Calendar,
      Settings
    )

    fun fromRoute(route: String?): AppScreen {
      return when (route?.substringBefore("?")) {
        Intro.route -> Intro
        Board.route -> Board
        Discover.route -> Discover
        Search.route -> Search
        MetaDetails.route -> MetaDetails
        Streams.route -> Streams
        Player.route -> Player
        Library.route -> Library
        Addons.route -> Addons
        Calendar.route -> Calendar
        Settings.route -> Settings
        else -> NotFound
      }
    }
  }
}
