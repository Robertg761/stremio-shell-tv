package com.stremioshell.host

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {
  companion object {
    private const val PLAYBACK_START_TIMEOUT_MS = 20_000L
    private const val SEEK_INCREMENT_MS = 10_000L
    private const val TAG = "StremioPlayer"
  }

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
  private var currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
  private val uiHandler = Handler(Looper.getMainLooper())
  private val imageLoader = Executors.newSingleThreadExecutor()
  private val appliedSettings = mutableMapOf<String, Boolean>()
  private val playbackStartTimeoutRunnable = Runnable {
    if (hasStarted || hasFailed || hasCompleted || fallbackTriggered) {
      return@Runnable
    }

    triggerPlaybackFallback(
      errorCode = "native_playback_timeout",
      failureDomain = "network",
      detail = "startup_timeout",
      message = "Native player timed out while starting playback."
    )
  }

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
    applyImmersiveMode()
    volumeControlStream = AudioManager.STREAM_MUSIC

    playerView = findViewById(R.id.playerView)
    artworkView = findViewById(R.id.artworkView)
    logoView = findViewById(R.id.logoView)
    introOverlay = findViewById(R.id.introOverlay)
    titleView = findViewById(R.id.titleView)
    subtitleView = findViewById(R.id.subtitleView)
    titleFlashView = findViewById(R.id.titleFlashView)
    configurePlayerControls()

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
    Log.d(TAG, "onCreate streamId=${parsedRequest.streamId} url=${parsedRequest.url.take(140)} fallback=${parsedRequest.fallbackWebUrl}")
    bindIntroMetadata(parsedRequest)
    initializePlayer(parsedRequest)
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      applyImmersiveMode()
    }
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

  override fun onResume() {
    super.onResume()
    applyImmersiveMode()
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
    Log.d(TAG, "initializePlayer streamId=${playbackRequest.streamId} url=${playbackRequest.url.take(140)} resume=${playbackRequest.resumePositionMs ?: playbackRequest.positionMs}")
    val selector = DefaultTrackSelector(this)
    trackSelector = selector
    applyTrackSelectorSettings(selector, playbackRequest)

    val exoPlayer = ExoPlayer.Builder(this)
      .setTrackSelector(selector)
      .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
      .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
      .build()
    player = exoPlayer
    playerView.player = exoPlayer
    applyRequestedVideoMode(playbackRequest.settings.videoMode)
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
      override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
        val hasAudio = tracks.containsType(C.TRACK_TYPE_AUDIO)
        val hasText = tracks.containsType(C.TRACK_TYPE_TEXT)
        Log.d(TAG, "onTracksChanged hasAudio=$hasAudio hasText=$hasText")
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        Log.d(TAG, "onIsPlayingChanged=$isPlaying state=${playbackStateLabel(exoPlayer.playbackState)}")
        if (isPlaying) {
          cancelPlaybackStartTimeout()
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
            if (!BuildConfig.IS_TV) {
              playerView.post { playerView.showController() }
            }
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
        Log.d(TAG, "onPlaybackStateChanged=${playbackStateLabel(state)}")
        if (state == Player.STATE_READY) {
          maybeRunIntroAnimation()
        }

        if (state == Player.STATE_ENDED) {
          cancelPlaybackStartTimeout()
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
        Log.e(TAG, "onPlayerError code=${error.errorCodeName} message=${error.message}", error)
        cancelPlaybackStartTimeout()
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

    exoPlayer.setMediaItem(buildMediaItem(playbackRequest))
    exoPlayer.prepare()
    val startPosition = playbackRequest.resumePositionMs ?: playbackRequest.positionMs
    if (startPosition > 0L) {
      exoPlayer.seekTo(startPosition)
    }
    exoPlayer.playWhenReady = true
    Log.d(TAG, "playWhenReady=true")
    schedulePlaybackStartTimeout()
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
    titleFlashView.visibility = View.VISIBLE
    titleFlashView.animate()
      .alpha(0.25f)
      .setDuration(130L)
      .withEndAction {
        titleFlashView.animate().alpha(0f).setDuration(260L).withEndAction {
          titleFlashView.visibility = View.GONE
        }.start()
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
      applyRequestedVideoMode(it)
      appliedSettings["videoMode"] = true
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

  private fun triggerNativeAudioFallback(detail: String, message: String) {
    Log.w(TAG, "triggerNativeAudioFallback detail=$detail message=$message")
    triggerPlaybackFallback(
      errorCode = "native_audio_unavailable",
      failureDomain = "native_audio",
      detail = detail,
      message = message
    )
  }

  private fun schedulePlaybackStartTimeout() {
    cancelPlaybackStartTimeout()
    Log.d(TAG, "schedulePlaybackStartTimeout ms=$PLAYBACK_START_TIMEOUT_MS")
    uiHandler.postDelayed(playbackStartTimeoutRunnable, PLAYBACK_START_TIMEOUT_MS)
  }

  private fun cancelPlaybackStartTimeout() {
    uiHandler.removeCallbacks(playbackStartTimeoutRunnable)
  }

  private fun triggerPlaybackFallback(
    errorCode: String,
    failureDomain: String,
    detail: String,
    message: String
  ) {
    if (fallbackTriggered) {
      return
    }

    fallbackTriggered = true
    hasFailed = true
    cancelPlaybackStartTimeout()
    Log.w(TAG, "triggerPlaybackFallback code=$errorCode domain=$failureDomain detail=$detail message=$message")
    val activeRequest = request ?: return
    val diagnostics = NativePlaybackContracts.settingsDiagnostics(activeRequest.settings, appliedSettings)

    PlaybackBridge.sendPlaybackEvent(
      this,
      HostBridgeContract.createPlaybackPayload(
        status = "failed",
        streamId = activeRequest.streamId,
        errorCode = errorCode,
        message = message,
        url = activeRequest.url,
        fallbackWebUrl = activeRequest.fallbackWebUrl,
        resumePositionMs = player?.currentPosition ?: activeRequest.positionMs,
        fallbackTriggered = true,
        failureDomain = failureDomain,
        failureDetail = detail,
        settingsDiagnostics = diagnostics
      )
    )

    finish()
  }

  private fun playbackStateLabel(state: Int): String {
    return when (state) {
      Player.STATE_IDLE -> "IDLE"
      Player.STATE_BUFFERING -> "BUFFERING"
      Player.STATE_READY -> "READY"
      Player.STATE_ENDED -> "ENDED"
      else -> "UNKNOWN($state)"
    }
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

  private fun configurePlayerControls() {
    playerView.setUseController(true)
    playerView.setControllerAutoShow(true)
    playerView.setControllerShowTimeoutMs(4_000)
    playerView.setControllerHideOnTouch(true)
    playerView.setShowRewindButton(true)
    playerView.setShowFastForwardButton(true)
    playerView.setShowPreviousButton(false)
    playerView.setShowNextButton(false)
    playerView.setShowSubtitleButton(true)
    playerView.setShowVrButton(false)
    playerView.setRepeatToggleModes(0)
    playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
      Log.d(TAG, "controllerVisibility=$visibility")
      if (visibility == View.VISIBLE) {
        bindControllerButtons()
      }
      // Media3 controller visibility transitions can bring system bars back on some devices.
      uiHandler.post { applyImmersiveMode() }
    })
    bindControllerButtons()
  }

  private fun bindControllerButtons() {
    val settingsButton = playerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_settings)
    settingsButton?.setOnClickListener {
      showPlaybackOptionsDialog()
    }
    val controlsBackground = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_controls_background)
    controlsBackground?.setOnClickListener {
      if (playerView.isControllerFullyVisible()) {
        Log.d(TAG, "controller background tap -> hideController")
        playerView.hideController()
      }
    }
    playerView.setFullscreenButtonClickListener {
      showVideoModeDialog()
    }
  }

  private fun showPlaybackOptionsDialog() {
    val options = listOf(
      getString(R.string.playback_option_audio_tracks),
      getString(R.string.playback_option_subtitles),
      getString(R.string.playback_option_speed),
      getString(R.string.playback_option_video_mode, currentVideoModeLabel())
    )

    AlertDialog.Builder(this)
      .setTitle(R.string.playback_options_title)
      .setItems(options.toTypedArray()) { _, which ->
        when (which) {
          0 -> openTrackSelectionDialog(
            trackType = C.TRACK_TYPE_AUDIO,
            titleResId = R.string.playback_audio_title,
            emptyResId = R.string.playback_no_audio_tracks
          )
          1 -> openTrackSelectionDialog(
            trackType = C.TRACK_TYPE_TEXT,
            titleResId = R.string.playback_subtitles_title,
            emptyResId = R.string.playback_no_subtitles
          )
          2 -> showPlaybackSpeedDialog()
          3 -> showVideoModeDialog()
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun openTrackSelectionDialog(trackType: Int, titleResId: Int, emptyResId: Int) {
    val activePlayer = player ?: return
    val hasTracks = activePlayer.currentTracks.containsType(trackType)
    if (!hasTracks) {
      Toast.makeText(this, getString(emptyResId), Toast.LENGTH_SHORT).show()
      return
    }

    TrackSelectionDialogBuilder(this, getString(titleResId), activePlayer, trackType)
      .setShowDisableOption(trackType == C.TRACK_TYPE_TEXT)
      .build()
      .show()
    playerView.post { playerView.showController() }
  }

  private fun showPlaybackSpeedDialog() {
    val activePlayer = player ?: return
    val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    val speedLabels = speedValues.map { "${it}x" }.toTypedArray()
    val currentSpeed = activePlayer.playbackParameters.speed
    val selectedIndex = speedValues.indexOfFirst { abs(it - currentSpeed) < 0.01f }.takeIf { it >= 0 } ?: 2

    AlertDialog.Builder(this)
      .setTitle(R.string.playback_speed_title)
      .setSingleChoiceItems(speedLabels, selectedIndex) { dialog, which ->
        val selectedSpeed = speedValues[which]
        activePlayer.playbackParameters = PlaybackParameters(selectedSpeed)
        appliedSettings["playbackSpeed"] = true
        dialog.dismiss()
        playerView.showController()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun showVideoModeDialog() {
    val labels = arrayOf(
      getString(R.string.playback_mode_fit),
      getString(R.string.playback_mode_fill),
      getString(R.string.playback_mode_zoom)
    )
    val selectedIndex = when (currentResizeMode) {
      AspectRatioFrameLayout.RESIZE_MODE_FILL -> 1
      AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> 2
      else -> 0
    }

    AlertDialog.Builder(this)
      .setTitle(R.string.playback_video_mode_title)
      .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
        val nextMode = when (which) {
          1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
          2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
          else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        setVideoMode(nextMode)
        dialog.dismiss()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun setVideoMode(resizeMode: Int) {
    currentResizeMode = resizeMode
    playerView.setResizeMode(currentResizeMode)
    appliedSettings["videoMode"] = true
    Toast.makeText(this, R.string.playback_video_mode_changed, Toast.LENGTH_SHORT).show()
    playerView.showController()
  }

  private fun applyRequestedVideoMode(rawVideoMode: String?) {
    val normalized = rawVideoMode?.trim()?.lowercase().orEmpty()
    val requestedResizeMode = when {
      normalized.contains("fill") || normalized.contains("stretch") -> AspectRatioFrameLayout.RESIZE_MODE_FILL
      normalized.contains("zoom") || normalized.contains("crop") || normalized.contains("cover") ->
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
      else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    currentResizeMode = requestedResizeMode
    playerView.setResizeMode(requestedResizeMode)
  }

  private fun currentVideoModeLabel(): String {
    return when (currentResizeMode) {
      AspectRatioFrameLayout.RESIZE_MODE_FILL -> getString(R.string.playback_mode_fill)
      AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> getString(R.string.playback_mode_zoom)
      else -> getString(R.string.playback_mode_fit)
    }
  }

  private fun buildMediaItem(playbackRequest: NativePlaybackRequest): MediaItem {
    val subtitleConfigurations = playbackRequest.tracks.subtitlesTracks.mapNotNull { track ->
      val subtitleUrl = track.url?.trim().orEmpty()
      if (subtitleUrl.isBlank()) {
        return@mapNotNull null
      }

      val subtitleUri = runCatching { Uri.parse(subtitleUrl) }.getOrNull() ?: return@mapNotNull null
      val mimeType = inferSubtitleMimeType(subtitleUrl, track.mode)
      val subtitleBuilder = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
        .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
        .setSelectionFlags(
          if (track.id != null && track.id == playbackRequest.tracks.selectedSubtitlesTrackId) {
            C.SELECTION_FLAG_DEFAULT
          } else {
            0
          }
        )

      if (!track.lang.isNullOrBlank()) {
        subtitleBuilder.setLanguage(track.lang)
      }
      if (!track.label.isNullOrBlank()) {
        subtitleBuilder.setLabel(track.label)
      }
      if (!track.id.isNullOrBlank()) {
        subtitleBuilder.setId(track.id)
      }
      if (!mimeType.isNullOrBlank()) {
        subtitleBuilder.setMimeType(mimeType)
      }

      subtitleBuilder.build()
    }

    if (subtitleConfigurations.isNotEmpty()) {
      Log.d(TAG, "Attaching ${subtitleConfigurations.size} external subtitle tracks to media item")
    }

    return MediaItem.Builder()
      .setUri(playbackRequest.url)
      .apply {
        if (subtitleConfigurations.isNotEmpty()) {
          setSubtitleConfigurations(subtitleConfigurations)
        }
      }
      .build()
  }

  private fun inferSubtitleMimeType(url: String, mode: String?): String? {
    val normalizedMode = mode?.trim()?.lowercase().orEmpty()
    if (normalizedMode.contains("vtt")) {
      return MimeTypes.TEXT_VTT
    }
    if (normalizedMode.contains("ass") || normalizedMode.contains("ssa")) {
      return MimeTypes.TEXT_SSA
    }
    if (normalizedMode.contains("ttml") || normalizedMode.contains("dfxp")) {
      return MimeTypes.APPLICATION_TTML
    }
    if (normalizedMode.contains("srt") || normalizedMode.contains("subrip")) {
      return MimeTypes.APPLICATION_SUBRIP
    }

    val normalizedPath = runCatching {
      Uri.parse(url).lastPathSegment?.lowercase().orEmpty()
    }.getOrDefault(url.lowercase())

    return when {
      normalizedPath.endsWith(".vtt") -> MimeTypes.TEXT_VTT
      normalizedPath.endsWith(".ass") || normalizedPath.endsWith(".ssa") -> MimeTypes.TEXT_SSA
      normalizedPath.endsWith(".ttml") || normalizedPath.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
      normalizedPath.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
      else -> null
    }
  }

  private fun applyImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
  }
}
