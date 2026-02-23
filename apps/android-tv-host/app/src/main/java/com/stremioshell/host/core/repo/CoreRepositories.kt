package com.stremioshell.host.core.repo

import com.stremioshell.host.core.AuthChangedEvent
import com.stremioshell.host.core.CoreRuntimeClient
import com.stremioshell.host.core.CoreStateQuery
import com.stremioshell.host.core.LibraryChangedEvent
import com.stremioshell.host.core.PlaybackProgressEvent
import com.stremioshell.host.core.TelemetryCoreEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

interface RefreshableRepository {
  fun refresh()
}

data class SessionUiState(
  val isAuthenticated: Boolean = false,
  val userId: String? = null
)

class SessionRepository(
  private val runtime: CoreRuntimeClient,
  private val scope: CoroutineScope
) : RefreshableRepository {
  private val state = MutableStateFlow(SessionUiState())
  val uiState: StateFlow<SessionUiState> = state.asStateFlow()

  init {
    runtime.events.onEach { event ->
      if (event is AuthChangedEvent) {
        state.value = SessionUiState(
          isAuthenticated = event.isAuthenticated,
          userId = event.userId
        )
      }
    }.launchIn(scope)
  }

  override fun refresh() {
    scope.launch {
      val snapshot = runtime.getState(CoreStateQuery(scope = "session"))
      val data = snapshot.data as? JSONObject
      state.value = SessionUiState(
        isAuthenticated = data?.optBoolean("isAuthenticated", false) ?: false,
        userId = data?.optString("userId")?.ifBlank { null }
      )
    }
  }
}

data class CatalogUiState(
  val featuredIds: List<String> = emptyList()
)

class CatalogRepository(
  private val runtime: CoreRuntimeClient,
  private val scope: CoroutineScope
) : RefreshableRepository {
  private val state = MutableStateFlow(CatalogUiState())
  val uiState: StateFlow<CatalogUiState> = state.asStateFlow()

  override fun refresh() {
    scope.launch {
      val snapshot = runtime.getState(CoreStateQuery(scope = "custom", key = "catalog"))
      val data = snapshot.data as? JSONObject
      val ids = data?.optJSONArray("featuredIds").toStringList()
      state.value = CatalogUiState(featuredIds = ids)
    }
  }
}

data class MetaUiState(
  val activeMetaId: String? = null,
  val title: String? = null,
  val subtitle: String? = null
)

class MetaRepository(
  private val runtime: CoreRuntimeClient,
  private val scope: CoroutineScope
) : RefreshableRepository {
  private val state = MutableStateFlow(MetaUiState())
  val uiState: StateFlow<MetaUiState> = state.asStateFlow()

  override fun refresh() {
    scope.launch {
      val snapshot = runtime.getState(CoreStateQuery(scope = "custom", key = "meta"))
      val data = snapshot.data as? JSONObject
      state.value = MetaUiState(
        activeMetaId = data?.optString("activeMetaId")?.ifBlank { null },
        title = data?.optString("title")?.ifBlank { null },
        subtitle = data?.optString("subtitle")?.ifBlank { null }
      )
    }
  }
}

data class SearchUiState(
  val query: String = "",
  val results: List<String> = emptyList()
)

class SearchRepository(
  private val runtime: CoreRuntimeClient,
  private val scope: CoroutineScope
) : RefreshableRepository {
  private val state = MutableStateFlow(SearchUiState())
  val uiState: StateFlow<SearchUiState> = state.asStateFlow()

  override fun refresh() {
    scope.launch {
      val snapshot = runtime.getState(CoreStateQuery(scope = "custom", key = "search"))
      val data = snapshot.data as? JSONObject
      state.value = SearchUiState(
        query = data?.optString("query")?.orEmpty() ?: "",
        results = data?.optJSONArray("results").toStringList()
      )
    }
  }
}

data class LibraryUiState(
  val itemCount: Int = 0,
  val changedItemIds: List<String> = emptyList(),
  val reason: String? = null
)

