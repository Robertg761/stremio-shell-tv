package com.stremioshell.host.core.mapper

import com.stremioshell.host.core.CoreEvent
import com.stremioshell.host.core.LibraryChangedEvent
import com.stremioshell.host.core.PlaybackProgressEvent
import com.stremioshell.host.core.RuntimeErrorEvent

fun CoreEvent.toDiagnosticsLine(): String {
  return when (this) {
    is RuntimeErrorEvent -> "runtime.error code=$code recoverable=$recoverable message=$message"
    is LibraryChangedEvent -> "library.changed itemCount=${itemCount ?: 0} reason=${reason ?: "unknown"}"
    is PlaybackProgressEvent -> "playback.progress streamId=$streamId progressMs=$progressMs"
    else -> envelope.type
  }
}
