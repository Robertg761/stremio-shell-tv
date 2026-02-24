package com.stremioshell.host.compose

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.util.Base64
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.javascriptengine.JavaScriptSandbox
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.stremioshell.host.NetworkMonitor
import com.stremioshell.host.compose.navigation.AppScreen
import com.stremioshell.host.compose.navigation.DeepLinkDestination
import com.stremioshell.host.compose.navigation.DeepLinkRouter
import com.stremioshell.host.compose.screens.RouteUiState
import com.stremioshell.host.core.CoreRuntimeClient
import com.stremioshell.host.core.CoreStateQuery
import com.stremioshell.host.core.CustomAction
import com.stremioshell.host.core.LibrarySyncAction
import com.stremioshell.host.core.PlaybackReportProgressAction
import com.stremioshell.host.core.PlaybackSelectStreamAction
import com.stremioshell.host.core.RuntimeInitializeAction
import com.stremioshell.host.core.mapper.toDiagnosticsLine
import com.stremioshell.host.core.runtime.JsSandboxRuntimeHost
import com.stremioshell.host.core.runtime.NoopRuntimeHost
import com.stremioshell.host.update.UpdateWorkScheduler
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class ComposeMainActivity : ComponentActivity() {
  companion object {
    private const val TAG = "ComposeMainActivity"
  }

  private lateinit var runtimeClient: CoreRuntimeClient
  private lateinit var networkMonitor: NetworkMonitor
  private lateinit var appContainer: AppContainer
  private lateinit var audioManager: AudioManager
  private lateinit var updateController: ComposeUpdateController

  private val pendingDeepLink = MutableStateFlow<DeepLinkDestination?>(null)
  private var lastResolvedDeepLinkRoute: String? = null

  private val audioFocusRequest by lazy {
    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
          .build()
      )
      .setOnAudioFocusChangeListener {}
      .build()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    volumeControlStream = AudioManager.STREAM_MUSIC
    UpdateWorkScheduler.ensureScheduled(this)

    runtimeClient = if (JavaScriptSandbox.isSupported()) {
      JsSandboxRuntimeHost(this)
    } else {
      NoopRuntimeHost()
    }

    appContainer = AppContainer.create(runtimeClient = runtimeClient, lifecycleScope = lifecycleScope)
    updateController = ComposeUpdateController(this, appContainer.diagnosticsStore)
    updateController.onCreate()

    networkMonitor = NetworkMonitor(this) { connected, transport ->
      appContainer.diagnosticsStore.record("network", "connected=$connected transport=$transport")
      dispatchHostSignal(
        signalType = "networkChanged",
        payload = JSONObject().put("connected", connected).put("transport", transport)
      )
    }

    lifecycleScope.launch {
      runCatching {
        runtimeClient.initializeRuntime(RuntimeInitializeAction(source = "host"))
        appContainer.repositories.refreshAll()
        updateController.checkForUpdates(manual = false)
      }.onFailure {
        Log.e(TAG, "initializeRuntime failed", it)
        appContainer.diagnosticsStore.record("runtime", "initialize failure=${it.message}")
      }
    }

    lifecycleScope.launch {
      runtimeClient.events.collectLatest { event ->
        val diagnosticsLine = event.toDiagnosticsLine()
        Log.d(TAG, "runtimeEvent=$diagnosticsLine")
        appContainer.diagnosticsStore.recordRuntimeEvent(diagnosticsLine)
      }
    }

    // Keeps Compose state in sync while core models resolve asynchronously.
    lifecycleScope.launch {
      while (currentCoroutineContext().isActive) {
        delay(2500)
        runCatching {
          appContainer.repositories.catalog.refresh()
          appContainer.repositories.search.refresh()
          appContainer.repositories.meta.refresh()
          appContainer.repositories.library.refresh()
        }
      }
    }

    handleDeepLink(intent)

    setContent {
      val diagnostics = appContainer.diagnosticsStore.entries.collectAsStateWithLifecycle().value
      val deepLinkDestination = pendingDeepLink.collectAsStateWithLifecycle().value
      val sessionState = appContainer.repositories.session.uiState.collectAsStateWithLifecycle().value
      val catalogState = appContainer.repositories.catalog.uiState.collectAsStateWithLifecycle().value
      val metaState = appContainer.repositories.meta.uiState.collectAsStateWithLifecycle().value
      val searchState = appContainer.repositories.search.uiState.collectAsStateWithLifecycle().value
      val libraryState = appContainer.repositories.library.uiState.collectAsStateWithLifecycle().value
      val addonsState = appContainer.repositories.addons.uiState.collectAsStateWithLifecycle().value
      val settingsState = appContainer.repositories.settings.uiState.collectAsStateWithLifecycle().value
      val playbackState = appContainer.repositories.playback.uiState.collectAsStateWithLifecycle().value
      val appState = rememberAppState(diagnosticsStore = appContainer.diagnosticsStore)

      androidx.compose.runtime.LaunchedEffect(deepLinkDestination) {
        deepLinkDestination?.let { destination ->
          appState.navController.navigate(destination.route)
          pendingDeepLink.value = null
        }
      }

      StremioTvApp(
        appState = appState,
        routeUiState = RouteUiState(
          session = sessionState,
          catalog = catalogState,
          meta = metaState,
          search = searchState,
          library = libraryState,
          addons = addonsState,
          settings = settingsState,
          playback = playbackState,
          diagnostics = diagnostics
        ),
        backPolicyManager = appContainer.backPolicyManager,
        actions = AppActions(
          onOpenDiagnostics = {},
          onCheckUpdates = { updateController.checkForUpdates(manual = true) },
          onLibrarySync = {
            lifecycleScope.launch {
              runtimeClient.dispatch(LibrarySyncAction(force = true))
              appContainer.repositories.library.refresh()
            }
          },
          onOpenPlayer = { streamId ->
            lifecycleScope.launch {
              val resolvedStreamId = streamId.trim().ifBlank { "stream" }
              runtimeClient.dispatch(
                PlaybackSelectStreamAction(
                  streamId = resolvedStreamId,
                  streamBase64 = encodeStreamBase64(resolvedStreamId)
                )
              )
              appState.navController.navigate(AppScreen.Player.route)
              appContainer.repositories.playback.refresh()
            }
          },
          onSearchQueryChanged = { query ->
            lifecycleScope.launch {
              runtimeClient.dispatch(
                CustomAction(
                  customType = "updateSearch",
                  customPayload = JSONObject().put("query", query)
                )
              )
              appContainer.repositories.search.refresh()
            }
          },
          onSelectMeta = { id ->
            lifecycleScope.launch {
              runtimeClient.dispatch(
                CustomAction(
                  customType = "selectMeta",
                  customPayload = JSONObject().put("id", id)
                )
              )
              appContainer.repositories.meta.refresh()
              if (id == "home") {
                appState.navigate(AppScreen.Board)
              } else {
                appState.navigate(AppScreen.MetaDetails)
              }
            }
          },
          onInstallAddon = { addon ->
            lifecycleScope.launch {
              runtimeClient.dispatch(
                CustomAction(
                  customType = "installAddon",
                  customPayload = JSONObject().put("name", addon)
                )
              )
              appContainer.repositories.addons.refresh()
            }
          },
          onRemoveAddon = { addon ->
            lifecycleScope.launch {
              runtimeClient.dispatch(
                CustomAction(
                  customType = "removeAddon",
                  customPayload = JSONObject().put("name", addon)
                )
              )
              appContainer.repositories.addons.refresh()
            }
          },
          onSettingChanged = { key, value ->
            lifecycleScope.launch {
              runtimeClient.dispatch(
                CustomAction(
                  customType = "updateSetting",
                  customPayload = JSONObject().put("key", key).put("value", value)
                )
              )
              appContainer.repositories.settings.refresh()
            }
          },
          onBackDecision = { decision ->
            appContainer.diagnosticsStore.recordBackDecision(decision)
            dispatchHostSignal(
              signalType = "backDecision",
              payload = JSONObject().put("decision", decision)
            )
          },
          onFocusRestored = { route ->
            appContainer.diagnosticsStore.recordHostEvent("focus restored route=$route")
            dispatchHostSignal(
              signalType = "focusRestored",
              payload = JSONObject().put("route", route)
            )
          },
          onPlayerDiagnostic = { message ->
            appContainer.diagnosticsStore.record("player", message)
            dispatchHostSignal(
              signalType = "playerDiagnostic",
              payload = JSONObject().put("message", message)
            )
          }
        ),
        onExportDiagnostics = ::exportDiagnostics,
        onPlayerProgress = { progressMs ->
          lifecycleScope.launch {
            val snapshot = runtimeClient.getState(CoreStateQuery(scope = "player"))
            val streamId = (snapshot.data as? JSONObject)
              ?.optString("streamId")
              ?.ifBlank { "compose-player" }
              ?: "compose-player"

            runtimeClient.dispatch(
              PlaybackReportProgressAction(
                streamId = streamId,
                progressMs = progressMs
              )
            )
          }
        },
        exitApp = ::finish
      )
    }

    emitLifecycle("created")
  }

  override fun onStart() {
    super.onStart()
    networkMonitor.start()
    requestAudioFocus()
    emitLifecycle("started")
  }

  override fun onResume() {
    super.onResume()
    requestAudioFocus()
    updateController.onResume()
    emitLifecycle("resumed")
  }

  override fun onPause() {
    emitLifecycle("paused")
    abandonAudioFocus()
    super.onPause()
  }

  override fun onStop() {
    emitLifecycle("stopped")
    networkMonitor.stop()
    super.onStop()
  }

  override fun onDestroy() {
    emitLifecycle("destroyed")
    lifecycleScope.launch { runtimeClient.close() }
    updateController.onDestroy()
    super.onDestroy()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleDeepLink(intent)
  }

  private fun requestAudioFocus() {
    val result = audioManager.requestAudioFocus(audioFocusRequest)
    appContainer.diagnosticsStore.record("audio", "focusRequest=$result")
  }

  private fun abandonAudioFocus() {
    audioManager.abandonAudioFocusRequest(audioFocusRequest)
  }

  private fun emitLifecycle(state: String) {
    appContainer.diagnosticsStore.record("lifecycle", state)
    dispatchHostSignal(
      signalType = "lifecycle",
      payload = JSONObject().put("state", state)
    )
  }

  private fun handleDeepLink(intent: Intent?) {
    val deepLink = intent?.data ?: return
    val destination = DeepLinkRouter.parse(deepLink.toString())
    lastResolvedDeepLinkRoute = destination.route

    appContainer.diagnosticsStore.recordHostEvent("deeplink uri=$deepLink route=${destination.route}")
    dispatchHostSignal(
      signalType = "deeplink",
      payload = JSONObject().put("uri", deepLink.toString()).put("route", destination.route)
    )

    destination.searchQuery?.let { query ->
      lifecycleScope.launch {
        runtimeClient.dispatch(
          CustomAction(
            customType = "updateSearch",
            customPayload = JSONObject().put("query", query)
          )
        )
        appContainer.repositories.search.refresh()
      }
    }

    destination.metaId?.let { metaId ->
      lifecycleScope.launch {
        runtimeClient.dispatch(
          CustomAction(
            customType = "selectMeta",
            customPayload = JSONObject().put("id", metaId)
          )
        )
        appContainer.repositories.meta.refresh()
      }
    }

    pendingDeepLink.value = destination
  }

  private fun dispatchHostSignal(signalType: String, payload: JSONObject) {
    lifecycleScope.launch {
      runCatching {
        runtimeClient.dispatch(
          CustomAction(
            customType = "host.$signalType",
            customPayload = payload
          )
        )
      }.onFailure {
        appContainer.diagnosticsStore.record("runtime", "host signal failed type=$signalType error=${it.message}")
      }
    }
  }

  private fun exportDiagnostics() {
    val text = appContainer.diagnosticsStore.exportText()
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, "Stremio Shell Compose diagnostics")
      putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
      startActivity(Intent.createChooser(shareIntent, "Export diagnostics"))
    }.onFailure {
      Toast.makeText(this, "Unable to export diagnostics.", Toast.LENGTH_SHORT).show()
    }
  }

  @VisibleForTesting
  fun getLastResolvedDeepLinkRoute(): String? = lastResolvedDeepLinkRoute

  private fun encodeStreamBase64(streamId: String): String {
    return Base64.encodeToString(streamId.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
  }
}
