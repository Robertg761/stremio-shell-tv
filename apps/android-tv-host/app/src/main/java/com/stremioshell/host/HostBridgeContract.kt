package com.stremioshell.host

import org.json.JSONObject

data class HostEnvelope(
  val type: String,
  val version: Int,
  val payload: JSONObject,
  val timestampMs: Long
)

object HostBridgeContract {
  const val CONTRACT_VERSION = 1
  const val HOST_EVENT_NAME = "stremio:host-event"

  fun parseCommand(commandJson: String): Result<HostEnvelope> {
    return runCatching {
      val parsed = JSONObject(commandJson)
      val type = parsed.optString("type")
      val version = parsed.optInt("version", -1)
      val payload = parsed.optJSONObject("payload")
      val timestampMs = parsed.optLong("timestampMs", 0L)

      if (type.isBlank()) {
        throw IllegalArgumentException("Command envelope missing type.")
      }
      if (version != CONTRACT_VERSION) {
        throw IllegalArgumentException("Unsupported command version $version.")
      }
      if (payload == null) {
        throw IllegalArgumentException("Command envelope payload must be an object.")
      }
      if (timestampMs <= 0L) {
        throw IllegalArgumentException("Command envelope missing timestampMs.")
      }

      HostEnvelope(type = type, version = version, payload = payload, timestampMs = timestampMs)
    }
  }

  fun createEventEnvelope(type: String, payload: JSONObject): JSONObject {
    return JSONObject().apply {
      put("type", type)
      put("version", CONTRACT_VERSION)
      put("payload", payload)
      put("timestampMs", System.currentTimeMillis())
    }
  }

  fun createPlaybackPayload(
    status: String,
    streamId: String? = null,
    errorCode: String? = null,
    message: String? = null
  ): JSONObject {
    return JSONObject().apply {
      put("status", status)
      if (!streamId.isNullOrBlank()) {
        put("streamId", streamId)
      }
      if (!errorCode.isNullOrBlank()) {
        put("errorCode", errorCode)
      }
      if (!message.isNullOrBlank()) {
        put("message", message)
      }
    }
  }
}
