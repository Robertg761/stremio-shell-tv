package com.stremioshell.host.tv.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MediaType { Movie, Show }

/** A row entry on browse/search surfaces. */
data class MediaItem(
  val tmdbId: Int,
  val type: MediaType,
  val title: String,
  val posterUrl: String?,
  val backdropUrl: String?,
  val overview: String,
  val year: String?,
  val rating: Double?,
)

data class SeasonSummary(
  val seasonNumber: Int,
  val name: String,
  val episodeCount: Int,
)

data class MediaDetails(
  val item: MediaItem,
  val imdbId: String?,
  val runtimeMinutes: Int?,
  val genres: List<String>,
  val seasons: List<SeasonSummary>,
)

data class EpisodeItem(
  val seasonNumber: Int,
  val episodeNumber: Int,
  val name: String,
  val overview: String,
  val stillUrl: String?,
  val airDate: String?,
)

// --- Wire models -----------------------------------------------------------

@Serializable
internal data class TmdbPagedResults(val results: List<TmdbEntry> = emptyList())

@Serializable
internal data class TmdbEntry(
  val id: Int,
  @SerialName("media_type") val mediaType: String? = null,
  val title: String? = null,
  val name: String? = null,
  @SerialName("poster_path") val posterPath: String? = null,
  @SerialName("backdrop_path") val backdropPath: String? = null,
  val overview: String = "",
  @SerialName("release_date") val releaseDate: String? = null,
  @SerialName("first_air_date") val firstAirDate: String? = null,
  @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
internal data class TmdbExternalIds(@SerialName("imdb_id") val imdbId: String? = null)

@Serializable
internal data class TmdbGenre(val name: String)

@Serializable
internal data class TmdbSeason(
  @SerialName("season_number") val seasonNumber: Int,
  val name: String = "",
  @SerialName("episode_count") val episodeCount: Int = 0,
)

@Serializable
internal data class TmdbDetailsResponse(
  val id: Int,
  val title: String? = null,
  val name: String? = null,
  @SerialName("poster_path") val posterPath: String? = null,
  @SerialName("backdrop_path") val backdropPath: String? = null,
  val overview: String = "",
  @SerialName("release_date") val releaseDate: String? = null,
  @SerialName("first_air_date") val firstAirDate: String? = null,
  @SerialName("vote_average") val voteAverage: Double? = null,
  val runtime: Int? = null,
  @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
  val genres: List<TmdbGenre> = emptyList(),
  val seasons: List<TmdbSeason> = emptyList(),
  @SerialName("external_ids") val externalIds: TmdbExternalIds? = null,
)

@Serializable
internal data class TmdbSeasonResponse(val episodes: List<TmdbEpisode> = emptyList())

@Serializable
internal data class TmdbEpisode(
  @SerialName("season_number") val seasonNumber: Int,
  @SerialName("episode_number") val episodeNumber: Int,
  val name: String = "",
  val overview: String = "",
  @SerialName("still_path") val stillPath: String? = null,
  @SerialName("air_date") val airDate: String? = null,
)
