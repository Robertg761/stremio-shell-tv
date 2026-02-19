package com.stremioshell.host

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
import android.view.View
import android.webkit.ConsoleMessage
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
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
  companion object {
    private const val TAG = "StremioHost"
    private const val STARTUP_TIMEOUT_MS = 12000L
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
  private val pendingEvents = mutableListOf<String>()
  private val diagnostics = ArrayDeque<String>()
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
      emitHostEventJson(eventJson)
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

    networkMonitor = NetworkMonitor(this) { connected, transport ->
      runOnUiThread {
        val payload = JSONObject()
          .put("connected", connected)
          .put("transport", transport)
        emitHostEvent("network.changed", payload)
      }
    }

    registerPlaybackEvents()
    setupBackHandling()
    loadShell()
    handleDeepLink(intent)

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
    abandonAudioFocus()
    emitLifecycle("destroyed")
    unregisterReceiver(playbackEventReceiver)
    webView.removeJavascriptInterface(bridgeName)
    webView.destroy()
    super.onDestroy()
  }

  private fun configureWebView() {
    assetLoader = WebViewAssetLoader.Builder()
      .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
      .build()

    with(webView.settings) {
      javaScriptEnabled = true
      domStorageEnabled = true
      mediaPlaybackRequiresUserGesture = false
      allowFileAccess = true
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
      override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        return assetLoader.shouldInterceptRequest(request.url)
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        appendDiagnostic("onPageFinished source=$shellSource url=$url")
        webReady = true
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

  private fun setupBackHandling() {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        emitHostEvent("back.pressed", JSONObject().put("source", "hardware"))
        if (webView.canGoBack()) {
          webView.goBack()
        } else {
          finish()
        }
      }
    })
  }

  private fun loadShell() {
    startupCompleted = false
    webReady = false
    showStartupLoading(
      getString(R.string.startup_loading_title),
      getString(R.string.startup_loading_message)
    )
    startStartupWatchdog()

    if (BuildConfig.DEBUG && BuildConfig.WEB_APP_URL.isNotBlank()) {
      usingLocalDebugServer = isLocalDebugUrl(BuildConfig.WEB_APP_URL)
      appendDiagnostic("Loading debug web shell from ${BuildConfig.WEB_APP_URL}")
      shellSource = if (usingLocalDebugServer) "debug" else "remote"
      webView.loadUrl(BuildConfig.WEB_APP_URL)
      return
    }

    usingLocalDebugServer = false
    loadLocalShell()
  }

  private fun loadLocalShell() {
    appendDiagnostic("Loading bundled web shell from assets.")
    shellSource = "bundled"
    webReady = false
    webView.loadUrl(localShellUrl)
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
    shellSource = "remote"
    webReady = false
    appendDiagnostic("Loading remote fallback shell due to $reason -> $fallbackUrl")
    Toast.makeText(this, "Local shell unavailable, loading fallback.", Toast.LENGTH_SHORT).show()
    webView.loadUrl(fallbackUrl)
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
          appendDiagnostic("Received host command: ${envelope.type}")
          when (envelope.type) {
            "playback.open" -> openPlayback(envelope.payload)
            "playback.close" -> PlaybackBridge.requestPlaybackClose(this)
            "external.openUrl" -> openExternalUrl(envelope.payload)
            "diagnostics.export" -> exportDiagnostics()
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
          emitHostEvent("playback.result", payload)
        }
      )
    }
  }

  private fun openPlayback(payload: JSONObject) {
    val url = payload.optString("url")
    val streamId = payload.optString("streamId")
    val title = payload.optString("title")
    val subtitle = payload.optString("subtitle")
    val positionMs = payload.optLong("positionMs", 0L)

    if (url.isBlank()) {
      val errorPayload = HostBridgeContract.createPlaybackPayload(
        status = "failed",
        streamId = streamId.ifBlank { null },
        errorCode = "missing_url",
        message = "playback.open command requires payload.url"
      )
      emitHostEvent("playback.result", errorPayload)
      return
    }

    val playerIntent = Intent(this, PlayerActivity::class.java).apply {
      putExtra(PlaybackBridge.EXTRA_URL, url)
      putExtra(PlaybackBridge.EXTRA_STREAM_ID, streamId)
      putExtra(PlaybackBridge.EXTRA_TITLE, title)
      putExtra(PlaybackBridge.EXTRA_SUBTITLE, subtitle)
      putExtra(PlaybackBridge.EXTRA_POSITION_MS, positionMs)
    }
    startActivity(playerIntent)
  }

  private fun openExternalUrl(payload: JSONObject) {
    val url = payload.optString("url")
    if (url.isBlank()) {
      appendDiagnostic("external.openUrl ignored due to blank payload.url")
      return
    }

    runCatching {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
          const root = document.getElementById('root');
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
    appendDiagnostic("Startup completed with source=$shellSource.")
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
      pendingEvents += eventJson
      return
    }
    dispatchToWeb(eventJson)
  }

  private fun flushPendingEvents() {
    if (!webReady || pendingEvents.isEmpty()) {
      return
    }
    pendingEvents.forEach(::dispatchToWeb)
    pendingEvents.clear()
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
