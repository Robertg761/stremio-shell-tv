package com.stremioshell.host

import org.json.JSONArray
import org.json.JSONObject

data class HostEnvelope(
  val type: String,
  val version: Int,
  val payload: JSONObject,
  val timestampMs: Long
)

@Deprecated(
  message = "Legacy WebView bridge contract. Compose runtime path no longer uses this as the runtime entrypoint."
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
    message: String? = null,
    url: String? = null,
    fallbackWebUrl: String? = null,
    resumePositionMs: Long? = null,
    fallbackTriggered: Boolean? = null,
    failureDomain: String? = null,
    failureDetail: String? = null,
    settingsDiagnostics: JSONArray? = null
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
      if (!url.isNullOrBlank()) {
        put("url", url)
      }
      if (!fallbackWebUrl.isNullOrBlank()) {
        put("fallbackWebUrl", fallbackWebUrl)
      }
      if (resumePositionMs != null && resumePositionMs >= 0L) {
        put("resumePositionMs", resumePositionMs)
      }
      if (fallbackTriggered != null) {
        put("fallbackTriggered", fallbackTriggered)
      }
      if (!failureDomain.isNullOrBlank()) {
        put("failureDomain", failureDomain)
      }
      if (!failureDetail.isNullOrBlank()) {
        put("failureDetail", failureDetail)
      }
      if (settingsDiagnostics != null) {
        put("settingsDiagnostics", settingsDiagnostics)
      }
    }
  }
}
