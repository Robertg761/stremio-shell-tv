package com.stremioshell.host.core.runtime

import com.stremioshell.host.core.CoreEvent
import com.stremioshell.host.core.CoreRuntimeClient
import com.stremioshell.host.core.CoreStateQuery
import com.stremioshell.host.core.CoreStateSnapshot
import com.stremioshell.host.core.RuntimeInitializeAction
import com.stremioshell.host.core.TelemetryEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class NoopRuntimeHost : CoreRuntimeClient {
  private val eventsFlow = MutableSharedFlow<CoreEvent>(replay = 0, extraBufferCapacity = 8)

  override val events: Flow<CoreEvent> = eventsFlow

  override suspend fun initializeRuntime(action: RuntimeInitializeAction) = Unit

  override suspend fun dispatch(
    action: com.stremioshell.host.core.CoreAction,
    field: Map<String, Any?>?,
    locationHash: String
  ) = Unit

  override suspend fun getState(query: CoreStateQuery): CoreStateSnapshot {
    return CoreStateSnapshot(
      scope = query.scope,
      version = 1,
      updatedAtMs = System.currentTimeMillis(),
      data = emptyMap<String, Any?>()
    )
  }

  override suspend fun analytics(event: TelemetryEvent, locationHash: String) = Unit

  override suspend fun decodeStream(streamBase64: String): Any? {
    return mapOf("streamBase64" to streamBase64)
  }

  override suspend fun close() = Unit
}
