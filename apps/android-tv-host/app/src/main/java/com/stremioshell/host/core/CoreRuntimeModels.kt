package com.stremioshell.host.core

import org.json.JSONArray
import org.json.JSONObject

const val CORE_CONTRACT_VERSION = 1

data class CoreEnvelope(
  val type: String,
  val version: Int = CORE_CONTRACT_VERSION,
  val payload: JSONObject,
  val timestampMs: Long = System.currentTimeMillis()
) {
  fun toJson(): JSONObject {
    return JSONObject()
      .put("type", type)
      .put("version", version)
      .put("payload", payload)
      .put("timestampMs", timestampMs)
  }
}

sealed interface CoreAction {
  fun toEnvelope(): CoreEnvelope
}

data class RuntimeInitializeAction(
  val source: String = "host"
) : CoreAction {
  override fun toEnvelope(): CoreEnvelope {
    return CoreEnvelope(
      type = "runtime.initialize",
      payload = JSONObject().put("source", source)
    )
  }
}

data class AuthLoginAction(
  val method: String,
  val token: String? = null,
  val email: String? = null
) : CoreAction {
  override fun toEnvelope(): CoreEnvelope {
    return CoreEnvelope(
      type = "auth.login",
      payload = JSONObject().put("method", method).apply {
        token?.takeIf { it.isNotBlank() }?.let { put("token", it) }
        email?.takeIf { it.isNotBlank() }?.let { put("email", it) }
      }
    )
  }
}

data class AuthLogoutAction(
  val reason: String = "user"
) : CoreAction {
  override fun toEnvelope(): CoreEnvelope {
    return CoreEnvelope(
      type = "auth.logout",
      payload = JSONObject().put("reason", reason)
    )
  }
}

data class LibrarySyncAction(
  val force: Boolean = false
) : CoreAction {
  override fun toEnvelope(): CoreEnvelope {
    return CoreEnvelope(
      type = "library.sync",
      payload = JSONObject().put("force", force)
    )
  }
}

data class PlaybackSelectStreamAction(
  val streamId: String,
  val streamBase64: String,
  val autoPlay: Boolean = true
) : CoreAction {
  override fun toEnvelope(): CoreEnvelope {
    return CoreEnvelope(
      type = "playback.selectStream",
      payload = JSONObject()
        .put("streamId", streamId)
        .put("streamBase64", streamBase64)
        .put("autoPlay", autoPlay)
    )
  }
}

data class PlaybackReportProgressAction(
  val streamId: String,
  val progressMs: Long,
  val durationMs: Long? = null
) : CoreAction {
  override fun toEnvelope(): CoreEnvelope {
    return CoreEnvelope(
      type = "playback.reportProgress",
      payload = JSONObject()
        .put("streamId", streamId)
        .put("progressMs", progressMs)
        .apply {
          durationMs?.let { put("durationMs", it) }
        }
    )
  }
}

data class CustomAction(
  val customType: String,
  val customPayload: JSONObject
) : CoreAction {
  override fun toEnvelope(): CoreEnvelope {
    val type = if (customType.startsWith("custom.")) customType else "custom.$customType"
    return CoreEnvelope(type = type, payload = customPayload)
  }
}

data class TelemetryEvent(
  val name: String,
  val level: String,
  val context: JSONObject? = null
) {
  fun toEnvelope(): CoreEnvelope {
    return CoreEnvelope(
      type = "telemetry.event",
      payload = JSONObject()
        .put("name", name)
        .put("level", level)
        .apply {
          context?.let { put("context", it) }
        }
    )
  }
}

data class CoreStateQuery(
  val scope: String,
  val key: String? = null,
  val params: JSONObject? = null
) {
  fun toJson(): JSONObject {
    return JSONObject()
      .put("scope", scope)
      .apply {
        key?.takeIf { it.isNotBlank() }?.let { put("key", it) }
        params?.let { put("params", it) }
      }
  }
}

data class CoreStateSnapshot(
  val scope: String,
  val version: Int,
  val updatedAtMs: Long,
  val data: Any?
) {
  fun toJson(): JSONObject {
    return JSONObject()
      .put("scope", scope)
      .put("version", version)
      .put("updatedAtMs", updatedAtMs)
      .put("data", data)
  }
}

