package com.stremioshell.host

import org.json.JSONObject

internal enum class PlaybackOpenDecision {
  OPENED,
  BLOCKED_BY_LOOP_GUARD,
  INVALID_PAYLOAD
}

internal data class PlaybackOpenOutcome(
  val decision: PlaybackOpenDecision,
  val streamId: String? = null,
  val url: String? = null,
  val fallbackWebUrl: String? = null
)

internal object PlaybackOpenDecider {
  const val LOOP_GUARD_FAILURE_DETAIL = "loop_guard_skip"
  const val INVALID_OPEN_PAYLOAD_FAILURE_DETAIL = "invalid_open_payload"

  fun classify(
    payload: JSONObject,
    request: NativePlaybackRequest?,
    shouldSkipNativePlayback: Boolean
  ): PlaybackOpenOutcome {
    if (request == null) {
      return PlaybackOpenOutcome(
        decision = PlaybackOpenDecision.INVALID_PAYLOAD,
        streamId = payload.optString("streamId").ifBlank { null },
        url = payload.optString("url").ifBlank { null },
        fallbackWebUrl = payload.optString("fallbackWebUrl").ifBlank { null }
      )
    }

    if (shouldSkipNativePlayback) {
      return PlaybackOpenOutcome(
        decision = PlaybackOpenDecision.BLOCKED_BY_LOOP_GUARD,
        streamId = request.streamId,
        url = request.url,
        fallbackWebUrl = request.fallbackWebUrl
      )
    }

    return PlaybackOpenOutcome(
      decision = PlaybackOpenDecision.OPENED,
      streamId = request.streamId,
      url = request.url,
      fallbackWebUrl = request.fallbackWebUrl
    )
  }
}
