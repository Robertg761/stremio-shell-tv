package com.stremioshell.host.core.runtime

import android.content.Context
import android.util.Log
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import com.stremioshell.host.core.CORE_CONTRACT_VERSION
import com.stremioshell.host.core.CoreAction
import com.stremioshell.host.core.CoreEnvelopeParser
import com.stremioshell.host.core.CoreEvent
import com.stremioshell.host.core.CoreRuntimeClient
import com.stremioshell.host.core.CoreStateQuery
import com.stremioshell.host.core.CoreStateSnapshot
import com.stremioshell.host.core.RuntimeInitializeAction
import com.stremioshell.host.core.TelemetryEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class JsSandboxRuntimeHost(
  context: Context,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CoreRuntimeClient {
  companion object {
    private const val TAG = "JsSandboxRuntimeHost"
    private const val RUNTIME_ASSET_PATH = "core-runtime/runtime.js"
  }

  private val appContext = context.applicationContext
  private val eventsFlow = MutableSharedFlow<CoreEvent>(replay = 0, extraBufferCapacity = 64)
  private val runtimeMutex = Mutex()

  private var sandbox: JavaScriptSandbox? = null
  private var isolate: JavaScriptIsolate? = null
  private var runtimeScriptLoaded = false

  override val events: Flow<CoreEvent> = eventsFlow.asSharedFlow()

  override suspend fun initializeRuntime(action: RuntimeInitializeAction) {
    val payload = JSONObject().put("action", action.toEnvelope().toJson())
    val response = callRuntime("__stremioRuntimeInit", payload)
    emitEvents(response)
  }

  override suspend fun dispatch(
    action: CoreAction,
    field: Map<String, Any?>?,
    locationHash: String
  ) {
    val payload = JSONObject()
      .put("action", action.toEnvelope().toJson())
      .put("locationHash", locationHash)

    if (field != null) {
      payload.put("field", mapToJson(field))
    }

    val response = callRuntime("__stremioRuntimeDispatch", payload)
    emitEvents(response)
  }

  override suspend fun getState(query: CoreStateQuery): CoreStateSnapshot {
    val response = callRuntime("__stremioRuntimeGetState", query.toJson())
    val snapshot = response.optJSONObject("snapshot") ?: response
    return CoreStateSnapshot(
      scope = snapshot.optString("scope").ifBlank { query.scope },
      version = snapshot.optInt("version", CORE_CONTRACT_VERSION),
      updatedAtMs = snapshot.optLong("updatedAtMs", System.currentTimeMillis()),
      data = snapshot.opt("data")
    )
  }

  override suspend fun analytics(event: TelemetryEvent, locationHash: String) {
    val payload = JSONObject()
      .put("event", event.toEnvelope().toJson())
      .put("locationHash", locationHash)

    val response = callRuntime("__stremioRuntimeAnalytics", payload)
    emitEvents(response)
  }

  override suspend fun decodeStream(streamBase64: String): Any? {
    val response = callRuntime(
      "__stremioRuntimeDecodeStream",
      JSONObject().put("streamBase64", streamBase64)
    )
    return response.opt("decoded")
  }

  override suspend fun close() {
    runtimeMutex.withLock {
      runtimeScriptLoaded = false
      runCatching { isolate?.close() }
      runCatching { sandbox?.close() }
      isolate = null
      sandbox = null
    }
  }

  private suspend fun callRuntime(functionName: String, payload: JSONObject): JSONObject {
    runtimeMutex.withLock {
      ensureRuntimeLoaded()
      val isolateRef = isolate ?: error("JS isolate is unavailable.")

      val script = """
        (() => {
          try {
            const response = $functionName(${payload.toString()});
            if (typeof response === 'string') {
              return response;
            }
            return JSON.stringify(response ?? {});
          } catch (error) {
            return JSON.stringify({ error: String(error) });
          }
        })();
      """.trimIndent()

      val raw = evaluateScript(isolateRef, script)
      val normalized = normalizeJsResult(raw)
      val parsed = runCatching { JSONTokener(normalized).nextValue() }.getOrNull()

      return when (parsed) {
        is JSONObject -> parsed
        is JSONArray -> JSONObject().put("data", parsed)
        is String -> runCatching { JSONObject(parsed) }.getOrDefault(JSONObject().put("value", parsed))
        else -> JSONObject().put("value", normalized.toString())
      }
    }
  }

  private suspend fun ensureRuntimeLoaded() {
    if (runtimeScriptLoaded) {
      return
    }
    if (!JavaScriptSandbox.isSupported()) {
      throw IllegalStateException("JavaScriptSandbox is not supported on this device.")
    }

    if (sandbox == null) {
      sandbox = withContext(dispatcher) {
        JavaScriptSandbox.createConnectedInstanceAsync(appContext).get()
      }
    }
    if (isolate == null) {
      isolate = sandbox?.createIsolate()
    }

    val runtimeSource = withContext(dispatcher) {
      appContext.assets.open(RUNTIME_ASSET_PATH).bufferedReader().use { it.readText() }
    }

    evaluateScript(isolate ?: error("JS isolate not available."), runtimeSource)
    runtimeScriptLoaded = true
  }

  private suspend fun evaluateScript(isolate: JavaScriptIsolate, script: String): String {
    return withContext(dispatcher) {
      isolate.evaluateJavaScriptAsync(script).get()
    }
  }

  private fun normalizeJsResult(rawResult: String): String {
    val parsed = runCatching { JSONTokener(rawResult).nextValue() }.getOrNull()
    return when (parsed) {
      is String -> parsed
      is JSONObject -> parsed.toString()
      is JSONArray -> parsed.toString()
      null -> rawResult
      else -> parsed.toString()
    }
  }

  private suspend fun emitEvents(response: JSONObject) {
    val error = response.optString("error").ifBlank { null }
    if (error != null) {
      Log.w(TAG, "Runtime function returned error=$error")
    }

    val events = response.optJSONArray("events") ?: return
    for (index in 0 until events.length()) {
      val event = events.optJSONObject(index) ?: continue
      eventsFlow.emit(CoreEnvelopeParser.parseEvent(event))
    }
  }

  private fun mapToJson(value: Map<String, Any?>): JSONObject {
    return JSONObject().apply {
      value.forEach { (key, item) ->
        put(key, anyToJson(item))
      }
    }
  }

  private fun anyToJson(value: Any?): Any? {
    return when (value) {
      null -> JSONObject.NULL
      is Boolean,
      is Int,
      is Long,
      is Float,
      is Double,
      is String,
      is JSONObject,
      is JSONArray -> value
      is Map<*, *> -> {
        val json = JSONObject()
        value.forEach { (key, item) ->
          if (key is String) {
            json.put(key, anyToJson(item))
          }
        }
        json
      }
      is Iterable<*> -> {
        JSONArray().apply {
          value.forEach { put(anyToJson(it)) }
        }
      }
      else -> value.toString()
    }
  }
}
