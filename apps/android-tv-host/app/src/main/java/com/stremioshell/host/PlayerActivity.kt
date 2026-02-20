package com.stremioshell.host

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import java.net.URL
import java.util.concurrent.Executors

class PlayerActivity : AppCompatActivity() {
  private var player: ExoPlayer? = null
  private var trackSelector: DefaultTrackSelector? = null
  private lateinit var playerView: PlayerView
  private lateinit var artworkView: ImageView
  private lateinit var logoView: ImageView
  private lateinit var introOverlay: View
  private lateinit var titleView: TextView
  private lateinit var subtitleView: TextView
  private lateinit var titleFlashView: View

  private var request: NativePlaybackRequest? = null
  private var hasStarted = false
  private var hasFailed = false
  private var hasCompleted = false
  private var fallbackTriggered = false
  private var introAnimationCompleted = false
  private val uiHandler = Handler(Looper.getMainLooper())
  private val imageLoader = Executors.newSingleThreadExecutor()
  private val appliedSettings = mutableMapOf<String, Boolean>()

  private val closeReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      finish()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestedOrientation = if (BuildConfig.IS_TV) {
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
    setContentView(R.layout.activity_player)
    volumeControlStream = AudioManager.STREAM_MUSIC

    playerView = findViewById(R.id.playerView)
    artworkView = findViewById(R.id.artworkView)
    logoView = findViewById(R.id.logoView)
    introOverlay = findViewById(R.id.introOverlay)
    titleView = findViewById(R.id.titleView)
    subtitleView = findViewById(R.id.subtitleView)
    titleFlashView = findViewById(R.id.titleFlashView)

    ContextCompat.registerReceiver(
      this,
      closeReceiver,
      IntentFilter(PlaybackBridge.ACTION_PLAYBACK_CLOSE),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )

    val parsedRequest = NativePlaybackContracts.fromIntent(intent)
    if (parsedRequest == null) {
      val payload = HostBridgeContract.createPlaybackPayload(
        status = "failed",
        streamId = intent.getStringExtra(PlaybackBridge.EXTRA_STREAM_ID),
        errorCode = "missing_url",
        message = "No media URL provided to player.",
        failureDomain = "host",
        failureDetail = "invalid_intent_payload",
        fallbackTriggered = false
      )
      PlaybackBridge.sendPlaybackEvent(this, payload)
      finish()
      return
    }

    request = parsedRequest
    bindIntroMetadata(parsedRequest)
    initializePlayer(parsedRequest)
  }

  override fun onPause() {
    request?.settings?.pauseOnMinimize?.takeIf { it }?.let {
      player?.playWhenReady = false
    }
    super.onPause()
  }

  override fun onStop() {
    super.onStop()
    if (hasStarted && !hasCompleted && !hasFailed) {
      PlaybackBridge.sendPlaybackEvent(this, HostBridgeContract.createPlaybackPayload(
        status = "paused",
        streamId = request?.streamId,
        url = request?.url,
        fallbackWebUrl = request?.fallbackWebUrl,
        resumePositionMs = player?.currentPosition ?: request?.positionMs
      ))
    }
  }

  override fun onDestroy() {
    uiHandler.removeCallbacksAndMessages(null)
    imageLoader.shutdownNow()
    runCatching {
      unregisterReceiver(closeReceiver)
    }

    playerView.player = null
    trackSelector = null
    player?.release()
    player = null
    super.onDestroy()
  }

  private fun initializePlayer(playbackRequest: NativePlaybackRequest) {
    val selector = DefaultTrackSelector(this)
    trackSelector = selector
    applyTrackSelectorSettings(selector, playbackRequest)

    val exoPlayer = ExoPlayer.Builder(this)
      .setTrackSelector(selector)
      .build()
    player = exoPlayer
    playerView.player = exoPlayer
    applySubtitlesSettings(playbackRequest)

    exoPlayer.setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build(),
      true
    )
    exoPlayer.volume = 1f
    playbackRequest.settings.playbackSpeed?.takeIf { it > 0f }?.let { speed ->
      exoPlayer.playbackParameters = PlaybackParameters(speed)
      appliedSettings["playbackSpeed"] = true
    }