sealed interface CoreEvent {
  val envelope: CoreEnvelope
}

data class RuntimeInitializedEvent(
  override val envelope: CoreEnvelope,
  val source: String
) : CoreEvent

data class RuntimeErrorEvent(
  override val envelope: CoreEnvelope,
  val code: String,
  val message: String,
  val recoverable: Boolean,
  val details: JSONObject?
) : CoreEvent

data class RuntimeRawEvent(
  override val envelope: CoreEnvelope,
  val rawEvent: Any?
) : CoreEvent

data class AuthChangedEvent(
  override val envelope: CoreEnvelope,
  val isAuthenticated: Boolean,
  val userId: String?
) : CoreEvent

data class LibraryChangedEvent(
  override val envelope: CoreEnvelope,
  val itemCount: Int?,
  val changedItemIds: List<String>,
  val reason: String?
) : CoreEvent

data class PlaybackProgressEvent(
  override val envelope: CoreEnvelope,
  val streamId: String,
  val progressMs: Long
) : CoreEvent

data class TelemetryCoreEvent(
  override val envelope: CoreEnvelope,
  val name: String,
  val level: String,
  val context: JSONObject?
) : CoreEvent

data class UnknownCoreEvent(
  override val envelope: CoreEnvelope
) : CoreEvent

object CoreEnvelopeParser {
  fun parse(json: JSONObject): CoreEnvelope {
    val type = json.optString("type").ifBlank { "runtime.raw" }
    val version = json.optInt("version", CORE_CONTRACT_VERSION)
    val payload = json.optJSONObject("payload") ?: JSONObject()
    val timestampMs = json.optLong("timestampMs", System.currentTimeMillis())
    return CoreEnvelope(
      type = type,
      version = version,
      payload = payload,
      timestampMs = timestampMs
    )
  }

  fun parseEvent(json: JSONObject): CoreEvent {
    val envelope = parse(json)
    val payload = envelope.payload
    return when (envelope.type) {
      "runtime.initialized" -> RuntimeInitializedEvent(
        envelope = envelope,
        source = payload.optString("source").ifBlank { "core-bridge" }
      )

      "runtime.error" -> RuntimeErrorEvent(
        envelope = envelope,
        code = payload.optString("code").ifBlank { "runtime_error" },
        message = payload.optString("message").ifBlank { "Unknown runtime error." },
        recoverable = payload.optBoolean("recoverable", false),
        details = payload.optJSONObject("details")
      )

      "runtime.raw" -> RuntimeRawEvent(
        envelope = envelope,
        rawEvent = payload.opt("rawEvent")
      )

      "auth.changed" -> AuthChangedEvent(
        envelope = envelope,
        isAuthenticated = payload.optBoolean("isAuthenticated", false),
        userId = payload.optString("userId").ifBlank { null }
      )

      "library.changed" -> LibraryChangedEvent(
        envelope = envelope,
        itemCount = payload.optInt("itemCount").takeIf { payload.has("itemCount") },
        changedItemIds = payload.optJSONArray("changedItemIds").toStringList(),
        reason = payload.optString("reason").ifBlank { null }
      )

      "playback.progress" -> PlaybackProgressEvent(
        envelope = envelope,
        streamId = payload.optString("streamId").ifBlank { "unknown" },
        progressMs = payload.optLong("progressMs", 0L)
      )

      "telemetry.event" -> TelemetryCoreEvent(
        envelope = envelope,
        name = payload.optString("name").ifBlank { "unknown" },
        level = payload.optString("level").ifBlank { "info" },
        context = payload.optJSONObject("context")
      )

      else -> UnknownCoreEvent(envelope)
    }
  }
}

private fun JSONArray?.toStringList(): List<String> {
  if (this == null) {
    return emptyList()
  }

  val values = mutableListOf<String>()
  for (index in 0 until length()) {
    val value = optString(index).trim()
    if (value.isNotBlank()) {
      values += value
    }
  }
  return values
}