class LibraryRepository(
  private val runtime: CoreRuntimeClient,
  private val scope: CoroutineScope
) : RefreshableRepository {
  private val state = MutableStateFlow(LibraryUiState())
  val uiState: StateFlow<LibraryUiState> = state.asStateFlow()

  init {
    runtime.events.onEach { event ->
      if (event is LibraryChangedEvent) {
        state.value = LibraryUiState(
          itemCount = event.itemCount ?: state.value.itemCount,
          changedItemIds = event.changedItemIds,
          reason = event.reason
        )
      }
    }.launchIn(scope)
  }

  override fun refresh() {
    scope.launch {
      val snapshot = runtime.getState(CoreStateQuery(scope = "library"))
      val data = snapshot.data as? JSONObject
      state.value = LibraryUiState(
        itemCount = data?.optInt("itemCount", 0) ?: 0,
        changedItemIds = data?.optJSONArray("changedItemIds").toStringList(),
        reason = data?.optString("reason")?.ifBlank { null }
      )
    }
  }
}

data class AddonsUiState(
  val installed: List<String> = emptyList()
)

class AddonsRepository(
  private val runtime: CoreRuntimeClient,
  private val scope: CoroutineScope
) : RefreshableRepository {
  private val state = MutableStateFlow(AddonsUiState())
  val uiState: StateFlow<AddonsUiState> = state.asStateFlow()

  override fun refresh() {
    scope.launch {
      val snapshot = runtime.getState(CoreStateQuery(scope = "addons"))
      val data = snapshot.data as? JSONObject
      state.value = AddonsUiState(
        installed = data?.optJSONArray("installed").toStringList()
      )
    }
  }
}

data class SettingsUiState(
  val values: Map<String, String> = emptyMap()
)

class SettingsRepository(
  private val runtime: CoreRuntimeClient,
  private val scope: CoroutineScope
) : RefreshableRepository {
  private val state = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

  override fun refresh() {
    scope.launch {
      val snapshot = runtime.getState(CoreStateQuery(scope = "custom", key = "settings"))
      val data = snapshot.data as? JSONObject
      state.value = SettingsUiState(values = data.toStringMap())
    }
  }
}

data class PlaybackUiState(
  val streamId: String? = null,
  val progressMs: Long = 0L
)

class PlaybackRepository(
  private val runtime: CoreRuntimeClient,
  private val scope: CoroutineScope
) : RefreshableRepository {
  private val state = MutableStateFlow(PlaybackUiState())
  val uiState: StateFlow<PlaybackUiState> = state.asStateFlow()

  init {
    runtime.events.onEach { event ->
      when (event) {
        is PlaybackProgressEvent -> {
          state.value = PlaybackUiState(
            streamId = event.streamId,
            progressMs = event.progressMs
          )
        }
        is TelemetryCoreEvent -> {
          // Keeps parity with event-driven diagnostics surfaces.
        }
        else -> Unit
      }
    }.launchIn(scope)
  }

  override fun refresh() {
    scope.launch {
      val snapshot = runtime.getState(CoreStateQuery(scope = "player"))
      val data = snapshot.data as? JSONObject
      state.value = PlaybackUiState(
        streamId = data?.optString("streamId")?.ifBlank { null },
        progressMs = data?.optLong("progressMs", 0L) ?: 0L
      )
    }
  }
}

class AppRepositories(
  val session: SessionRepository,
  val catalog: CatalogRepository,
  val meta: MetaRepository,
  val search: SearchRepository,
  val library: LibraryRepository,
  val addons: AddonsRepository,
  val settings: SettingsRepository,
  val playback: PlaybackRepository
) {
  fun refreshAll() {
    session.refresh()
    catalog.refresh()
    meta.refresh()
    search.refresh()
    library.refresh()
    addons.refresh()
    settings.refresh()
    playback.refresh()
  }
}

private fun JSONArray?.toStringList(): List<String> {
  if (this == null) {
    return emptyList()
  }

  val values = mutableListOf<String>()
  for (index in 0 until length()) {
    val value = optString(index).trim()
    if (value.isNotBlank()) {
      values += value
    }
  }
  return values
}

private fun JSONObject?.toStringMap(): Map<String, String> {
  if (this == null) {
    return emptyMap()
  }

  val map = linkedMapOf<String, String>()
  val iterator = keys()
  while (iterator.hasNext()) {
    val key = iterator.next()
    map[key] = optString(key)
  }
  return map
}