    exoPlayer.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
          maybeRunIntroAnimation()
          val diagnostics = NativePlaybackContracts.settingsDiagnostics(playbackRequest.settings, appliedSettings)
          if (!hasStarted) {
            hasStarted = true
            PlaybackBridge.sendPlaybackEvent(
              this@PlayerActivity,
              HostBridgeContract.createPlaybackPayload(
                status = "started",
                streamId = playbackRequest.streamId,
                url = playbackRequest.url,
                fallbackWebUrl = playbackRequest.fallbackWebUrl,
                resumePositionMs = player?.currentPosition ?: playbackRequest.positionMs,
                settingsDiagnostics = diagnostics
              )
            )
          } else {
            PlaybackBridge.sendPlaybackEvent(
              this@PlayerActivity,
              HostBridgeContract.createPlaybackPayload(
                status = "resumed",
                streamId = playbackRequest.streamId,
                url = playbackRequest.url,
                fallbackWebUrl = playbackRequest.fallbackWebUrl,
                resumePositionMs = player?.currentPosition ?: playbackRequest.positionMs
              )
            )
          }
        } else if (hasStarted && exoPlayer.playbackState == Player.STATE_READY) {
          PlaybackBridge.sendPlaybackEvent(
            this@PlayerActivity,
            HostBridgeContract.createPlaybackPayload(
              status = "paused",
              streamId = playbackRequest.streamId,
              url = playbackRequest.url,
              fallbackWebUrl = playbackRequest.fallbackWebUrl,
              resumePositionMs = player?.currentPosition ?: playbackRequest.positionMs
            )
          )
        }
      }

      override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_READY) {
          maybeRunIntroAnimation()
          if (!hasPlayableAudioTrack(exoPlayer.currentTracks)) {
            triggerNativeAudioFallback(
              detail = "ready_without_audio_track",
              message = "Playback reached ready state without a selected audio track."
            )
            return
          }
        }

        if (state == Player.STATE_ENDED) {
          hasCompleted = true
          PlaybackBridge.sendPlaybackEvent(
            this@PlayerActivity,
            HostBridgeContract.createPlaybackPayload(
              status = "completed",
              streamId = playbackRequest.streamId,
              url = playbackRequest.url,
              fallbackWebUrl = playbackRequest.fallbackWebUrl,
              resumePositionMs = player?.currentPosition ?: playbackRequest.positionMs
            )
          )
          finish()
        }
      }

      override fun onPlayerError(error: PlaybackException) {
        hasFailed = true
        if (isLikelyAudioFailure(error)) {
          triggerNativeAudioFallback(
            detail = "audio_renderer_error",
            message = error.message ?: "Native player reported an audio renderer failure."
          )
          return
        }

        val diagnostics = NativePlaybackContracts.settingsDiagnostics(playbackRequest.settings, appliedSettings)
        PlaybackBridge.sendPlaybackEvent(
          this@PlayerActivity,
          HostBridgeContract.createPlaybackPayload(
            status = "failed",
            streamId = playbackRequest.streamId,
            errorCode = error.errorCodeName,
            message = error.message,
            url = playbackRequest.url,
            fallbackWebUrl = playbackRequest.fallbackWebUrl,
            resumePositionMs = player?.currentPosition ?: playbackRequest.positionMs,
            fallbackTriggered = false,
            failureDomain = "decode",
            failureDetail = error.errorCodeName,
            settingsDiagnostics = diagnostics
          )
        )
        finish()
      }

      override fun onRenderedFirstFrame() {
        maybeRunIntroAnimation()
      }
    })

    exoPlayer.setMediaItem(MediaItem.fromUri(playbackRequest.url))
    exoPlayer.prepare()
    val startPosition = playbackRequest.resumePositionMs ?: playbackRequest.positionMs
    if (startPosition > 0L) {
      exoPlayer.seekTo(startPosition)
    }
    exoPlayer.playWhenReady = true
  }

  private fun bindIntroMetadata(playbackRequest: NativePlaybackRequest) {
    titleView.text = playbackRequest.title ?: ""
    subtitleView.text = playbackRequest.subtitle ?: ""
    subtitleView.visibility = if (playbackRequest.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE

    titleView.alpha = 0f
    subtitleView.alpha = 0f
    introOverlay.alpha = 1f

    loadRemoteImage(playbackRequest.artworkUrl, artworkView, showOnLoad = true)
    loadRemoteImage(playbackRequest.logoUrl, logoView, showOnLoad = true)
  }

  private fun maybeRunIntroAnimation() {
    if (introAnimationCompleted) {
      return
    }
    introAnimationCompleted = true

    titleFlashView.alpha = 0f
    titleFlashView.animate()
      .alpha(0.25f)
      .setDuration(130L)
      .withEndAction {
        titleFlashView.animate().alpha(0f).setDuration(260L).start()
      }
      .start()

    titleView.animate().alpha(1f).setDuration(240L).start()
    subtitleView.animate().alpha(1f).setDuration(300L).start()

    uiHandler.postDelayed({
      introOverlay.animate()
        .alpha(0f)
        .setDuration(420L)
        .withEndAction {
          introOverlay.visibility = View.GONE
        }
        .start()
      logoView.animate().alpha(0f).setDuration(350L).withEndAction {
        logoView.visibility = View.GONE
      }.start()
      artworkView.animate().alpha(0f).setDuration(500L).withEndAction {
        artworkView.visibility = View.GONE
      }.start()
    }, 1200L)
  }

  private fun applyTrackSelectorSettings(selector: DefaultTrackSelector, playbackRequest: NativePlaybackRequest) {
    val settings = playbackRequest.settings
    val tracks = playbackRequest.tracks
    val parametersBuilder = selector.buildUponParameters()

    val preferredAudioLanguage = trackLanguage(tracks.audioTracks, tracks.selectedAudioTrackId)
      ?: settings.audioLanguage
    if (!preferredAudioLanguage.isNullOrBlank()) {
      parametersBuilder.setPreferredAudioLanguage(preferredAudioLanguage)
      appliedSettings["audioLanguage"] = true
    }

    val preferredSubtitleLanguage = trackLanguage(tracks.subtitlesTracks, tracks.selectedSubtitlesTrackId)
      ?: settings.subtitlesLanguage
    if (!preferredSubtitleLanguage.isNullOrBlank()) {
      parametersBuilder.setPreferredTextLanguage(preferredSubtitleLanguage)
      appliedSettings["subtitlesLanguage"] = true
    }

    settings.surroundSound?.let { surroundSound ->
      parametersBuilder.setMaxAudioChannelCount(if (surroundSound) 8 else 2)
      appliedSettings["surroundSound"] = true
    }

    settings.hardwareDecoding?.let {
      // ExoPlayer hardware decode preference is renderer/device dependent in this shell.
      appliedSettings["hardwareDecoding"] = false
    }

    settings.videoMode?.let {
      // Video output mode selection is not currently exposed by Media3 in this host.
      appliedSettings["videoMode"] = false
    }

    settings.assSubtitlesStyling?.let {
      // ASS parser style override parity is not yet exposed in this native path.
      appliedSettings["assSubtitlesStyling"] = false
    }

    settings.nextVideoNotificationDuration?.let {
      appliedSettings["nextVideoNotificationDuration"] = false
    }

    settings.bingeWatching?.let {
      appliedSettings["bingeWatching"] = false
    }

    settings.pauseOnMinimize?.let {
      appliedSettings["pauseOnMinimize"] = true
    }

    selector.setParameters(parametersBuilder)
  }

  private fun applySubtitlesSettings(playbackRequest: NativePlaybackRequest) {
    val settings = playbackRequest.settings
    val subtitleRenderer = playerView.subtitleView ?: return

    val textColor = parseColorOrDefault(settings.subtitlesTextColor, Color.WHITE).also {
      if (settings.subtitlesTextColor != null) {
        appliedSettings["subtitlesTextColor"] = true
      }
    }
    val backgroundColor = parseColorOrDefault(settings.subtitlesBackgroundColor, Color.TRANSPARENT).also {
      if (settings.subtitlesBackgroundColor != null) {
        appliedSettings["subtitlesBackgroundColor"] = true
      }
    }
    val outlineColor = parseColorOrDefault(settings.subtitlesOutlineColor, Color.BLACK).also {
      if (settings.subtitlesOutlineColor != null) {
        appliedSettings["subtitlesOutlineColor"] = true
      }
    }

    subtitleRenderer.setStyle(
      CaptionStyleCompat(
        textColor,
        backgroundColor,
        Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        outlineColor,
        null
      )
    )

    settings.subtitlesSize?.let { sizePercent ->
      val normalized = (sizePercent.coerceIn(50, 250) / 100f) * 0.053f
      subtitleRenderer.setFractionalTextSize(normalized)
      appliedSettings["subtitlesSize"] = true
    }

    settings.subtitlesOffset?.let { offsetPercent ->
      val normalized = (100 - offsetPercent.coerceIn(0, 100)) / 100f
      subtitleRenderer.setBottomPaddingFraction(normalized * 0.15f)
      appliedSettings["subtitlesOffset"] = true
    }

    settings.subtitlesDelay?.let {
      // Subtitle timing offset is not currently exposed via PlayerView subtitle renderer controls.
      appliedSettings["subtitlesDelay"] = false
    }
  }

  private fun trackLanguage(tracks: List<NativePlaybackTrack>, selectedTrackId: String?): String? {
    if (selectedTrackId.isNullOrBlank()) {
      return null
    }
    return tracks.firstOrNull { it.id == selectedTrackId }?.lang
  }

  private fun hasPlayableAudioTrack(currentTracks: Tracks): Boolean {
    for (group in currentTracks.groups) {
      if (group.type != C.TRACK_TYPE_AUDIO) {
        continue
      }

      for (trackIndex in 0 until group.length) {
        if (group.isTrackSelected(trackIndex)) {
          return true
        }
      }
    }
    return false
  }

  private fun triggerNativeAudioFallback(detail: String, message: String) {
    if (fallbackTriggered) {
      return
    }

    fallbackTriggered = true
    hasFailed = true
    val activeRequest = request ?: return
    val diagnostics = NativePlaybackContracts.settingsDiagnostics(activeRequest.settings, appliedSettings)

    PlaybackBridge.sendPlaybackEvent(
      this,
      HostBridgeContract.createPlaybackPayload(
        status = "failed",
        streamId = activeRequest.streamId,
        errorCode = "native_audio_unavailable",
        message = message,
        url = activeRequest.url,
        fallbackWebUrl = activeRequest.fallbackWebUrl,
        resumePositionMs = player?.currentPosition ?: activeRequest.positionMs,
        fallbackTriggered = true,
        failureDomain = "native_audio",
        failureDetail = detail,
        settingsDiagnostics = diagnostics
      )
    )

    finish()
  }

  private fun isLikelyAudioFailure(error: PlaybackException): Boolean {
    val errorName = error.errorCodeName.lowercase()
    val message = error.message?.lowercase().orEmpty()
    return errorName.contains("audio") || message.contains("audio")
  }

  private fun loadRemoteImage(url: String?, target: ImageView, showOnLoad: Boolean) {
    if (url.isNullOrBlank()) {
      return
    }

    imageLoader.execute {
      val bitmap = runCatching {
        URL(url).openStream().use { input ->
          BitmapFactory.decodeStream(input)
        }
      }.getOrNull()

      if (bitmap != null) {
        runOnUiThread {
          target.setImageBitmap(bitmap)
          if (showOnLoad) {
            target.visibility = View.VISIBLE
          }
        }
      }
    }
  }

  private fun parseColorOrDefault(value: String?, fallback: Int): Int {
    if (value.isNullOrBlank()) {
      return fallback
    }
    return runCatching { Color.parseColor(value) }.getOrDefault(fallback)
  }
}
