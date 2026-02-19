package com.stremioshell.host

import android.webkit.JavascriptInterface

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
