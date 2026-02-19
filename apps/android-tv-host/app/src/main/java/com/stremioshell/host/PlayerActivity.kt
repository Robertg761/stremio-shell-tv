package com.stremioshell.host

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {
  private var player: ExoPlayer? = null
  private lateinit var playerView: PlayerView

  private var streamId: String? = null
  private var hasStarted = false
  private var hasFailed = false
  private var hasCompleted = false

  private val closeReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      finish()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_player)
    volumeControlStream = AudioManager.STREAM_MUSIC

    playerView = findViewById(R.id.playerView)

    ContextCompat.registerReceiver(
      this,
      closeReceiver,
      IntentFilter(PlaybackBridge.ACTION_PLAYBACK_CLOSE),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )

    val mediaUrl = intent.getStringExtra(PlaybackBridge.EXTRA_URL).orEmpty()
    streamId = intent.getStringExtra(PlaybackBridge.EXTRA_STREAM_ID)
    val startPositionMs = intent.getLongExtra(PlaybackBridge.EXTRA_POSITION_MS, 0L)

    if (mediaUrl.isBlank()) {
      val payload = HostBridgeContract.createPlaybackPayload(
        status = "failed",
        streamId = streamId,
        errorCode = "missing_url",
        message = "No media URL provided to player."
      )
      PlaybackBridge.sendPlaybackEvent(this, payload)
      finish()
      return
    }

    initializePlayer(mediaUrl, startPositionMs)
  }

  override fun onStop() {
    super.onStop()
    if (hasStarted && !hasCompleted && !hasFailed) {
      PlaybackBridge.sendPlaybackEvent(this, HostBridgeContract.createPlaybackPayload(status = "paused", streamId = streamId))
    }
  }

  override fun onDestroy() {
    runCatching {
      unregisterReceiver(closeReceiver)
    }

    playerView.player = null
    player?.release()
    player = null
    super.onDestroy()
  }

  private fun initializePlayer(mediaUrl: String, startPositionMs: Long) {
    val exoPlayer = ExoPlayer.Builder(this).build()
    player = exoPlayer
    playerView.player = exoPlayer
    exoPlayer.setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build(),
      true
    )
    exoPlayer.volume = 1f

    exoPlayer.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
          if (!hasStarted) {
            hasStarted = true
            PlaybackBridge.sendPlaybackEvent(
              this@PlayerActivity,
              HostBridgeContract.createPlaybackPayload(status = "started", streamId = streamId)
            )
          } else {
            PlaybackBridge.sendPlaybackEvent(
              this@PlayerActivity,
              HostBridgeContract.createPlaybackPayload(status = "resumed", streamId = streamId)
            )
          }
        } else if (hasStarted && exoPlayer.playbackState == Player.STATE_READY) {
          PlaybackBridge.sendPlaybackEvent(
            this@PlayerActivity,
            HostBridgeContract.createPlaybackPayload(status = "paused", streamId = streamId)
          )
        }
      }

      override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) {
          hasCompleted = true
          PlaybackBridge.sendPlaybackEvent(
            this@PlayerActivity,
            HostBridgeContract.createPlaybackPayload(status = "completed", streamId = streamId)
          )
        }
      }

      override fun onPlayerError(error: PlaybackException) {
        hasFailed = true
        PlaybackBridge.sendPlaybackEvent(
          this@PlayerActivity,
          HostBridgeContract.createPlaybackPayload(
            status = "failed",
            streamId = streamId,
            errorCode = error.errorCodeName,
            message = error.message
          )
        )
      }
    })

    exoPlayer.setMediaItem(MediaItem.fromUri(mediaUrl))
    exoPlayer.prepare()
    if (startPositionMs > 0L) {
      exoPlayer.seekTo(startPositionMs)
    }
    exoPlayer.playWhenReady = true
  }
}
