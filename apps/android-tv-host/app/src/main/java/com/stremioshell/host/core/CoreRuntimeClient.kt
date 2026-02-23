package com.stremioshell.host.core

import kotlinx.coroutines.flow.Flow

interface CoreRuntimeClient {
  val events: Flow<CoreEvent>

  suspend fun initializeRuntime(action: RuntimeInitializeAction = RuntimeInitializeAction())

  suspend fun dispatch(
    action: CoreAction,
    field: Map<String, Any?>? = null,
    locationHash: String = ""
  )

  suspend fun getState(query: CoreStateQuery): CoreStateSnapshot

  suspend fun analytics(
    event: TelemetryEvent,
    locationHash: String = ""
  )

  suspend fun decodeStream(streamBase64: String): Any?

  suspend fun close()
}
