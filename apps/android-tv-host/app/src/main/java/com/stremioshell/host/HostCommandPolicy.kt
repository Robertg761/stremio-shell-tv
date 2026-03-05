package com.stremioshell.host

object HostCommandPolicy {
  private val REMOTE_FALLBACK_ALLOWLIST = setOf(
    "playback.open",
    "back.handled"
  )

  fun isAllowed(commandType: String, shellSource: String, usingLocalDebugServer: Boolean): Boolean {
    val isRemoteFallback = shellSource == "remote" && !usingLocalDebugServer
    if (!isRemoteFallback) {
      return true
    }
    return commandType in REMOTE_FALLBACK_ALLOWLIST
  }
}
