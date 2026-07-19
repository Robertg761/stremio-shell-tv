package com.stremioshell.host.tv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stremioshell.host.tv.data.WatchEntry
import com.stremioshell.host.tv.data.WatchStateStore
import com.stremioshell.host.tv.data.addon.AddonStream
import com.stremioshell.host.tv.ui.Screen
import com.stremioshell.host.tv.ui.theme.StremioTvTheme
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.launch

class MpvPlayerActivity : ComponentActivity() {
  private var mpvCreated = false
  private lateinit var watchStore: WatchStateStore
  private val mainHandler = Handler(Looper.getMainLooper())

  private var url = ""
  private var title = ""
  private var watchKey = ""
  private var tmdbId = 0
  private var mediaType = "movie"
  private var posterUrl: String? = null
  private var season: Int? = null
  private var episode: Int? = null
  private var resumeMs = 0L

  // Observed playback state, updated from mpv events on the main thread.
  private val paused = mutableStateOf(false)
  private val buffering = mutableStateOf(true)
  private val timePosSec = mutableDoubleStateOf(0.0)
  private val durationSec = mutableDoubleStateOf(0.0)
  private val osdVisible = mutableStateOf(true)
  private var osdHideAtMs = 0L

  private val observer = object : MPVLib.EventObserver {
    override fun eventProperty(property: String) {}
    override fun eventProperty(property: String, value: Long) {}
    override fun eventProperty(property: String, value: Double) {
      mainHandler.post {
        when (property) {
          "time-pos" -> timePosSec.doubleValue = value
          "duration" -> durationSec.doubleValue = value
        }
      }
    }

    override fun eventProperty(property: String, value: Boolean) {
      mainHandler.post {
        when (property) {
          "pause" -> paused.value = value
          "paused-for-cache" -> buffering.value = value
          "eof-reached" -> if (value) finishPlayback(markFinished = true)
        }
      }
    }

    override fun eventProperty(property: String, value: String) {}

    override fun event(eventId: Int) {
      if (eventId == MPVLib.MPV_EVENT_FILE_LOADED) {
        mainHandler.post {
          buffering.value = false
          if (resumeMs > 3_000) {
            MPVLib.setPropertyDouble("time-pos", resumeMs / 1000.0)
          }
        }
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    watchStore = WatchStateStore(applicationContext)

    url = intent.getStringExtra(EXTRA_URL).orEmpty()
    title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
    watchKey = intent.getStringExtra(EXTRA_WATCH_KEY).orEmpty()
    tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, 0)
    mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "movie"
    posterUrl = intent.getStringExtra(EXTRA_POSTER)
    season = intent.getIntExtra(EXTRA_SEASON, -1).takeIf { it >= 0 }
    episode = intent.getIntExtra(EXTRA_EPISODE, -1).takeIf { it >= 0 }
    resumeMs = intent.getLongExtra(EXTRA_RESUME_MS, 0L)

    if (url.isBlank()) {
      finish()
      return
    }

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    MPVLib.create(this)
    mpvCreated = true
    MPVLib.setOptionString("vo", "gpu")
    MPVLib.setOptionString("gpu-context", "android")
    MPVLib.setOptionString("opengl-es", "yes")
    MPVLib.setOptionString("hwdec", "mediacodec")
    MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
    MPVLib.setOptionString("ao", "audiotrack")
    MPVLib.setOptionString("cache", "yes")
    MPVLib.setOptionString("demuxer-max-bytes", "67108864")
    MPVLib.setOptionString("demuxer-max-back-bytes", "33554432")
    MPVLib.setOptionString("sub-font-size", "44")
    MPVLib.setOptionString("keep-open", "yes")
    MPVLib.setOptionString("force-window", "no")
    MPVLib.init()
    MPVLib.addObserver(observer)
    MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_DOUBLE)
    MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_DOUBLE)
    MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)
    MPVLib.observeProperty("paused-for-cache", MPVLib.MPV_FORMAT_FLAG)
    MPVLib.observeProperty("eof-reached", MPVLib.MPV_FORMAT_FLAG)

    setContent {
      StremioTvTheme {
        PlayerSurface()
      }
    }
    showOsd()
  }

  @Composable
  private fun PlayerSurface() {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
      AndroidView(factory = { context -> createSurfaceView(context) }, modifier = Modifier.fillMaxSize())

      val isPaused by paused
      val isBuffering by buffering
      val position by timePosSec
      val duration by durationSec
      val showOsd by osdVisible

      if (isBuffering) {
        Text(
          "Buffering...",
          style = MaterialTheme.typography.titleMedium,
          color = Color.White,
          modifier = Modifier.align(Alignment.Center),
        )
      }

      if (showOsd || isPaused) {
        Column(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color(0xB3000000))
            .padding(horizontal = 40.dp, vertical = 20.dp),
        ) {
          val suffix = if (season != null) "  S${season}E${episode}" else ""
          Text("$title$suffix", style = MaterialTheme.typography.titleLarge, color = Color.White)
          Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(formatTime(position), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Box(
              modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
                .height(5.dp)
                .background(Color(0x66FFFFFF)),
            ) {
              val fraction = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
              Box(
                modifier = Modifier
                  .fillMaxWidth(fraction)
                  .height(5.dp)
                  .background(MaterialTheme.colorScheme.primary),
              )
            }
            Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.bodyMedium)
          }
          if (isPaused) {
            Text("Paused", color = Color.White, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }

  private fun createSurfaceView(context: Context): SurfaceView {
    val view = SurfaceView(context)
    view.holder.addCallback(object : SurfaceHolder.Callback {
      override fun surfaceCreated(holder: SurfaceHolder) {
        if (!mpvCreated) return
        MPVLib.attachSurface(holder.surface)
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.command(arrayOf("loadfile", url))
      }

      override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (mpvCreated) MPVLib.setPropertyString("android-surface-size", "${width}x${height}")
      }

      override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (!mpvCreated) return
        MPVLib.setPropertyString("vo", "null")
        MPVLib.detachSurface()
      }
    })
    return view
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    if (!mpvCreated) return super.onKeyDown(keyCode, event)
    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_CENTER,
      KeyEvent.KEYCODE_ENTER,
      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
        MPVLib.command(arrayOf("cycle", "pause"))
        showOsd()
        return true
      }
      KeyEvent.KEYCODE_MEDIA_PLAY -> {
        MPVLib.setPropertyBoolean("pause", false); showOsd(); return true
      }
      KeyEvent.KEYCODE_MEDIA_PAUSE -> {
        MPVLib.setPropertyBoolean("pause", true); showOsd(); return true
      }
      KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
        MPVLib.command(arrayOf("seek", "-10")); showOsd(); return true
      }
      KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
        MPVLib.command(arrayOf("seek", "10")); showOsd(); return true
      }
      KeyEvent.KEYCODE_DPAD_UP -> {
        MPVLib.command(arrayOf("seek", "60")); showOsd(); return true
      }
      KeyEvent.KEYCODE_DPAD_DOWN -> {
        MPVLib.command(arrayOf("seek", "-60")); showOsd(); return true
      }
      KeyEvent.KEYCODE_BACK -> {
        finishPlayback(markFinished = false)
        return true
      }
    }
    return super.onKeyDown(keyCode, event)
  }

  private fun showOsd() {
    osdVisible.value = true
    val hideAt = System.currentTimeMillis() + OSD_TIMEOUT_MS
    osdHideAtMs = hideAt
    mainHandler.postDelayed({
      if (osdHideAtMs == hideAt && !paused.value) {
        osdVisible.value = false
      }
    }, OSD_TIMEOUT_MS)
  }

  private fun finishPlayback(markFinished: Boolean) {
    saveWatchState(markFinished)
    finish()
  }

  private fun saveWatchState(markFinished: Boolean) {
    if (watchKey.isBlank() || tmdbId == 0) return
    val positionMs = (timePosSec.doubleValue * 1000).toLong()
    val durationMs = (durationSec.doubleValue * 1000).toLong()
    val nearEnd = durationMs > 0 && positionMs.toFloat() / durationMs > 0.95f
    lifecycleScope.launch {
      if (markFinished || nearEnd) {
        watchStore.remove(watchKey)
      } else if (positionMs > 10_000) {
        watchStore.upsert(
          WatchEntry(
            key = watchKey,
            tmdbId = tmdbId,
            mediaType = mediaType,
            title = title,
            posterUrl = posterUrl,
            season = season,
            episode = episode,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtMs = System.currentTimeMillis(),
          )
        )
      }
    }
  }

  override fun onPause() {
    saveWatchState(markFinished = false)
    if (mpvCreated) MPVLib.setPropertyBoolean("pause", true)
    super.onPause()
  }

  override fun onDestroy() {
    if (mpvCreated) {
      MPVLib.removeObserver(observer)
      MPVLib.destroy()
      mpvCreated = false
    }
    super.onDestroy()
  }

  private fun formatTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
  }

  companion object {
    private const val OSD_TIMEOUT_MS = 4_000L
    private const val EXTRA_URL = "url"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_WATCH_KEY = "watchKey"
    private const val EXTRA_TMDB_ID = "tmdbId"
    private const val EXTRA_MEDIA_TYPE = "mediaType"
    private const val EXTRA_POSTER = "posterUrl"
    private const val EXTRA_SEASON = "season"
    private const val EXTRA_EPISODE = "episode"
    private const val EXTRA_RESUME_MS = "resumeMs"

    fun watchKeyFor(screen: Screen.Streams): String {
      return if (screen.season != null) {
        "episode:${screen.tmdbId}:${screen.season}:${screen.episode}"
      } else {
        "movie:${screen.tmdbId}"
      }
    }

    fun createIntent(
      context: Context,
      screen: Screen.Streams,
      stream: AddonStream,
      resumeMs: Long = 0L,
    ): Intent {
      val watchKey = watchKeyFor(screen)
      return Intent(context, MpvPlayerActivity::class.java).apply {
        putExtra(EXTRA_URL, stream.url)
        putExtra(EXTRA_TITLE, screen.title)
        putExtra(EXTRA_WATCH_KEY, watchKey)
        putExtra(EXTRA_TMDB_ID, screen.tmdbId)
        putExtra(EXTRA_MEDIA_TYPE, if (screen.mediaType.name == "Show") "show" else "movie")
        putExtra(EXTRA_POSTER, screen.posterUrl)
        screen.season?.let { putExtra(EXTRA_SEASON, it) }
        screen.episode?.let { putExtra(EXTRA_EPISODE, it) }
        putExtra(EXTRA_RESUME_MS, resumeMs)
      }
    }
  }
}
