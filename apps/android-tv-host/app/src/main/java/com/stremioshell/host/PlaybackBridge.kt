package com.stremioshell.host

import android.content.Context
import android.content.Intent
import org.json.JSONObject

@Deprecated(
  message = "Legacy inter-activity playback bridge retained as migration fixture; Compose runtime uses in-process player events."
)
object PlaybackBridge {
  const val ACTION_PLAYBACK_EVENT = "com.stremioshell.host.action.PLAYBACK_EVENT"
  const val ACTION_PLAYBACK_CLOSE = "com.stremioshell.host.action.PLAYBACK_CLOSE"
  const val EXTRA_EVENT_JSON = "event_json"
  const val EXTRA_URL = "url"
  const val EXTRA_STREAM_ID = "stream_id"
  const val EXTRA_TITLE = "title"
  const val EXTRA_SUBTITLE = "subtitle"
  const val EXTRA_POSITION_MS = "position_ms"
  const val EXTRA_ARTWORK_URL = "artwork_url"
  const val EXTRA_LOGO_URL = "logo_url"
  const val EXTRA_RESUME_POSITION_MS = "resume_position_ms"
  const val EXTRA_FALLBACK_WEB_URL = "fallback_web_url"
  const val EXTRA_SETTINGS_JSON = "settings_json"
  const val EXTRA_TRACKS_JSON = "tracks_json"
  const val EXTRA_SOURCE_URL = "source_url"

  fun sendPlaybackEvent(context: Context, payload: JSONObject) {
    val event = HostBridgeContract.createEventEnvelope("playback.result", payload)
    val intent = Intent(ACTION_PLAYBACK_EVENT)
      .setPackage(context.packageName)
      .putExtra(EXTRA_EVENT_JSON, event.toString())
    context.sendBroadcast(intent)
  }

  fun requestPlaybackClose(context: Context) {
    val intent = Intent(ACTION_PLAYBACK_CLOSE).setPackage(context.packageName)
    context.sendBroadcast(intent)
  }
}
