package com.stremioshell.host.compose

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiagnosticsStore(
  private val maxEntries: Int = 300
) {
  private val hostEvents = ArrayDeque<String>()
  private val runtimeEvents = ArrayDeque<String>()
  private val backDecisions = ArrayDeque<String>()
  private val entriesFlow = MutableStateFlow<List<String>>(emptyList())

  val entries: StateFlow<List<String>> = entriesFlow.asStateFlow()

  @Synchronized
  fun record(channel: String, message: String) {
    val line = "${timestamp()} [$channel] $message"
    entriesFlow.value = (listOf(line) + entriesFlow.value).take(maxEntries)
  }

  fun recordHostEvent(message: String) {
    appendBounded(hostEvents, "${timestamp()} $message")
    record("host.event", message)
  }

  fun recordRuntimeEvent(message: String) {
    appendBounded(runtimeEvents, "${timestamp()} $message")
    record("runtime.event", message)
  }

  fun recordBackDecision(message: String) {
    appendBounded(backDecisions, "${timestamp()} $message")
    record("back.decision", message)
  }

  fun exportText(): String {
    return buildString {
      appendLine("Stremio Shell Compose diagnostics")
      appendLine("generatedAt=${System.currentTimeMillis()}")
      appendLine()
      appendLine("=== Recent diagnostics ===")
      entriesFlow.value.forEach { appendLine(it) }
      appendLine()
      appendLine("=== Recent host events ===")
      if (hostEvents.isEmpty()) {
        appendLine("none")
      } else {
        hostEvents.forEach { appendLine(it) }
      }
      appendLine()
      appendLine("=== Recent runtime events ===")
      if (runtimeEvents.isEmpty()) {
        appendLine("none")
      } else {
        runtimeEvents.forEach { appendLine(it) }
      }
      appendLine()
      appendLine("=== Back decision records ===")
      if (backDecisions.isEmpty()) {
        appendLine("none")
      } else {
        backDecisions.forEach { appendLine(it) }
      }
    }
  }

  @Synchronized
  private fun appendBounded(target: ArrayDeque<String>, line: String) {
    target.addFirst(line)
    while (target.size > maxEntries) {
      target.removeLast()
    }
  }

  private fun timestamp(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
  }
}
