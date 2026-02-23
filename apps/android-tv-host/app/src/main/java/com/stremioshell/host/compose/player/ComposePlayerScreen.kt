package com.stremioshell.host.compose.player

import android.net.Uri
import android.widget.Toast
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import com.stremioshell.host.R
import kotlinx.coroutines.delay
import android.view.KeyEvent as AndroidKeyEvent

@Composable
fun ComposePlayerScreen(
  initialUrl: String?,
  onProgress: (Long) -> Unit,
  onDiagnostic: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var mediaUrl by remember { mutableStateOf(initialUrl.orEmpty()) }
  var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
  val speeds = remember { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f) }
  var speedIndex by remember { mutableIntStateOf(2) }
  var subtitlesEnabled by remember { mutableStateOf(true) }
  var optionsVisible by remember { mutableStateOf(false) }
  var playbackError by remember { mutableStateOf<String?>(null) }
  var hasStarted by remember { mutableStateOf(false) }
  var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
  val context = LocalContext.current

  val exoPlayer = remember(context) {
    ExoPlayer.Builder(context).build().apply {
      playWhenReady = true
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      exoPlayer.release()
    }
  }

  DisposableEffect(exoPlayer) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(state: Int) {
        val label = when (state) {
          Player.STATE_IDLE -> "IDLE"
          Player.STATE_BUFFERING -> "BUFFERING"
          Player.STATE_READY -> "READY"
          Player.STATE_ENDED -> "ENDED"
          else -> "UNKNOWN($state)"
        }
        onDiagnostic("playback.state=$label")
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
          hasStarted = true
          onDiagnostic("playback.started")
        } else {
          onDiagnostic("playback.paused")
        }
      }

      override fun onPlayerError(error: PlaybackException) {
        val errorMessage = error.message ?: "Unknown playback error."
        onDiagnostic("playback.failed code=${error.errorCodeName} message=$errorMessage")
        playbackError = errorMessage
      }
    }

    exoPlayer.addListener(listener)
    onDispose {
      exoPlayer.removeListener(listener)
    }
  }

  LaunchedEffect(mediaUrl) {
    val trimmed = mediaUrl.trim()
    if (trimmed.isBlank()) {
      return@LaunchedEffect
    }

    hasStarted = false
    playbackError = null
    exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(trimmed)))
    exoPlayer.prepare()
    exoPlayer.playWhenReady = true
    onDiagnostic("playback.select url=$trimmed")
  }

  LaunchedEffect(mediaUrl, hasStarted) {
    val trimmed = mediaUrl.trim()
    if (trimmed.isBlank()) {
      return@LaunchedEffect
    }

    if (hasStarted) {
      return@LaunchedEffect
    }

    val startedAt = System.currentTimeMillis()
    while (!hasStarted && (System.currentTimeMillis() - startedAt) < 20_000L) {
      delay(250L)
    }

    if (!hasStarted) {
      val message = "Native player timed out while starting playback."
      onDiagnostic("playback.fallback code=native_playback_timeout domain=network detail=startup_timeout message=$message")
      playbackError = message
    }
  }

  LaunchedEffect(exoPlayer) {
    while (true) {
      onProgress(exoPlayer.currentPosition)
      delay(1000L)
    }
  }

  BackHandler(enabled = playerViewRef?.isControllerFullyVisible() == true) {
    playerViewRef?.hideController()
    onDiagnostic("player.back hide_controller=true")
  }

  fun togglePlaybackByRemote() {
    exoPlayer.playWhenReady = !exoPlayer.playWhenReady
    playerViewRef?.showController()
  }

  fun openTrackSelectionDialog(trackType: Int, titleResId: Int, emptyResId: Int) {
    if (!exoPlayer.currentTracks.containsType(trackType)) {
      Toast.makeText(context, context.getString(emptyResId), Toast.LENGTH_SHORT).show()
      return
    }

    TrackSelectionDialogBuilder(context, context.getString(titleResId), exoPlayer, trackType)
      .setShowDisableOption(trackType == C.TRACK_TYPE_TEXT)
      .build()
      .show()
    playerViewRef?.post { playerViewRef?.showController() }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface
          )
        )
      )
      .padding(20.dp)
      .onPreviewKeyEvent { keyEvent ->
        if (keyEvent.type != KeyEventType.KeyDown) {
          return@onPreviewKeyEvent false
        }

        when (keyEvent.key) {
          Key.Menu -> {
            optionsVisible = true
            true
          }
          Key.MediaPlayPause,
          Key.Spacebar,
          Key.Enter,
          Key.NumPadEnter -> {
            val controllerVisible = playerViewRef?.isControllerFullyVisible() == true
            if (!controllerVisible) {
              togglePlaybackByRemote()
              true
            } else {
              false
            }
          }
          else -> false
        }
      }
  ) {
    Text("Player", style = MaterialTheme.typography.headlineMedium)
    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
      value = mediaUrl,
      onValueChange = { mediaUrl = it },
      label = { Text("Stream URL") },
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
    )

    Spacer(modifier = Modifier.height(12.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Button(onClick = {
        exoPlayer.setPlaybackParameters(PlaybackParameters(speeds[speedIndex]))
      }) {
        Text("Speed ${speeds[speedIndex]}x")
      }
      Button(onClick = {
        speedIndex = (speedIndex + 1) % speeds.size
        exoPlayer.setPlaybackParameters(PlaybackParameters(speeds[speedIndex]))
      }) {
        Text("Next speed")
      }
      Button(onClick = {
        resizeMode = when (resizeMode) {
          AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
          AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
          else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        }
      }) {
        Text("Mode")
      }
      Button(onClick = {
        optionsVisible = true
      }) {
        Text("Options")
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "Playback: ${if (exoPlayer.playWhenReady) "playing" else "paused"}  speed=${speeds[speedIndex]}x  subtitles=${if (subtitlesEnabled) "on" else "off"}",
      style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(8.dp))

    AndroidView(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      factory = { context ->
        PlayerView(context).apply {
          player = exoPlayer
          useController = true
          setControllerShowTimeoutMs(4_000)
          setShowRewindButton(true)
          setShowFastForwardButton(true)
          setShowSubtitleButton(true)
          setControllerAutoShow(true)
          setOnKeyListener { _, keyCode, event ->
            if (event.action != AndroidKeyEvent.ACTION_DOWN) {
              return@setOnKeyListener false
            }
            when (keyCode) {
              AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                true
              }
              AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                exoPlayer.seekBack()
                true
              }
              AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                exoPlayer.seekForward()
                true
              }
              AndroidKeyEvent.KEYCODE_MENU,
              AndroidKeyEvent.KEYCODE_INFO -> {
                optionsVisible = true
                true
              }
              else -> false
            }
          }
        }
      },
      update = { view ->
        view.player = exoPlayer
        view.resizeMode = resizeMode
        view.keepScreenOn = true
        view.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        playerViewRef = view
      }
    )
  }

  if (optionsVisible) {
    AlertDialog(
      onDismissRequest = { optionsVisible = false },
      title = { Text("Playback options") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
              openTrackSelectionDialog(
                trackType = C.TRACK_TYPE_AUDIO,
                titleResId = R.string.playback_audio_title,
                emptyResId = R.string.playback_no_audio_tracks
              )
            }) { Text("Audio tracks") }
            TextButton(onClick = {
              openTrackSelectionDialog(
                trackType = C.TRACK_TYPE_TEXT,
                titleResId = R.string.playback_subtitles_title,
                emptyResId = R.string.playback_no_subtitles
              )
            }) { Text("Subtitles") }
          }

          Text("Video mode")
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
              resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }) { Text("Fit") }
            TextButton(onClick = {
              resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            }) { Text("Fill") }
            TextButton(onClick = {
              resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }) { Text("Zoom") }
          }

          Text("Speed")
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
              speedIndex = (speedIndex - 1).coerceAtLeast(0)
              exoPlayer.setPlaybackParameters(PlaybackParameters(speeds[speedIndex]))
            }) { Text("-") }
            Text("${speeds[speedIndex]}x")
            TextButton(onClick = {
              speedIndex = (speedIndex + 1).coerceAtMost(speeds.lastIndex)
              exoPlayer.setPlaybackParameters(PlaybackParameters(speeds[speedIndex]))
            }) { Text("+") }
          }

          Text("Subtitles")
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
              subtitlesEnabled = !subtitlesEnabled
              exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                .build()
              onDiagnostic("playback.subtitles enabled=$subtitlesEnabled")
            }) {
              Text(if (subtitlesEnabled) "Disable" else "Enable")
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { optionsVisible = false }) {
          Text("Done")
        }
      }
    )
  }

  val error = playbackError
  if (error != null) {
    AlertDialog(
      onDismissRequest = { playbackError = null },
      title = { Text("Playback failed") },
      text = { Text(error) },
      confirmButton = {
        TextButton(onClick = { playbackError = null }) {
          Text("OK")
        }
      }
    )
  }
}
