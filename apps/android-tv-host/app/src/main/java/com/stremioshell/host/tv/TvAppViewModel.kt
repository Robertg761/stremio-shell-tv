package com.stremioshell.host.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stremioshell.host.tv.data.SettingsStore
import com.stremioshell.host.tv.data.WatchEntry
import com.stremioshell.host.tv.data.WatchStateStore
import com.stremioshell.host.tv.data.addon.AddonClient
import com.stremioshell.host.tv.data.addon.AddonStream
import com.stremioshell.host.tv.data.tmdb.EpisodeItem
import com.stremioshell.host.tv.data.tmdb.MediaDetails
import com.stremioshell.host.tv.data.tmdb.MediaItem
import com.stremioshell.host.tv.data.tmdb.MediaType
import com.stremioshell.host.tv.data.tmdb.TmdbClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeRail(val title: String, val items: List<MediaItem>)

sealed interface LoadState<out T> {
  data object Loading : LoadState<Nothing>
  data class Ready<T>(val value: T) : LoadState<T>
  data class Failed(val message: String) : LoadState<Nothing>
}

class TvAppViewModel(application: Application) : AndroidViewModel(application) {
  val settings = SettingsStore(application)
  val watchState = WatchStateStore(application)
  private val addonClient = AddonClient()

  val tmdbApiKey: StateFlow<String?> = settings.tmdbApiKey
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)
  val addonManifestUrl: StateFlow<String?> = settings.addonManifestUrl
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)
  val continueWatching: StateFlow<List<WatchEntry>> = watchState.entries
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val _homeRails = MutableStateFlow<LoadState<List<HomeRail>>>(LoadState.Loading)
  val homeRails: StateFlow<LoadState<List<HomeRail>>> = _homeRails

  private val _searchResults = MutableStateFlow<LoadState<List<MediaItem>>>(LoadState.Ready(emptyList()))
  val searchResults: StateFlow<LoadState<List<MediaItem>>> = _searchResults

  private val _details = MutableStateFlow<LoadState<MediaDetails>>(LoadState.Loading)
  val details: StateFlow<LoadState<MediaDetails>> = _details

  private val _episodes = MutableStateFlow<LoadState<List<EpisodeItem>>>(LoadState.Ready(emptyList()))
  val episodes: StateFlow<LoadState<List<EpisodeItem>>> = _episodes

  private val _streams = MutableStateFlow<LoadState<List<AddonStream>>>(LoadState.Loading)
  val streams: StateFlow<LoadState<List<AddonStream>>> = _streams

  private var railsLoadedForKey: String? = null

  private fun tmdb(): TmdbClient? = tmdbApiKey.value?.takeIf { it.isNotBlank() }?.let { TmdbClient(it) }

  fun loadHomeRails(force: Boolean = false) {
    val key = tmdbApiKey.value?.takeIf { it.isNotBlank() } ?: return
    if (!force && railsLoadedForKey == key && _homeRails.value is LoadState.Ready) return
    railsLoadedForKey = key
    _homeRails.value = LoadState.Loading
    viewModelScope.launch {
      _homeRails.value = runCatching {
        val client = TmdbClient(key)
        LoadState.Ready(
          listOf(
            HomeRail("Trending Movies", client.trending(MediaType.Movie)),
            HomeRail("Trending Shows", client.trending(MediaType.Show)),
            HomeRail("Popular Movies", client.popular(MediaType.Movie)),
            HomeRail("Popular Shows", client.popular(MediaType.Show)),
          )
        ) as LoadState<List<HomeRail>>
      }.getOrElse { LoadState.Failed(it.message ?: "TMDB request failed") }
    }
  }

  fun search(query: String) {
    val client = tmdb() ?: return
    if (query.isBlank()) {
      _searchResults.value = LoadState.Ready(emptyList())
      return
    }
    _searchResults.value = LoadState.Loading
    viewModelScope.launch {
      _searchResults.value = runCatching {
        LoadState.Ready(client.search(query)) as LoadState<List<MediaItem>>
      }.getOrElse { LoadState.Failed(it.message ?: "Search failed") }
    }
  }

  fun loadDetails(type: MediaType, tmdbId: Int) {
    val client = tmdb() ?: return
    _details.value = LoadState.Loading
    _episodes.value = LoadState.Ready(emptyList())
    viewModelScope.launch {
      _details.value = runCatching {
        LoadState.Ready(client.details(type, tmdbId)) as LoadState<MediaDetails>
      }.getOrElse { LoadState.Failed(it.message ?: "Failed to load details") }
    }
  }

  fun loadSeason(tmdbId: Int, seasonNumber: Int) {
    val client = tmdb() ?: return
    _episodes.value = LoadState.Loading
    viewModelScope.launch {
      _episodes.value = runCatching {
        LoadState.Ready(client.season(tmdbId, seasonNumber)) as LoadState<List<EpisodeItem>>
      }.getOrElse { LoadState.Failed(it.message ?: "Failed to load season") }
    }
  }

  fun loadStreams(imdbId: String, season: Int?, episode: Int?) {
    val manifest = addonManifestUrl.value?.takeIf { it.isNotBlank() }
    if (manifest == null) {
      _streams.value = LoadState.Failed("No addon configured. Set your Comet manifest URL in Settings.")
      return
    }
    _streams.value = LoadState.Loading
    viewModelScope.launch {
      _streams.value = runCatching {
        val streams = if (season != null && episode != null) {
          addonClient.episodeStreams(manifest, imdbId, season, episode)
        } else {
          addonClient.movieStreams(manifest, imdbId)
        }
        LoadState.Ready(streams) as LoadState<List<AddonStream>>
      }.getOrElse { LoadState.Failed(it.message ?: "Addon request failed") }
    }
  }

  fun saveSettings(tmdbKey: String, addonUrl: String, onDone: () -> Unit) {
    viewModelScope.launch {
      settings.setTmdbApiKey(tmdbKey)
      settings.setAddonManifestUrl(addonUrl)
      onDone()
    }
  }
}
