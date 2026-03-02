package com.stremioshell.host

internal data class BackRequestState(
  val requestId: String,
  val createdAtMs: Long,
  val expiresAtMs: Long
)

internal sealed class BackHandshakeResolution {
  data class Handled(val requestId: String, val reason: String?) : BackHandshakeResolution()
  data class RunNativeFallback(val requestId: String, val reason: String) : BackHandshakeResolution()
  data class Ignored(val reason: String) : BackHandshakeResolution()
}

internal class BackHandshakeController(
  private val timeoutMs: Long = 250L
) {
  private var pendingRequest: BackRequestState? = null

  fun begin(requestId: String, nowMs: Long = System.currentTimeMillis()): BackRequestState? {
    if (requestId.isBlank() || pendingRequest != null) {
      return null
    }

    val state = BackRequestState(
      requestId = requestId,
      createdAtMs = nowMs,
      expiresAtMs = nowMs + timeoutMs
    )
    pendingRequest = state
    return state
  }

  fun acknowledge(
    requestId: String,
    handled: Boolean,
    reason: String? = null,
    nowMs: Long = System.currentTimeMillis()
  ): BackHandshakeResolution {
    val pending = pendingRequest ?: return BackHandshakeResolution.Ignored("no_pending_request")
    if (pending.requestId != requestId) {
      return BackHandshakeResolution.Ignored("request_id_mismatch")
    }

    pendingRequest = null
    if (nowMs > pending.expiresAtMs) {
      return BackHandshakeResolution.RunNativeFallback(requestId = requestId, reason = "ack_timeout")
    }

    return if (handled) {
      BackHandshakeResolution.Handled(requestId = requestId, reason = reason)
    } else {
      BackHandshakeResolution.RunNativeFallback(
        requestId = requestId,
        reason = reason?.ifBlank { "host_unhandled" } ?: "host_unhandled"
      )
    }
  }

  fun onTimeout(requestId: String, nowMs: Long = System.currentTimeMillis()): BackHandshakeResolution {
    val pending = pendingRequest ?: return BackHandshakeResolution.Ignored("no_pending_request")
    if (pending.requestId != requestId) {
      return BackHandshakeResolution.Ignored("request_id_mismatch")
    }
    if (nowMs < pending.expiresAtMs) {
      return BackHandshakeResolution.Ignored("timeout_not_reached")
    }

    pendingRequest = null
    return BackHandshakeResolution.RunNativeFallback(requestId = requestId, reason = "ack_timeout")
  }

  fun pendingRequestId(): String? = pendingRequest?.requestId

  fun clear(): BackRequestState? {
    val current = pendingRequest
    pendingRequest = null
    return current
  }
}
