package com.stremioshell.host

import android.content.Context
import android.content.Intent
import org.json.JSONObject

object PlaybackBridge {
  const val ACTION_PLAYBACK_EVENT = "com.stremioshell.host.action.PLAYBACK_EVENT"
  const val ACTION_PLAYBACK_CLOSE = "com.stremioshell.host.action.PLAYBACK_CLOSE"
  const val EXTRA_EVENT_JSON = "event_json"
  const val EXTRA_URL = "url"
  const val EXTRA_STREAM_ID = "stream_id"
  const val EXTRA_TITLE = "title"
  const val EXTRA_SUBTITLE = "subtitle"
  const val EXTRA_POSITION_MS = "position_ms"

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
