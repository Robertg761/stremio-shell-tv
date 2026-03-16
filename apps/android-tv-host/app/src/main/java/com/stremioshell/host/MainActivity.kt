package com.stremioshell.host

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import com.stremioshell.host.update.ApkUpdateManager
import com.stremioshell.host.update.AutoUpdatePolicy
import com.stremioshell.host.update.UpdateInfo
import com.stremioshell.host.update.UpdateRepository
import com.stremioshell.host.update.UpdateWorkScheduler
import java.io.ByteArrayInputStream
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {
  companion object {
    private const val TAG = "StremioHost"
    private const val STARTUP_TIMEOUT_MS = 12000L
    private const val LOCAL_STREAMING_PORT = 11470
    private const val NATIVE_PLAYBACK_DEDUP_MS = 3000L
    private const val BACK_ACK_TIMEOUT_MS = 450L
  }

  private lateinit var webView: WebView
  private lateinit var networkMonitor: NetworkMonitor
  private lateinit var assetLoader: WebViewAssetLoader
  private lateinit var audioManager: AudioManager
  private lateinit var startupOverlay: LinearLayout
  private lateinit var startupProgress: ProgressBar
  private lateinit var startupTitle: TextView
  private lateinit var startupMessage: TextView
  private lateinit var startupActions: LinearLayout
  private lateinit var retryButton: Button
  private lateinit var exportDiagnosticsButton: Button

  private val bridgeName = "stremioHost"
  private val localShellUrl = "https://appassets.androidplatform.net/assets/web/index.html"
  private val startupHandler = Handler(Looper.getMainLooper())
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

  private var webReady = false
  private var startupCompleted = false
  private var shellSource: String = "unknown"
  private var usingLocalDebugServer = false
  private var attemptedDebugFallback = false
  private var attemptedRemoteFallback = false
  private var lastNativePlaybackUrl: String? = null
  private var lastNativePlaybackAtMs: Long = 0L
  private var lastStructuredPlaybackAtMs: Long = 0L
  private val nativeFallbackLoopGuard = NativePlaybackLoopGuard()
  private val backHandshakeController = BackHandshakeController(timeoutMs = BACK_ACK_TIMEOUT_MS)
  private val backAckTimeoutRunnables = mutableMapOf<String, Runnable>()
  private val pendingEvents = mutableListOf<String>()
  private val diagnostics = ArrayDeque<String>()
  private val updateRepository = UpdateRepository()
  private val apkUpdateManager = ApkUpdateManager()
  private var updateCheckInFlight = false
  private var promptedDownloadedVersion: String? = null
  private var awaitingUnknownSourcesPermissionVersion: String? = null
  private var activeUpdateDialog: AlertDialog? = null
  private var updateReceiverRegistered = false
  private val startupTimeoutRunnable = Runnable {
    if (startupCompleted) {
      return@Runnable
    }
    appendDiagnostic("Startup timeout while source=$shellSource.")
    if (!attemptedRemoteFallback && BuildConfig.WEB_REMOTE_FALLBACK_URL.isNotBlank()) {
      loadRemoteFallback("startup_timeout")
    } else {
      showStartupFailure(
        getString(R.string.startup_timeout_title),
        getString(R.string.startup_timeout_message)
      )
    }
  }

  private val playbackEventReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val eventJson = intent.getStringExtra(PlaybackBridge.EXTRA_EVENT_JSON) ?: return
      parsePlaybackResultPayload(eventJson)?.let { payload ->
        onPlaybackResult(payload)
      }
      emitHostEventJson(eventJson)
    }
  }
  private val updateDownloadReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
        return
      }

      val completedDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
      val trackedDownloadId = apkUpdateManager.getActiveDownloadId(this@MainActivity)
      if (completedDownloadId <= 0L || trackedDownloadId == null || completedDownloadId != trackedDownloadId) {
        return
      }

      val result = apkUpdateManager.queryDownload(this@MainActivity)
      if (result?.status == DownloadManager.STATUS_SUCCESSFUL) {
        appendDiagnostic("Update download completed id=$completedDownloadId.")
        maybePromptForDownloadedUpdate(force = true)
      } else {
        appendDiagnostic("Update download failed id=$completedDownloadId reason=${result?.reason}.")
        apkUpdateManager.clearDownloadedState(this@MainActivity, deleteApk = true)
        resetUpdatePromptState()
        Toast.makeText(this@MainActivity, getString(R.string.update_download_failed), Toast.LENGTH_LONG).show()
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    volumeControlStream = AudioManager.STREAM_MUSIC

    webView = findViewById(R.id.webView)
    startupOverlay = findViewById(R.id.startupOverlay)
    startupProgress = findViewById(R.id.startupProgress)
    startupTitle = findViewById(R.id.startupTitle)
    startupMessage = findViewById(R.id.startupMessage)
    startupActions = findViewById(R.id.startupActions)
    retryButton = findViewById(R.id.retryButton)
    exportDiagnosticsButton = findViewById(R.id.exportDiagnosticsButton)

    initializeStartupOverlay()
    configureWebView()
    UpdateWorkScheduler.ensureScheduled(this)

    networkMonitor = NetworkMonitor(this) { connected, transport ->
      runOnUiThread {
        val payload = JSONObject()
          .put("connected", connected)
          .put("transport", transport)
        emitHostEvent("network.changed", payload)
      }
    }

    registerPlaybackEvents()
    registerUpdateDownloadReceiver()
    setupBackHandling()
    loadShell()
    handleDeepLink(intent)
    maybePromptForDownloadedUpdate(force = false)
    checkForUpdates(manual = false)

    emitLifecycle("created")
    appendDiagnostic("MainActivity created for ${if (BuildConfig.IS_TV) "tv" else "mobile"} flavor.")
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleDeepLink(intent)
  }

  override fun onStart() {
    super.onStart()
    networkMonitor.start()
    emitLifecycle("started")
  }

  override fun onResume() {
    super.onResume()
    requestAudioFocus()
    applyWebMediaAudioGuard()
    appendAudioStateDiagnostic("onResume")
    if (awaitingUnknownSourcesPermissionVersion != null && !apkUpdateManager.needsUnknownSourcesPermission(this)) {
      maybePromptForDownloadedUpdate(force = true)
    }
    maybePromptForDownloadedUpdate(force = false)
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
    startupHandler.removeCallbacksAndMessages(null)
    backAckTimeoutRunnables.clear()
    backHandshakeController.clear()
    abandonAudioFocus()
    emitLifecycle("destroyed")
    activeUpdateDialog?.dismiss()
    if (updateReceiverRegistered) {
      unregisterReceiver(updateDownloadReceiver)
      updateReceiverRegistered = false
    }
    unregisterReceiver(playbackEventReceiver)
    webView.removeJavascriptInterface(bridgeName)
    webView.destroy()
    super.onDestroy()
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (BuildConfig.IS_TV) {
      if (BuildConfig.DEBUG && event.action == KeyEvent.ACTION_DOWN) {
        appendDiagnostic("TV key event passthrough code=${event.keyCode} webReady=$webReady")
      }
    }

    return super.dispatchKeyEvent(event)
  }

  override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
    if (BuildConfig.IS_TV && event.action == MotionEvent.ACTION_SCROLL) {
      // Prevent touchpad/rotary scroll gestures from scrolling WebView content on TV.
      return true
    }
    return super.dispatchGenericMotionEvent(event)
  }

  private fun configureWebView() {
    assetLoader = WebViewAssetLoader.Builder()
      .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
      .build()

    with(webView.settings) {
      javaScriptEnabled = true
      domStorageEnabled = true
      mediaPlaybackRequiresUserGesture = false
      allowFileAccess = false
      mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      if (BuildConfig.IS_TV) {
        val currentUserAgent = userAgentString.orEmpty()
        if (!currentUserAgent.contains("Android TV")) {
          userAgentString = "$currentUserAgent StremioShellTV Android TV"
        }
      }
    }

    webView.webChromeClient = object : WebChromeClient() {
      override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        Log.d(
          TAG,
          "WebConsole ${consoleMessage.messageLevel()} ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}"
        )
        return true
      }
    }

    webView.addJavascriptInterface(WebCommandBridge(::handleCommandFromWeb), bridgeName)
    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val target = extractLocalStreamingTarget(request.url) ?: return false
        return maybeOpenNativePlaybackFromStreamingServer(target.streamId, target.mediaUrl, request.url.toString())
      }

      override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        interceptStreamingServerRequest(request.url)?.let { return it }
        return assetLoader.shouldInterceptRequest(request.url)
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        appendDiagnostic("onPageFinished source=$shellSource url=$url")
        webReady = true
        installNativePlaybackCommandAdapter()
        installPlaybackHandoffShim()
        flushPendingEvents()
        applyWebMediaAudioGuard()
        probeForRenderedContent()
      }

      override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
      ) {
        val isMainFrame = request?.isForMainFrame == true
        Log.e(
          TAG,
          "WebView load error mainFrame=$isMainFrame code=${error?.errorCode} description=${error?.description} url=${request?.url}"
        )
        appendDiagnostic(
          "WebView load error source=$shellSource code=${error?.errorCode} description=${error?.description} url=${request?.url}"
        )
        if (!isMainFrame) {
          return
        }

        if (usingLocalDebugServer && !attemptedDebugFallback) {
          attemptedDebugFallback = true
          appendDiagnostic("Debug URL failed, falling back to bundled web assets.")
          Toast.makeText(this@MainActivity, "Dev server unavailable, using bundled shell.", Toast.LENGTH_SHORT).show()
          loadLocalShell()
          return
        }

        if (!attemptedRemoteFallback && BuildConfig.WEB_REMOTE_FALLBACK_URL.isNotBlank()) {
          loadRemoteFallback("main_frame_error")
          return
        }

        showStartupFailure(
          getString(R.string.startup_error_title),
          getString(R.string.startup_error_message)
        )
      }
    }
  }

  private fun registerPlaybackEvents() {
    ContextCompat.registerReceiver(
      this,
      playbackEventReceiver,
      IntentFilter(PlaybackBridge.ACTION_PLAYBACK_EVENT),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
  }

  private fun registerUpdateDownloadReceiver() {
    ContextCompat.registerReceiver(
      this,
      updateDownloadReceiver,
      IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
    updateReceiverRegistered = true
  }

  private fun checkForUpdates(manual: Boolean) {
    if (updateCheckInFlight) {
      if (manual) {
        Toast.makeText(this, getString(R.string.check_updates_in_progress), Toast.LENGTH_SHORT).show()
      }
      return
    }
    if (BuildConfig.DEBUG) {
      if (manual) {
        Toast.makeText(this, getString(R.string.check_updates_unavailable_debug), Toast.LENGTH_SHORT).show()
      }
      return
    }

    val owner = BuildConfig.GITHUB_RELEASE_OWNER.trim()
    val repo = BuildConfig.GITHUB_RELEASE_REPO.trim()
    if (owner.isBlank() || repo.isBlank()) {
      appendDiagnostic("Skipping update check because release repo is not configured.")
      if (manual) {
        Toast.makeText(this, getString(R.string.check_updates_failed), Toast.LENGTH_SHORT).show()
      }
      return
    }

    updateCheckInFlight = true
    Thread {
      val result = runCatching {
        updateRepository.checkForUpdate(
          owner = owner,
          repo = repo,
          currentVersionName = BuildConfig.VERSION_NAME,
          isTvFlavor = BuildConfig.IS_TV
        )
      }

      runOnUiThread {
        updateCheckInFlight = false
        if (isFinishing || isDestroyed) {
          return@runOnUiThread
        }
        result.exceptionOrNull()?.let { error ->
          appendDiagnostic("Update check failed: ${error.message}")
          if (manual) {
            Toast.makeText(this, getString(R.string.check_updates_failed), Toast.LENGTH_SHORT).show()
          }
        }
        val info = result.getOrNull()
        if (info != null) {
          if (manual) {
            maybeShowUpdateAvailableDialog(info)
          } else {
            maybeAutoDownloadUpdate(info)
          }
        } else if (manual) {
          Toast.makeText(this, getString(R.string.check_updates_none), Toast.LENGTH_SHORT).show()
        }
      }
    }.start()
  }

  private fun maybeAutoDownloadUpdate(info: UpdateInfo) {
    val decision = AutoUpdatePolicy.decide(
      updateInfo = info,
      hasDownloadedForVersion = apkUpdateManager.hasDownloadedApkForVersion(this, info.latestVersionName),
      hasActiveDownload = apkUpdateManager.isDownloadInProgress(this)
    )

    when (decision) {
      AutoUpdatePolicy.Decision.NO_UPDATE -> return
      AutoUpdatePolicy.Decision.DOWNLOAD_IN_PROGRESS -> {
        appendDiagnostic("Background update download already in progress; skipping new request.")
        return
      }
      AutoUpdatePolicy.Decision.ALREADY_DOWNLOADED -> {
        appendDiagnostic("Update ${info.latestVersionName} already downloaded; prompting install.")
        maybePromptForDownloadedUpdate(force = true)
        return
      }
      AutoUpdatePolicy.Decision.START_DOWNLOAD -> {
        startUpdateDownload(info, showUserFeedback = false)
      }
    }
  }

  private fun maybeShowUpdateAvailableDialog(info: UpdateInfo) {
    if (apkUpdateManager.hasDownloadedApkForVersion(this, info.latestVersionName)) {
      maybePromptForDownloadedUpdate(force = true)
      return
    }
    if (activeUpdateDialog?.isShowing == true) {
      return
    }

    val releaseNotes = info.releaseNotes.trim()
    val summary = releaseNotes
      .lineSequence()
      .filter { it.isNotBlank() }
      .take(6)
      .joinToString(separator = "\n")
      .ifBlank { "A newer build is available on GitHub Releases." }
    val suffix = if (releaseNotes.length > summary.length) "\n\n..." else ""
    val publishedAt = info.publishedAt?.let { "\nPublished: $it" } ?: ""
    val message = buildString {
      append(summary)
      append(suffix)
      append(publishedAt)
    }

    activeUpdateDialog = AlertDialog.Builder(this)
      .setTitle(getString(R.string.update_available_title, info.latestVersionName))
      .setMessage(message)
      .setPositiveButton(R.string.update_download_button) { _, _ ->
        startUpdateDownload(info)
      }
      .setNeutralButton(R.string.update_view_release_button) { _, _ ->
        info.releaseUrl.takeIf { it.isNotBlank() }?.let { releaseUrl ->
          openExternalUrl(JSONObject().put("url", releaseUrl))
        }
      }
      .setNegativeButton(R.string.update_later_button, null)
      .setOnDismissListener {
        activeUpdateDialog = null
      }
      .show()
  }

  private fun startUpdateDownload(info: UpdateInfo, showUserFeedback: Boolean = true) {
    runCatching {
      apkUpdateManager.clearDownloadedState(this, deleteApk = true)
      resetUpdatePromptState()
      val downloadId = apkUpdateManager.startDownload(this, info)
      appendDiagnostic("Started update download id=$downloadId version=${info.latestVersionName}.")
      if (showUserFeedback) {
        Toast.makeText(
          this,
          getString(R.string.update_download_started, info.latestVersionName),
          Toast.LENGTH_LONG
        ).show()
      }
    }.onFailure {
      appendDiagnostic("Failed to start update download: ${it.message}")
      if (showUserFeedback) {
        Toast.makeText(this, getString(R.string.update_download_failed), Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun maybePromptForDownloadedUpdate(force: Boolean) {
    if (!apkUpdateManager.hasPendingDownloadedUpdate(this, BuildConfig.VERSION_NAME)) {
      resetUpdatePromptState()
      return
    }

    val downloadedVersion = apkUpdateManager.getDownloadedVersionName(this) ?: return

    if (apkUpdateManager.needsUnknownSourcesPermission(this)) {
      awaitingUnknownSourcesPermissionVersion = downloadedVersion
      showEnableUnknownSourcesDialog(downloadedVersion)
    } else {
      val shouldForcePrompt = force || awaitingUnknownSourcesPermissionVersion == downloadedVersion
      awaitingUnknownSourcesPermissionVersion = null
      if (!shouldForcePrompt && promptedDownloadedVersion == downloadedVersion) {
        return
      }
      promptedDownloadedVersion = downloadedVersion
      showInstallDownloadedUpdateDialog(downloadedVersion)
    }
  }

  private fun showEnableUnknownSourcesDialog(version: String) {
    if (activeUpdateDialog?.isShowing == true) {
      return
    }
    activeUpdateDialog = AlertDialog.Builder(this)
      .setTitle(getString(R.string.update_download_ready_title))
      .setMessage(
        getString(R.string.update_download_ready_message, version) +
          "\n\n" +
          getString(R.string.update_enable_installs_message)
      )
      .setPositiveButton(R.string.update_enable_installs_button) { _, _ ->
        startActivity(apkUpdateManager.buildUnknownSourcesSettingsIntent(this))
      }
      .setNegativeButton(R.string.update_later_button, null)
      .setOnDismissListener {
        activeUpdateDialog = null
      }
      .show()
  }

  private fun showInstallDownloadedUpdateDialog(version: String) {
    if (activeUpdateDialog?.isShowing == true) {
      return
    }
    activeUpdateDialog = AlertDialog.Builder(this)
      .setTitle(getString(R.string.update_download_ready_title))
      .setMessage(getString(R.string.update_download_ready_message, version))
      .setPositiveButton(R.string.update_install_button) { _, _ ->
        launchDownloadedInstaller()
      }
      .setNegativeButton(R.string.update_later_button, null)
      .setOnDismissListener {
        activeUpdateDialog = null
      }
      .show()
  }

  private fun launchDownloadedInstaller() {
    val installIntent = apkUpdateManager.buildInstallIntentFromDownloadedApk(this)
    if (installIntent != null) {
      startActivity(installIntent)
      return
    }

    if (apkUpdateManager.needsUnknownSourcesPermission(this)) {
      val version = apkUpdateManager.getDownloadedVersionName(this) ?: "latest"
      showEnableUnknownSourcesDialog(version)
      return
    }

    Toast.makeText(this, getString(R.string.update_install_failed), Toast.LENGTH_LONG).show()
  }

  private fun setupBackHandling() {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        requestBackHandling(source = "hardware")
      }
    })
  }

  private fun requestBackHandling(source: String) {
    if (!webReady) {
      appendDiagnostic("Back request received before web ready; applying native fallback immediately.")
      applyNativeBackFallback(reason = "web_not_ready", requestId = null)
      return
    }

    val existing = backHandshakeController.pendingRequestId()
    if (existing != null) {
      appendDiagnostic("Back request ignored because pending request still active id=$existing")
      return
    }

    val requestId = UUID.randomUUID().toString()
    val pending = backHandshakeController.begin(requestId)
    if (pending == null) {
      appendDiagnostic("Back request setup failed id=$requestId; applying native fallback.")
      applyNativeBackFallback(reason = "handshake_begin_failed", requestId = requestId)
      return
    }

    appendDiagnostic("Back request dispatched id=${pending.requestId} source=$source timeoutMs=$BACK_ACK_TIMEOUT_MS")
    emitHostEvent(
      "back.pressed",
      JSONObject()
        .put("source", source)
        .put("requestId", pending.requestId)
    )
    scheduleBackAckTimeout(pending.requestId)
  }

  private fun scheduleBackAckTimeout(requestId: String) {
    val runnable = Runnable {
      backAckTimeoutRunnables.remove(requestId)
      when (val resolution = backHandshakeController.onTimeout(requestId)) {
        is BackHandshakeResolution.RunNativeFallback -> {
          appendDiagnostic("Back ack timeout id=${resolution.requestId} reason=${resolution.reason}")
          applyNativeBackFallback(reason = resolution.reason, requestId = resolution.requestId)
        }
        is BackHandshakeResolution.Ignored -> {
          appendDiagnostic("Back ack timeout ignored id=$requestId reason=${resolution.reason}")
        }
        is BackHandshakeResolution.Handled -> {
          appendDiagnostic("Back ack timeout resolved as handled id=${resolution.requestId}")
        }
      }
    }
    backAckTimeoutRunnables[requestId] = runnable
    startupHandler.postDelayed(runnable, BACK_ACK_TIMEOUT_MS)
  }

  private fun cancelBackAckTimeout(requestId: String) {
    val runnable = backAckTimeoutRunnables.remove(requestId) ?: return
    startupHandler.removeCallbacks(runnable)
  }

  private fun handleBackHandledCommand(payload: JSONObject) {
    val requestId = payload.optString("requestId").trim()
    if (requestId.isEmpty()) {
      appendDiagnostic("Ignoring back.handled without requestId.")
      return
    }

    val handled = payload.optBoolean("handled", false)
    val reason = payload.optString("reason").ifBlank { null }
    cancelBackAckTimeout(requestId)

    when (val resolution = backHandshakeController.acknowledge(requestId, handled, reason)) {
      is BackHandshakeResolution.Handled -> {
        appendDiagnostic("Back ack handled id=${resolution.requestId} reason=${resolution.reason ?: "none"}")
      }
      is BackHandshakeResolution.RunNativeFallback -> {
        appendDiagnostic("Back ack unhandled id=${resolution.requestId} reason=${resolution.reason}; applying native fallback.")
        applyNativeBackFallback(reason = resolution.reason, requestId = resolution.requestId)
      }
      is BackHandshakeResolution.Ignored -> {
        appendDiagnostic("Ignoring back.handled id=$requestId reason=${resolution.reason}")
      }
    }
  }

  private fun applyNativeBackFallback(reason: String, requestId: String?) {
    appendDiagnostic("Applying native back fallback id=${requestId ?: "none"} reason=$reason")
    if (webReady) {
      val script = """
        (() => {
          try {
            const currentHash = String(window.location.hash || '');
            const rootHashes = new Set(['', '#', '#/', '#/board']);
            if (!rootHashes.has(currentHash) && window.history && typeof window.history.back === 'function') {
              window.history.back();
              return 'router_back';
            }
          } catch (_) {}
          return 'native_back';
        })();
      """.trimIndent()
      webView.evaluateJavascript(script) { rawResult ->
        if (rawResult?.trim('"') == "router_back") {
          appendDiagnostic("Back fallback resolved via router history.")
          requestWebViewFocusIfTv()
          return@evaluateJavascript
        }
        runNativeHistoryFallback()
      }
      return
    }

    runNativeHistoryFallback()
  }

  private fun runNativeHistoryFallback() {
    if (webView.canGoBack()) {
      webView.goBack()
      requestWebViewFocusIfTv()
    } else {
      finish()
    }
  }

  private fun loadShell() {
    if (BuildConfig.DEBUG && BuildConfig.WEB_APP_URL.isNotBlank()) {
      usingLocalDebugServer = isLocalDebugUrl(BuildConfig.WEB_APP_URL)
      val source = if (usingLocalDebugServer) "debug" else "remote"
      loadShellUrl(
        source = source,
        url = BuildConfig.WEB_APP_URL,
        diagnostic = "Loading debug web shell from ${BuildConfig.WEB_APP_URL}"
      )
      return
    }

    usingLocalDebugServer = false
    loadLocalShell()
  }

  private fun loadLocalShell() {
    usingLocalDebugServer = false
    loadShellUrl(
      source = "bundled",
      url = localShellUrl,
      diagnostic = "Loading bundled web shell from assets."
    )
  }

  private fun loadRemoteFallback(reason: String) {
    val fallbackUrl = BuildConfig.WEB_REMOTE_FALLBACK_URL
    if (fallbackUrl.isBlank()) {
      showStartupFailure(
        getString(R.string.startup_error_title),
        getString(R.string.startup_error_message)
      )
      return
    }

    attemptedRemoteFallback = true
    usingLocalDebugServer = false
    loadShellUrl(
      source = "remote",
      url = fallbackUrl,
      diagnostic = "Loading remote fallback shell due to $reason -> $fallbackUrl",
      toastMessage = "Local shell unavailable, loading fallback."
    )
  }

  private fun handleDeepLink(intent: Intent?) {
    val deepLink = intent?.data ?: return
    val payload = JSONObject().put("url", deepLink.toString())
    emitHostEvent("deepLink.received", payload)
  }

  private fun emitLifecycle(state: String) {
    emitHostEvent("lifecycle.changed", JSONObject().put("state", state))
  }

  private fun handleCommandFromWeb(commandJson: String) {
    runOnUiThread {
      HostBridgeContract.parseCommand(commandJson).fold(
        onSuccess = { envelope ->
          if (!isCommandAllowedForCurrentShell(envelope.type)) {
            appendDiagnostic("Blocked command ${envelope.type} while source=$shellSource.")
            return@fold
          }

          appendDiagnostic("Received host command: ${envelope.type}")
          when (envelope.type) {
            "playback.open" -> {
              lastStructuredPlaybackAtMs = System.currentTimeMillis()
              val outcome = openPlayback(envelope.payload)
              handlePlaybackOpenOutcome(outcome, source = "host_command")
            }
            "playback.close" -> PlaybackBridge.requestPlaybackClose(this)
            "back.handled" -> handleBackHandledCommand(envelope.payload)
            "external.openUrl" -> openExternalUrl(envelope.payload)
            "diagnostics.export" -> exportDiagnostics()
            "updates.check" -> checkForUpdates(manual = true)
            else -> {
              appendDiagnostic("Ignoring unsupported command: ${envelope.type}")
            }
          }
        },
        onFailure = { error ->
          appendDiagnostic("Invalid command envelope: ${error.message}")
          val payload = HostBridgeContract.createPlaybackPayload(
            status = "failed",
            errorCode = "invalid_command",
            message = error.message ?: "Invalid command envelope."
          )
          dispatchPlaybackResult(payload)
        }
      )
    }
  }

  private fun openPlayback(payload: JSONObject): PlaybackOpenOutcome {
    val request = NativePlaybackContracts.fromPayload(payload)
    val outcome = PlaybackOpenDecider.classify(
      payload = payload,
      request = request,
      shouldSkipNativePlayback = request?.let {
        nativeFallbackLoopGuard.shouldSkip(it.url, it.streamId)
      } ?: false
    )
    if (outcome.decision != PlaybackOpenDecision.OPENED) {
      return outcome
    }

    val activeRequest = request ?: return PlaybackOpenOutcome(
      decision = PlaybackOpenDecision.INVALID_PAYLOAD,
      streamId = payload.optString("streamId").ifBlank { null }
    )

    val settingsJson = payload.optJSONObject("settings")
    val tracksJson = payload.optJSONObject("tracks")
    val playbackPositionMs = activeRequest.resumePositionMs ?: activeRequest.positionMs

    val playerIntent = Intent(this, PlayerActivity::class.java).apply {
      putExtra(PlaybackBridge.EXTRA_URL, activeRequest.url)
      putExtra(PlaybackBridge.EXTRA_STREAM_ID, activeRequest.streamId)
      putExtra(PlaybackBridge.EXTRA_TITLE, activeRequest.title)
      putExtra(PlaybackBridge.EXTRA_SUBTITLE, activeRequest.subtitle)
      putExtra(PlaybackBridge.EXTRA_POSITION_MS, playbackPositionMs)
      putExtra(PlaybackBridge.EXTRA_ARTWORK_URL, activeRequest.artworkUrl)
      putExtra(PlaybackBridge.EXTRA_LOGO_URL, activeRequest.logoUrl)
      putExtra(PlaybackBridge.EXTRA_RESUME_POSITION_MS, activeRequest.resumePositionMs ?: -1L)
      putExtra(PlaybackBridge.EXTRA_FALLBACK_WEB_URL, activeRequest.fallbackWebUrl)
      putExtra(PlaybackBridge.EXTRA_SOURCE_URL, activeRequest.sourceUrl)
      if (settingsJson != null) {
        putExtra(PlaybackBridge.EXTRA_SETTINGS_JSON, settingsJson.toString())
      }
      if (tracksJson != null) {
        putExtra(PlaybackBridge.EXTRA_TRACKS_JSON, tracksJson.toString())
      }
      if (activeRequest.navigationContext != null) {
        putExtra(PlaybackBridge.EXTRA_NAVIGATION_CONTEXT_JSON, activeRequest.navigationContext.toString())
      }
    }

    appendDiagnostic(
      "Opening native playback streamId=${activeRequest.streamId} url=${activeRequest.url.take(120)} fallback=${activeRequest.fallbackWebUrl}"
    )
    startActivity(playerIntent)
    return outcome
  }

  private fun handlePlaybackOpenOutcome(outcome: PlaybackOpenOutcome, source: String): Boolean {
    when (outcome.decision) {
      PlaybackOpenDecision.OPENED -> return true
      PlaybackOpenDecision.INVALID_PAYLOAD -> {
        appendDiagnostic("Invalid playback.open payload source=$source streamId=${outcome.streamId ?: "none"}")
        val errorPayload = HostBridgeContract.createPlaybackPayload(
          status = "failed",
          streamId = outcome.streamId,
          errorCode = "missing_url",
          message = "playback.open command requires payload.url",
          url = outcome.url,
          fallbackWebUrl = outcome.fallbackWebUrl,
          failureDomain = "host",
          failureDetail = PlaybackOpenDecider.INVALID_OPEN_PAYLOAD_FAILURE_DETAIL
        )
        dispatchPlaybackResult(errorPayload)
      }
      PlaybackOpenDecision.BLOCKED_BY_LOOP_GUARD -> {
        appendDiagnostic(
          "Native playback blocked by loop guard source=$source streamId=${outcome.streamId} url=${outcome.url?.take(120)}; dispatching fallback."
        )
        dispatchLoopGuardPlaybackFailure(
          streamId = outcome.streamId,
          url = outcome.url,
          fallbackWebUrl = outcome.fallbackWebUrl
        )
      }
    }
    return false
  }

  private fun dispatchLoopGuardPlaybackFailure(streamId: String?, url: String?, fallbackWebUrl: String?) {
    val payload = HostBridgeContract.createPlaybackPayload(
      status = "failed",
      streamId = streamId,
      errorCode = "loop_guard_skip",
      message = "Native playback launch skipped due to loop guard.",
      url = url,
      fallbackWebUrl = fallbackWebUrl,
      fallbackTriggered = true,
      failureDomain = "host",
      failureDetail = PlaybackOpenDecider.LOOP_GUARD_FAILURE_DETAIL
    )
    dispatchPlaybackResult(payload, processFallback = true)
  }

  private fun dispatchPlaybackResult(payload: JSONObject, processFallback: Boolean = false) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      runOnUiThread {
        dispatchPlaybackResult(payload, processFallback)
      }
      return
    }

    if (processFallback) {
      onPlaybackResult(payload)
    }
    val envelope = HostBridgeContract.createEventEnvelope("playback.result", payload)
    emitHostEventJson(envelope.toString())
  }

  private fun openExternalUrl(payload: JSONObject) {
    val url = payload.optString("url")
    if (url.isBlank()) {
      appendDiagnostic("external.openUrl ignored due to blank payload.url")
      return
    }

    val parsed = runCatching { Uri.parse(url) }.getOrNull()
    val scheme = parsed?.scheme?.lowercase(Locale.US)
    if (scheme !in setOf("https", "http")) {
      appendDiagnostic("external.openUrl blocked due to unsupported scheme: ${scheme ?: "none"}")
      return
    }
    val safeUri = parsed ?: return

    runCatching {
      val intent = Intent(Intent.ACTION_VIEW, safeUri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      startActivity(intent)
    }.onFailure {
      appendDiagnostic("Failed to open external URL: ${it.message}")
    }
  }

  private fun exportDiagnostics() {
    val text = buildString {
      appendLine("Stremio Shell diagnostics")
      appendLine("flavor=${if (BuildConfig.IS_TV) "tv" else "mobile"}")
      appendLine("timestamp=${System.currentTimeMillis()}")
      appendLine()
      diagnostics.forEach { appendLine(it) }
    }

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, "Stremio Shell diagnostics")
      putExtra(Intent.EXTRA_TEXT, text)
    }

    startActivity(Intent.createChooser(shareIntent, getString(R.string.export_diagnostics_title)))
  }

  private fun initializeStartupOverlay() {
    retryButton.setOnClickListener {
      appendDiagnostic("Manual startup retry requested by user.")
      attemptedDebugFallback = false
      attemptedRemoteFallback = false
      loadShell()
    }
    exportDiagnosticsButton.setOnClickListener { exportDiagnostics() }
    startupOverlay.visibility = View.VISIBLE
  }

  private fun startStartupWatchdog() {
    startupHandler.removeCallbacks(startupTimeoutRunnable)
    startupHandler.postDelayed(startupTimeoutRunnable, STARTUP_TIMEOUT_MS)
  }

  private fun probeForRenderedContent() {
    val probeScript = """
      (() => {
        try {
          const root = document.getElementById('root') || document.getElementById('app') || document.body;
          const text = (root && root.innerText ? root.innerText : '').trim();
          const result = {
            rootExists: !!root,
            childCount: root ? root.childElementCount : 0,
            textLength: text.length
          };
          return JSON.stringify(result);
        } catch (error) {
          return JSON.stringify({ error: String(error) });
        }
      })();
    """.trimIndent()

    webView.evaluateJavascript(probeScript) { rawResult ->
      if (startupCompleted) {
        return@evaluateJavascript
      }

      val probe = parseProbeResult(rawResult)

      if (probe == null) {
        appendDiagnostic("Startup probe parse failed for source=$shellSource: $rawResult")
        markStartupComplete()
        return@evaluateJavascript
      }

      val childCount = probe.optInt("childCount", 0)
      val textLength = probe.optInt("textLength", 0)
      appendDiagnostic("Startup probe source=$shellSource childCount=$childCount textLength=$textLength")

      if (childCount > 0 || textLength > 0 || shellSource == "remote") {
        markStartupComplete()
        return@evaluateJavascript
      }

      if (shellSource == "debug" && usingLocalDebugServer && !attemptedDebugFallback) {
        attemptedDebugFallback = true
        appendDiagnostic("Debug shell rendered blank, falling back to bundled shell.")
        loadLocalShell()
        return@evaluateJavascript
      }

      if (shellSource != "remote" && !attemptedRemoteFallback && BuildConfig.WEB_REMOTE_FALLBACK_URL.isNotBlank()) {
        loadRemoteFallback("blank_render_probe")
        return@evaluateJavascript
      }

      showStartupFailure(
        getString(R.string.startup_error_title),
        getString(R.string.startup_error_message)
      )
    }
  }

  private fun parseProbeResult(rawResult: String): JSONObject? {
    val parsed = runCatching { JSONTokener(rawResult).nextValue() }.getOrNull() ?: return null
    return when (parsed) {
      is JSONObject -> parsed
      is String -> runCatching { JSONObject(parsed) }.getOrNull()
      else -> null
    }
  }

  private fun markStartupComplete() {
    startupCompleted = true
    startupHandler.removeCallbacks(startupTimeoutRunnable)
    startupOverlay.visibility = View.GONE
    requestWebViewFocusIfTv()
    appendDiagnostic("Startup completed with source=$shellSource.")
  }

  private fun requestWebViewFocusIfTv() {
    if (BuildConfig.IS_TV) {
      webView.requestFocus()
    }
  }

  private fun showStartupLoading(title: String, message: String) {
    startupOverlay.visibility = View.VISIBLE
    startupProgress.visibility = View.VISIBLE
    startupActions.visibility = View.GONE
    startupTitle.text = title
    startupMessage.text = message
  }

  private fun showStartupFailure(title: String, message: String) {
    startupCompleted = false
    startupHandler.removeCallbacks(startupTimeoutRunnable)
    startupOverlay.visibility = View.VISIBLE
    startupProgress.visibility = View.GONE
    startupActions.visibility = View.VISIBLE
    startupTitle.text = title
    startupMessage.text = message
    appendDiagnostic("Startup failure shown: $title - $message")
  }

  private fun isLocalDebugUrl(url: String): Boolean {
    val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
    return host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost"
  }

  private fun appendDiagnostic(entry: String) {
    val formatted = "${timestamp()} $entry"
    Log.d(TAG, formatted)
    diagnostics.addLast(formatted)
    while (diagnostics.size > 200) {
      diagnostics.removeFirst()
    }
  }

  private fun timestamp(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
  }

  private fun emitHostEvent(type: String, payload: JSONObject) {
    val envelope = HostBridgeContract.createEventEnvelope(type, payload)
    emitHostEventJson(envelope.toString())
  }

  private fun emitHostEventJson(eventJson: String) {
    if (!webReady) {
      if (isQueueablePendingEvent(eventJson)) {
        pendingEvents += eventJson
      } else {
        appendDiagnostic("Dropping non-queueable host event while web not ready.")
      }
      return
    }
    dispatchToWeb(eventJson)
  }

  private fun flushPendingEvents() {
    if (!webReady || pendingEvents.isEmpty()) {
      return
    }
    val queueable = pendingEvents.filter(::isQueueablePendingEvent)
    val dropped = pendingEvents.size - queueable.size
    if (dropped > 0) {
      appendDiagnostic("Dropped $dropped stale non-queueable host event(s) before flush.")
    }
    queueable.forEach(::dispatchToWeb)
    pendingEvents.clear()
  }

  private fun loadShellUrl(
    source: String,
    url: String,
    diagnostic: String,
    toastMessage: String? = null
  ) {
    startupCompleted = false
    webReady = false
    shellSource = source
    showStartupLoading(
      getString(R.string.startup_loading_title),
      getString(R.string.startup_loading_message)
    )
    startStartupWatchdog()
    appendDiagnostic(diagnostic)
    if (!toastMessage.isNullOrBlank()) {
      Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
    }
    webView.loadUrl(url)
    requestWebViewFocusIfTv()
  }

  private fun isCommandAllowedForCurrentShell(commandType: String): Boolean {
    return HostCommandPolicy.isAllowed(commandType, shellSource, usingLocalDebugServer)
  }

  private fun isQueueablePendingEvent(eventJson: String): Boolean {
    val type = runCatching { JSONObject(eventJson).optString("type") }.getOrNull().orEmpty()
    return HostEventQueuePolicy.shouldQueue(type)
  }

  private fun resetUpdatePromptState() {
    promptedDownloadedVersion = null
    awaitingUnknownSourcesPermissionVersion = null
  }

  private fun dispatchToWeb(eventJson: String) {
    val escaped = JSONObject.quote(eventJson)
    val script = """
      (() => {
        const detail = JSON.parse($escaped);
        window.dispatchEvent(new CustomEvent('${HostBridgeContract.HOST_EVENT_NAME}', { detail }));
      })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
  }

  private fun parsePlaybackResultPayload(eventJson: String): JSONObject? {
    val event = runCatching { JSONObject(eventJson) }.getOrNull() ?: return null
    if (event.optString("type") != "playback.result") {
      return null
    }
    return event.optJSONObject("payload")
  }

  private fun onPlaybackResult(payload: JSONObject) {
    val status = payload.optString("status")
    if (status != "failed" || !payload.optBoolean("fallbackTriggered", false)) {
      return
    }

    val streamId = payload.optString("streamId").ifBlank { null }
    val mediaUrl = payload.optString("url").ifBlank { null }
    val fallbackWebUrl = payload.optString("fallbackWebUrl").ifBlank { null }
    val failureDomain = payload.optString("failureDomain").ifBlank { "unknown" }
    val failureDetail = payload.optString("failureDetail").ifBlank { "unspecified" }

    nativeFallbackLoopGuard.mark(mediaUrl, streamId)
    registerLegacyShimSkip(mediaUrl)
    appendDiagnostic(
      "Native fallback triggered streamId=$streamId domain=$failureDomain detail=$failureDetail mediaUrl=${mediaUrl?.take(120)}"
    )

    if (!fallbackWebUrl.isNullOrBlank()) {
      val resolvedFallbackUrl = resolveFallbackWebUrl(fallbackWebUrl)
      runOnUiThread {
        appendDiagnostic("Loading fallback web route: $resolvedFallbackUrl")
        webView.loadUrl(resolvedFallbackUrl)
        requestWebViewFocusIfTv()
      }
      return
    }

    dispatchNativeFallbackEvent(payload)
  }

  private fun dispatchNativeFallbackEvent(payload: JSONObject) {
    val escaped = JSONObject.quote(payload.toString())
    val script = """
      (() => {
        try {
          const detail = JSON.parse($escaped);
          window.dispatchEvent(new CustomEvent('stremio:native-playback-fallback', { detail }));
        } catch (_) {}
      })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
  }

  private fun registerLegacyShimSkip(mediaUrl: String?) {
    if (mediaUrl.isNullOrBlank()) {
      return
    }
    val escapedUrl = JSONObject.quote(mediaUrl)
    val skipUntil = System.currentTimeMillis() + 15_000L
    val script = """
      (() => {
        window.__stremioNativeSkipUrls = window.__stremioNativeSkipUrls || {};
        window.__stremioNativeSkipUrls[$escapedUrl] = $skipUntil;
      })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
  }

  private fun resolveFallbackWebUrl(rawUrl: String): String {
    if (!rawUrl.startsWith("#")) {
      return rawUrl
    }

    val base = webView.url?.substringBefore('#') ?: localShellUrl
    return "$base$rawUrl"
  }

  private fun interceptStreamingServerRequest(uri: Uri): WebResourceResponse? {
    if (!isLocalStreamingServerRequest(uri)) {
      return null
    }

    val target = extractLocalStreamingTarget(uri)
    if (target != null) {
      if (maybeOpenNativePlaybackFromStreamingServer(target.streamId, target.mediaUrl, uri.toString())) {
        // Return a tiny valid manifest response so WebView does not hard-fail while native playback opens.
        return createTextResponse("application/vnd.apple.mpegurl", "#EXTM3U\n")
      }
      return null
    }

    val path = uri.path.orEmpty()
    if (path == "/settings") {
      val body = JSONObject()
        .put("version", "android-host")
        .put("serverVersion", "android-host")
        .put("transcodeSupport", false)
        .put("remoteHttps", false)
      return createTextResponse("application/json", body.toString())
    }

    if (path == "/network-info") {
      val body = JSONObject()
        .put("connected", true)
        .put("transport", "internet")
      return createTextResponse("application/json", body.toString())
    }

    if (path == "/device-info") {
      val body = JSONObject()
        .put("platform", "android")
        .put("isTv", BuildConfig.IS_TV)
      return createTextResponse("application/json", body.toString())
    }

    if (path == "/casting") {
      val body = JSONObject()
        .put("available", false)
        .put("devices", org.json.JSONArray())
      return createTextResponse("application/json", body.toString())
    }

    return createTextResponse("application/json", "{}")
  }

  private data class StreamingTarget(
    val mediaUrl: String,
    val streamId: String?
  )

  private fun isLocalStreamingServerRequest(uri: Uri): Boolean {
    val host = uri.host.orEmpty()
    if (host != "127.0.0.1" && host != "localhost") {
      return false
    }
    val port = uri.port
    return port == -1 || port == LOCAL_STREAMING_PORT
  }

  private fun extractLocalStreamingTarget(uri: Uri): StreamingTarget? {
    if (!isLocalStreamingServerRequest(uri)) {
      return null
    }

    val path = uri.path.orEmpty()
    val mediaUrl = sequenceOf("mediaURL", "mediaUrl", "url")
      .mapNotNull { uri.getQueryParameter(it) }
      .map { it.trim() }
      .firstOrNull { it.isNotBlank() }
      ?: return null

    if (mediaUrl.startsWith("http://127.0.0.1:$LOCAL_STREAMING_PORT") || mediaUrl.startsWith("http://localhost:$LOCAL_STREAMING_PORT")) {
      return null
    }

    return StreamingTarget(
      mediaUrl = mediaUrl,
      streamId = extractStreamIdFromHlsPath(path)
    )
  }

  private fun maybeOpenNativePlaybackFromStreamingServer(streamId: String?, mediaUrl: String, sourceUrl: String): Boolean {
    if (nativeFallbackLoopGuard.shouldSkip(mediaUrl, streamId)) {
      appendDiagnostic(
        "Skipping native intercept due to fallback guard streamId=$streamId mediaUrl=${mediaUrl.take(120)}; dispatching fallback."
      )
      dispatchLoopGuardPlaybackFailure(
        streamId = streamId,
        url = mediaUrl,
        fallbackWebUrl = webView.url?.takeIf { it.isNotBlank() }
      )
      return false
    }

    val now = System.currentTimeMillis()
    if (now - lastStructuredPlaybackAtMs < 1500L) {
      appendDiagnostic("Skipping legacy intercept because structured playback command was just received.")
      return false
    }

    if (lastNativePlaybackUrl == mediaUrl && now - lastNativePlaybackAtMs < NATIVE_PLAYBACK_DEDUP_MS) {
      return true
    }

    lastNativePlaybackUrl = mediaUrl
    lastNativePlaybackAtMs = now

    val payload = JSONObject()
      .put("url", mediaUrl)
      .put("sourceUrl", sourceUrl)
      .put("streamId", streamId ?: mediaUrl.hashCode().toString())
    webView.url?.takeIf { it.isNotBlank() }?.let { payload.put("fallbackWebUrl", it) }

    appendDiagnostic("Intercepted streaming request and opened native playback. source=$sourceUrl")

    if (Looper.myLooper() == Looper.getMainLooper()) {
      val outcome = openPlayback(payload)
      return handlePlaybackOpenOutcome(outcome, source = "streaming_intercept")
    }

    runOnUiThread {
      val outcome = openPlayback(payload)
      handlePlaybackOpenOutcome(outcome, source = "streaming_intercept")
    }
    return true
  }

  private fun extractStreamIdFromHlsPath(path: String): String? {
    // Path format: /hlsv2/{stream-id}/master.m3u8
    val segments = path.split('/').filter { it.isNotBlank() }
    if (segments.size < 2) {
      return null
    }
    if (segments[0] != "hlsv2") {
      return null
    }
    return segments[1]
  }

  private fun createTextResponse(contentType: String, body: String): WebResourceResponse {
    return WebResourceResponse(
      contentType,
      "utf-8",
      200,
      "OK",
      mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Access-Control-Allow-Methods" to "GET, OPTIONS",
        "Access-Control-Allow-Headers" to "*",
        "Cache-Control" to "no-store, no-cache, must-revalidate"
      ),
      ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    )
  }

  private fun installNativePlaybackCommandAdapter() {
    val script = """
      (() => {
        if (window.__stremioNativePlaybackCommandAdapterInstalled) {
          return;
        }
        window.__stremioNativePlaybackCommandAdapterInstalled = true;

        const sendPlaybackOpen = (payload) => {
          if (!payload || typeof payload.url !== 'string' || !payload.url.trim()) {
            return;
          }
          if (!window.stremioHost || typeof window.stremioHost.sendCommand !== 'function') {
            return;
          }

          const streamId = typeof payload.streamId === 'string' && payload.streamId.trim().length > 0
            ? payload.streamId
            : (payload.url + ':' + Date.now());

          const envelope = {
            type: 'playback.open',
            version: 1,
            payload: {
              ...payload,
              streamId,
              fallbackWebUrl: payload.fallbackWebUrl || window.location.href
            },
            timestampMs: Date.now()
          };
          window.stremioHost.sendCommand(JSON.stringify(envelope));
        };

        window.stremioShellNativePlayback = window.stremioShellNativePlayback || {};
        if (typeof window.stremioShellNativePlayback.open !== 'function') {
          window.stremioShellNativePlayback.open = sendPlaybackOpen;
        }

        window.addEventListener('stremio:native-playback-open', (event) => {
          const detail = event && event.detail ? event.detail : null;
          if (!detail) {
            return;
          }
          sendPlaybackOpen(detail);
        });
      })();
    """.trimIndent()

    webView.evaluateJavascript(script, null)
  }

  private fun installPlaybackHandoffShim() {
    val script = """
      (() => {
        if (window.__stremioNativePlaybackShimInstalled) {
          return;
        }
        window.__stremioNativePlaybackShimInstalled = true;

        const LOCAL_HOSTS = new Set(['127.0.0.1', 'localhost']);
        const LOCAL_PORT = '11470';
        const extractTarget = (value) => {
          if (!value) return null;
          try {
            const parsed = new URL(String(value), window.location.href);
            if (!LOCAL_HOSTS.has(parsed.hostname)) return null;
            if (parsed.port && parsed.port !== LOCAL_PORT) return null;
            if (!parsed.pathname.startsWith('/hlsv2/')) return null;
            const mediaUrl = parsed.searchParams.get('mediaURL') || parsed.searchParams.get('mediaUrl') || parsed.searchParams.get('url');
            if (!mediaUrl) return null;
            const segments = parsed.pathname.split('/').filter(Boolean);
            return {
              mediaUrl,
              streamId: segments.length > 1 ? segments[1] : null,
              sourceUrl: parsed.toString()
            };
          } catch (_) {
            return null;
          }
        };

        const sendPlaybackOpen = (target) => {
          if (!window.stremioHost || typeof window.stremioHost.sendCommand !== 'function') {
            return;
          }
          const envelope = {
            type: 'playback.open',
            version: 1,
            payload: {
              url: target.mediaUrl,
              streamId: target.streamId || undefined,
              sourceUrl: target.sourceUrl || undefined,
              fallbackWebUrl: window.location.href
            },
            timestampMs: Date.now()
          };
          window.stremioHost.sendCommand(JSON.stringify(envelope));
        };

        const maybeOpenNative = (value) => {
          const target = extractTarget(value);
          if (!target) return false;
          const skips = window.__stremioNativeSkipUrls || {};
          const skipUntil = skips[target.mediaUrl];
          if (typeof skipUntil === 'number' && Date.now() < skipUntil) {
            return false;
          }
          sendPlaybackOpen(target);
          return true;
        };

        const originalFetch = window.fetch ? window.fetch.bind(window) : null;
        if (originalFetch) {
          window.fetch = (input, init) => {
            const candidate = typeof input === 'string' ? input : (input && input.url ? input.url : '');
            if (maybeOpenNative(candidate)) {
              return Promise.resolve(new Response('#EXTM3U\n', {
                status: 200,
                headers: { 'content-type': 'application/vnd.apple.mpegurl' }
              }));
            }
            return originalFetch(input, init);
          };
        }

        const mediaSrcDescriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
        if (mediaSrcDescriptor && typeof mediaSrcDescriptor.set === 'function' && typeof mediaSrcDescriptor.get === 'function') {
          Object.defineProperty(HTMLMediaElement.prototype, 'src', {
            configurable: true,
            enumerable: mediaSrcDescriptor.enumerable,
            get() {
              return mediaSrcDescriptor.get.call(this);
            },
            set(value) {
              if (!maybeOpenNative(value)) {
                mediaSrcDescriptor.set.call(this, value);
              }
            }
          });
        }

        const originalSetAttribute = Element.prototype.setAttribute;
        Element.prototype.setAttribute = function(name, value) {
          if (this instanceof HTMLMediaElement && String(name).toLowerCase() === 'src') {
            if (maybeOpenNative(value)) {
              return;
            }
          }
          return originalSetAttribute.call(this, name, value);
        };
      })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
  }

  private fun requestAudioFocus() {
    val result = audioManager.requestAudioFocus(audioFocusRequest)
    appendDiagnostic("Audio focus request result=$result.")
  }

  private fun abandonAudioFocus() {
    audioManager.abandonAudioFocusRequest(audioFocusRequest)
  }

  private fun appendAudioStateDiagnostic(source: String) {
    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val muted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
    appendDiagnostic("Audio state $source stream=music volume=$current/$max muted=$muted.")
  }

  private fun applyWebMediaAudioGuard() {
    val script = """
      (() => {
        const apply = (media) => {
          if (!(media instanceof HTMLMediaElement)) return;
          media.defaultMuted = false;
          media.muted = false;
          if (typeof media.volume === 'number' && media.volume === 0) {
            media.volume = 1;
          }
        };

        const syncAll = () => {
          document.querySelectorAll('video, audio').forEach(apply);
        };

        syncAll();

        if (!window.__stremioAudioGuardInstalled) {
          window.__stremioAudioGuardInstalled = true;
          const observer = new MutationObserver(syncAll);
          observer.observe(document.documentElement || document.body, { childList: true, subtree: true });
          setInterval(syncAll, 1500);
          ['click', 'keydown', 'touchend'].forEach((evt) => {
            window.addEventListener(evt, syncAll, { passive: true });
          });
        }
      })();
    """.trimIndent()

    webView.evaluateJavascript(script, null)
  }
}
