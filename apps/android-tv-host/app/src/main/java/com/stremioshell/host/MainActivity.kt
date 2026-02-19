package com.stremioshell.host

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
  private lateinit var webView: WebView
  private lateinit var networkMonitor: NetworkMonitor

  private val bridgeName = "stremioHost"
  private val localShellUrl = "file:///android_asset/web/index.html"

  private var webReady = false
  private var attemptedDebugFallback = false
  private val pendingEvents = mutableListOf<String>()
  private val diagnostics = ArrayDeque<String>()

  private val playbackEventReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val eventJson = intent.getStringExtra(PlaybackBridge.EXTRA_EVENT_JSON) ?: return
      emitHostEventJson(eventJson)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    webView = findViewById(R.id.webView)
    configureWebView()

    networkMonitor = NetworkMonitor(this) { connected, transport ->
      val payload = JSONObject()
        .put("connected", connected)
        .put("transport", transport)
      emitHostEvent("network.changed", payload)
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
    emitLifecycle("resumed")
  }

  override fun onPause() {
    emitLifecycle("paused")
    super.onPause()
  }

  override fun onStop() {
    emitLifecycle("stopped")
    networkMonitor.stop()
    super.onStop()
  }

  override fun onDestroy() {
    emitLifecycle("destroyed")
    unregisterReceiver(playbackEventReceiver)
    webView.removeJavascriptInterface(bridgeName)
    webView.destroy()
    super.onDestroy()
  }

  private fun configureWebView() {
    with(webView.settings) {
      javaScriptEnabled = true
      domStorageEnabled = true
      mediaPlaybackRequiresUserGesture = false
      allowFileAccess = true
    }

    webView.addJavascriptInterface(WebCommandBridge(::handleCommandFromWeb), bridgeName)
    webView.webViewClient = object : WebViewClient() {
      override fun onPageFinished(view: WebView?, url: String?) {
        webReady = true
        flushPendingEvents()
      }

      override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: android.webkit.WebResourceError?
      ) {
        val isMainFrame = request?.isForMainFrame == true
        if (!isMainFrame) {
          return
        }

        if (BuildConfig.DEBUG && !attemptedDebugFallback) {
          attemptedDebugFallback = true
          appendDiagnostic("Debug URL failed, falling back to bundled web assets.")
          Toast.makeText(this@MainActivity, "Dev server unavailable, using bundled shell.", Toast.LENGTH_SHORT).show()
          loadLocalShell()
        }
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
    if (BuildConfig.DEBUG && BuildConfig.WEB_APP_URL.isNotBlank()) {
      appendDiagnostic("Loading debug web shell from ${BuildConfig.WEB_APP_URL}")
      webView.loadUrl(BuildConfig.WEB_APP_URL)
      return
    }
    loadLocalShell()
  }

  private fun loadLocalShell() {
    appendDiagnostic("Loading bundled web shell from assets.")
    webView.loadUrl(localShellUrl)
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
}
