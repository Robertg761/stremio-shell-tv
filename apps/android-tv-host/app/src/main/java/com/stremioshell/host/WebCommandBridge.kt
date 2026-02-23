package com.stremioshell.host

import android.webkit.JavascriptInterface

@Deprecated(
  message = "Legacy WebView command bridge. Compose runtime path does not use window.stremioHost."
)
class WebCommandBridge(
  private val onCommand: (commandJson: String) -> Unit
) {
  @JavascriptInterface
  fun sendCommand(commandJson: String?) {
    if (commandJson.isNullOrBlank()) {
      return
    }
    onCommand(commandJson)
  }
}
