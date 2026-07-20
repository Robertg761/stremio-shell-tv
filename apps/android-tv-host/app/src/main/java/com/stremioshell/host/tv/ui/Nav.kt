package com.stremioshell.host.tv.ui

import com.stremioshell.host.tv.data.tmdb.MediaType

/** Minimal back-stack navigation for the TV app. */
sealed interface Screen {
  data object Home : Screen
  data object Search : Screen
  data object Settings : Screen
  data object Pair : Screen
  data class Details(val type: MediaType, val tmdbId: Int) : Screen
  data class Streams(
    val imdbId: String,
    val title: String,
    val tmdbId: Int,
    val mediaType: MediaType,
    val posterUrl: String?,
    val season: Int? = null,
    val episode: Int? = null,
  ) : Screen
}
