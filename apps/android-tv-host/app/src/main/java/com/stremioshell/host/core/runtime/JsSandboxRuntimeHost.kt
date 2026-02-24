package com.stremioshell.host.core.runtime

import android.content.Context
import android.util.Log
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import com.stremioshell.host.BuildConfig
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class JsSandboxRuntimeHost(
  context: Context,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CoreRuntimeClient {
  companion object {
    private const val TAG = "JsSandboxRuntimeHost"
    private const val RUNTIME_ASSET_PATH = "core-runtime/runtime.js"
    private const val WEB_INDEX_ASSET_PATH = "web/index.html"
    private val WORKER_SCRIPT_REGEX = Regex("""<script\s+src="([^"]+/scripts/worker\.js)"""")
  }

  private val appContext = context.applicationContext
  private val eventsFlow = MutableSharedFlow<CoreEvent>(replay = 0, extraBufferCapacity = 64)
  private val runtimeMutex = Mutex()

  private var sandbox: JavaScriptSandbox? = null
  private var isolate: JavaScriptIsolate? = null
  private var runtimeScriptLoaded = false
  private var coreInitCompleted = false

  override val events: Flow<CoreEvent> = eventsFlow.asSharedFlow()

  override suspend fun initializeRuntime(action: RuntimeInitializeAction) {
    // Phase 1: Kick off the WASM init (fire-and-forget, starts the Promise)
    val payload = JSONObject().put("action", action.toEnvelope().toJson())
    val response = callRuntime("__stremioRuntimeInit", payload)
    emitEvents(response)

    // Phase 2: Wait for WASM core to actually finish initializing
    val supportsPromiseReturn = sandbox?.isFeatureSupported(
      JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN
    ) == true
    Log.d(TAG, "initializeRuntime supportsPromiseReturn=$supportsPromiseReturn")

    if (supportsPromiseReturn) {
      // The sandbox can natively resolve Promises from evaluateJavaScriptAsync
      runtimeMutex.withLock {
        ensureRuntimeLoaded()
        val isolateRef = isolate ?: error("JS isolate is unavailable.")

        // Diagnostic: check JS environment state before awaiting
        val diagRaw = evaluateScript(isolateRef, """
          (() => JSON.stringify({
            hasFetch: typeof fetch === 'function',
            hasWebAssembly: typeof WebAssembly === 'object',
            hasWasmInstantiate: typeof WebAssembly !== 'undefined' && typeof WebAssembly.instantiate === 'function',
            hasInit: typeof globalThis.init === 'function',
            hasAwaitInit: typeof globalThis.__stremioAwaitInit === 'function',
            initStatus: typeof globalThis.__stremioRuntimeInitStatus === 'function' ? globalThis.__stremioRuntimeInitStatus() : 'no_status_fn',
            hasAndroid: typeof android !== 'undefined',
            hasConsume: typeof android !== 'undefined' && android && typeof android.consumeNamedDataAsArrayBuffer === 'function',
            documentBaseURI: typeof document !== 'undefined' && document ? document.baseURI : 'no_document',
            selfLocationHref: typeof self !== 'undefined' && self.location ? self.location.href : 'no_location'
          }))();
        """.trimIndent())
        Log.d(TAG, "initializeRuntime diag=$diagRaw")

        // Test: does JS_FEATURE_PROMISE_RETURN actually resolve?
        val simplePromiseTest = evaluateScript(isolateRef, """
          (async () => {
            const x = await Promise.resolve(42);
            return JSON.stringify({ resolved: true, value: x });
          })();
        """.trimIndent())
        Log.d(TAG, "initializeRuntime simplePromiseTest=$simplePromiseTest")

        // Test: does fetch() for the WASM binary work?
        val fetchTest = evaluateScript(isolateRef, """
          (async () => {
            try {
              const resp = await fetch('stremio_core_web_bg.wasm');
              const ok = resp && resp.ok;
              const buf = await resp.arrayBuffer();
              const byteLen = buf ? buf.byteLength : -1;
              const result = await WebAssembly.instantiate(buf, {});
              return JSON.stringify({ fetchOk: ok, byteLength: byteLen, instantiateOk: !!result });
            } catch (e) {
              return JSON.stringify({ error: String(e) });
            }
          })();
        """.trimIndent())
        Log.d(TAG, "initializeRuntime fetchTest=$fetchTest")

        val statusRaw = evaluateScript(isolateRef, "__stremioAwaitInit();")
        val statusJson = runCatching { JSONObject(statusRaw) }.getOrNull()
        val coreReady = statusJson?.optBoolean("coreReady", false) ?: false
        val status = statusJson?.optString("status")?.ifBlank { "unknown" } ?: "unknown"
        Log.d(TAG, "initializeRuntime awaited status=$status coreReady=$coreReady")
        coreInitCompleted = true
      }
    } else {
      // Fallback: poll __stremioRuntimeInitStatus between separate eval calls
      awaitCoreInit()
    }
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
    emitEvents(response)
    val snapshot = response.optJSONObject("snapshot") ?: response
    if (query.scope == "custom" && query.key == "catalog") {
      val catalog = snapshot.optJSONObject("data")
      val rowsCount = catalog?.optJSONArray("rows")?.length() ?: -1
      val featuredCount = catalog?.optJSONArray("featuredIds")?.length() ?: -1
      val coreReady = catalog?.optBoolean("coreReady", false) ?: false
      val initStartedAt = catalog?.optLong("initStartedAtMs", 0L) ?: 0L
      val initStatus = catalog?.optString("initStatus")?.ifBlank { "unknown" } ?: "unknown"
      val initErrorMessage = catalog?.optString("initErrorMessage")?.ifBlank { null }
      val dispatchSuccessCount = catalog?.optInt("dispatchSuccessCount", -1) ?: -1
      val dispatchFailureCount = catalog?.optInt("dispatchFailureCount", -1) ?: -1
      val lastDispatchError = catalog?.optString("lastDispatchError")?.ifBlank { null }
      val hasBoardState = catalog?.optBoolean("hasBoardState", false) ?: false
      val boardCatalogCount = catalog?.optInt("boardCatalogCount", -1) ?: -1
      Log.d(
        TAG,
        "catalog snapshot rows=$rowsCount featured=$featuredCount coreReady=$coreReady initStatus=$initStatus initStartedAt=$initStartedAt hasBoardState=$hasBoardState boardCatalogCount=$boardCatalogCount dispatchOk=$dispatchSuccessCount dispatchFail=$dispatchFailureCount lastDispatchError=${lastDispatchError ?: "none"} initError=${initErrorMessage ?: "none"}"
      )
    }
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
      coreInitCompleted = false
      runCatching { isolate?.close() }
      runCatching { sandbox?.close() }
      isolate = null
      sandbox = null
    }
  }

  private suspend fun awaitCoreInit() {
    val maxWaitMs = 60_000L
    val pollIntervalMs = 500L
    val startMs = System.currentTimeMillis()

    while (System.currentTimeMillis() - startMs < maxWaitMs) {
      runtimeMutex.withLock {
        ensureRuntimeLoaded()
        val isolateRef = isolate ?: error("JS isolate is unavailable.")
        val statusRaw = evaluateScript(isolateRef, "__stremioRuntimeInitStatus();")
        val statusJson = runCatching { JSONObject(statusRaw) }.getOrNull()
        val status = statusJson?.optString("status")?.ifBlank { "idle" } ?: "idle"
        val coreReady = statusJson?.optBoolean("coreReady", false) ?: false

        Log.d(TAG, "awaitCoreInit poll status=$status coreReady=$coreReady")

        if (coreReady || status == "ready") {
          coreInitCompleted = true
          Log.d(TAG, "awaitCoreInit completed successfully")
          return
        }
        if (status == "failed") {
          val errorMessage = statusJson?.optString("errorMessage")?.ifBlank { "unknown" } ?: "unknown"
          coreInitCompleted = true // Allow degraded operation
          Log.e(TAG, "awaitCoreInit core init failed: $errorMessage")
          return
        }
      }
      delay(pollIntervalMs)
    }

    coreInitCompleted = true // Allow degraded operation after timeout
    Log.e(TAG, "awaitCoreInit timed out after ${maxWaitMs}ms")
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

      val response = when (parsed) {
        is JSONObject -> parsed
        is JSONArray -> JSONObject().put("data", parsed)
        is String -> runCatching { JSONObject(parsed) }.getOrDefault(JSONObject().put("value", parsed))
        else -> JSONObject().put("value", normalized.toString())
      }
      val runtimeError = response.optString("error").ifBlank { null }
      if (runtimeError != null) {
        Log.w(TAG, "runtime call failed function=$functionName error=$runtimeError")
      }
      return response
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

    val isolateRef = isolate ?: error("JS isolate not available.")

    val workerAssetPath = resolveWorkerAssetPath()
    val workerSource = withContext(dispatcher) {
      appContext.assets.open(workerAssetPath).bufferedReader().use { it.readText() }
    }
    val runtimeSource = withContext(dispatcher) {
      appContext.assets.open(RUNTIME_ASSET_PATH).bufferedReader().use { it.readText() }
    }

    val wasmAssetPath = workerAssetPath
      .replace("/scripts/worker.js", "/binaries/stremio_core_web_bg.wasm")
    val wasmBytes = withContext(dispatcher) { appContext.assets.open(wasmAssetPath).use { it.readBytes() } }

    val wasmDataName = "stremio-wasm-${UUID.randomUUID()}"
    val supportsArrayBufferTransfer = sandbox?.isFeatureSupported(
      JavaScriptSandbox.JS_FEATURE_PROVIDE_CONSUME_ARRAY_BUFFER
    ) == true

    if (supportsArrayBufferTransfer) {
      isolateRef.provideNamedData(wasmDataName, wasmBytes)
    }

    val prelude = buildWorkerPrelude(
      workerAssetPath = workerAssetPath,
      wasmDataName = if (supportsArrayBufferTransfer) wasmDataName else null
    )

    evaluateScript(isolateRef, prelude)
    val envProbe = evaluateScript(
      isolateRef,
      """
        (() => JSON.stringify({
          hasFetch: typeof fetch === 'function',
          hasResponse: typeof Response === 'function',
          hasSetTimeout: typeof setTimeout === 'function',
          hasSetInterval: typeof setInterval === 'function',
          hasTextEncoder: typeof TextEncoder === 'function',
          hasTextDecoder: typeof TextDecoder === 'function'
        }))();
      """.trimIndent()
    )
    Log.d(TAG, "sandbox probe=$envProbe")
    evaluateScript(isolateRef, workerSource)
    evaluateScript(isolateRef, runtimeSource)
    runtimeScriptLoaded = true
  }

  private suspend fun evaluateScript(isolate: JavaScriptIsolate, script: String): String {
    return withContext(dispatcher) {
      try {
        isolate.evaluateJavaScriptAsync(script).get(30, TimeUnit.SECONDS)
      } catch (timeout: TimeoutException) {
        val preview = script.take(120).replace('\n', ' ')
        Log.e(TAG, "evaluateScript timeout preview=$preview")
        throw IllegalStateException("JavaScript evaluation timed out.", timeout)
      }
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

  private suspend fun resolveWorkerAssetPath(): String {
    val indexHtml = withContext(dispatcher) {
      appContext.assets.open(WEB_INDEX_ASSET_PATH).bufferedReader().use { it.readText() }
    }
    val workerScript = WORKER_SCRIPT_REGEX.find(indexHtml)?.groupValues?.getOrNull(1)
      ?: throw IllegalStateException("Unable to locate worker.js script path in web/index.html.")
    return "web/$workerScript"
  }

  private fun buildWorkerPrelude(workerAssetPath: String, wasmDataName: String?): String {
    val workerUrl = "https://stremio.local/$workerAssetPath"
    val versionName = BuildConfig.VERSION_NAME
    val quotedWorkerUrl = JSONObject.quote(workerUrl)
    val quotedVersionName = JSONObject.quote(versionName)
    val quotedWasmDataName = wasmDataName?.let { JSONObject.quote(it) } ?: "null"

    return """
      (() => {
        const workerUrl = $quotedWorkerUrl;
        const appVersion = $quotedVersionName;
        const wasmDataName = $quotedWasmDataName;

        if (typeof globalThis.WorkerGlobalScope === 'undefined') {
          globalThis.WorkerGlobalScope = class WorkerGlobalScope {};
        }
        try {
          Object.setPrototypeOf(globalThis, globalThis.WorkerGlobalScope.prototype);
        } catch (_error) {}

        globalThis.self = globalThis;
        globalThis.self.self = globalThis;
        const runtimeLocation = {
          href: workerUrl,
          hash: globalThis.location && typeof globalThis.location.hash === 'string' ? globalThis.location.hash : '',
          toString() {
            return this.href;
          }
        };
        globalThis.location = runtimeLocation;
        if (typeof globalThis.importScripts !== 'function') {
          globalThis.importScripts = function() {};
        }
        globalThis.document = {
          baseURI: workerUrl,
          currentScript: null,
          getElementsByTagName() {
            return [];
          }
        };
        if (typeof globalThis.setTimeout !== 'function') {
          let nextTimerId = 1;
          const timers = new Map();
          const scheduleTimer = function(callback, delay, repeating, args) {
            const timerId = nextTimerId++;
            const timeoutMs = Math.max(0, Number(delay) || 0);
            timers.set(timerId, {
              id: timerId,
              callback: callback,
              args: args,
              timeoutMs: timeoutMs,
              repeating: repeating,
              dueAt: Date.now() + timeoutMs
            });
            return timerId;
          };
          globalThis.setTimeout = function(callback, delay, ...args) {
            return scheduleTimer(callback, delay, false, args);
          };
          globalThis.clearTimeout = function(timerId) {
            timers.delete(timerId);
          };
          globalThis.setInterval = function(callback, delay, ...args) {
            return scheduleTimer(callback, delay, true, args);
          };
          globalThis.clearInterval = globalThis.clearTimeout;
          globalThis.__flushTimers = function() {
            const now = Date.now();
            const due = [];
            timers.forEach((timer, id) => {
              if (timer.dueAt <= now) {
                due.push(id);
              }
            });
            for (let index = 0; index < due.length; index += 1) {
              const timerId = due[index];
              const timer = timers.get(timerId);
              if (!timer) {
                continue;
              }
              if (!timer.repeating) {
                timers.delete(timerId);
              } else {
                timer.dueAt = now + Math.max(16, timer.timeoutMs);
                timers.set(timerId, timer);
              }
              if (typeof timer.callback === 'function') {
                try {
                  timer.callback(...timer.args);
                } catch (_error) {}
              }
            }
          };
        }
        if (typeof globalThis.URL !== 'function') {
          globalThis.URL = function(input, base) {
            const value = String(input || '');
            const baseValue = String(base || workerUrl);
            const absolutePattern = /^[A-Za-z][A-Za-z0-9+.-]*:/;
            if (absolutePattern.test(value)) {
              this.href = value;
            } else if (value.startsWith('/')) {
              const protocolIndex = baseValue.indexOf('://');
              const pathStart = protocolIndex >= 0
                ? baseValue.indexOf('/', protocolIndex + 3)
                : -1;
              const origin = pathStart >= 0 ? baseValue.substring(0, pathStart) : baseValue;
              this.href = origin + value;
            } else {
              const lastSlash = baseValue.lastIndexOf('/');
              const prefix = lastSlash >= 0 ? baseValue.substring(0, lastSlash + 1) : (baseValue + '/');
              this.href = prefix + value;
            }
          };
          globalThis.URL.prototype.toString = function() {
            return this.href;
          };
        }
        if (typeof globalThis.TextEncoder !== 'function') {
          globalThis.TextEncoder = class TextEncoder {
            constructor() {
              this.encoding = 'utf-8';
            }
            encode(input) {
              const source = String(input == null ? '' : input);
              const utf8 = unescape(encodeURIComponent(source));
              const output = new Uint8Array(utf8.length);
              for (let index = 0; index < utf8.length; index += 1) {
                output[index] = utf8.charCodeAt(index);
              }
              return output;
            }
            encodeInto(input, destination) {
              const bytes = this.encode(input);
              const writable = Math.min(bytes.length, destination.length || 0);
              for (let index = 0; index < writable; index += 1) {
                destination[index] = bytes[index];
              }
              return { read: String(input == null ? '' : input).length, written: writable };
            }
          };
        }
        if (typeof globalThis.TextDecoder !== 'function') {
          globalThis.TextDecoder = class TextDecoder {
            constructor(_label, _options) {
              this.encoding = 'utf-8';
            }
            decode(input) {
              if (!input) {
                return '';
              }
              let view;
              if (input instanceof Uint8Array) {
                view = input;
              } else if (typeof ArrayBuffer !== 'undefined' && input instanceof ArrayBuffer) {
                view = new Uint8Array(input);
              } else if (typeof ArrayBuffer !== 'undefined' && ArrayBuffer.isView && ArrayBuffer.isView(input)) {
                view = new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
              } else {
                return String(input);
              }
              let binary = '';
              for (let index = 0; index < view.length; index += 1) {
                binary += String.fromCharCode(view[index]);
              }
              try {
                return decodeURIComponent(escape(binary));
              } catch (_error) {
                return binary;
              }
            }
          };
        }
        if (typeof globalThis.Response !== 'function') {
          const toArrayBuffer = function(value) {
            if (value instanceof ArrayBuffer) {
              return value;
            }
            if (typeof ArrayBuffer !== 'undefined' && ArrayBuffer.isView && ArrayBuffer.isView(value)) {
              const view = value;
              return view.byteOffset === 0 && view.byteLength === view.buffer.byteLength
                ? view.buffer
                : view.buffer.slice(view.byteOffset, view.byteOffset + view.byteLength);
            }
            if (value == null) {
              return new Uint8Array(0).buffer;
            }
            const encoded = new TextEncoder().encode(String(value));
            return encoded.buffer;
          };
          const toText = function(value) {
            if (typeof value === 'string') {
              return value;
            }
            return new TextDecoder('utf-8').decode(toArrayBuffer(value));
          };
          class PolyfillHeaders {
            constructor(init) {
              this.values = Object.create(null);
              if (!init || typeof init !== 'object') {
                return;
              }
              const keys = Object.keys(init);
              for (let index = 0; index < keys.length; index += 1) {
                this.values[String(keys[index]).toLowerCase()] = String(init[keys[index]]);
              }
            }
            get(name) {
              return this.values[String(name).toLowerCase()] || null;
            }
          }
          class PolyfillResponse {
            constructor(body, init) {
              const options = init || {};
              this._body = body;
              this.status = Number(options.status || 200);
              this.ok = this.status >= 200 && this.status < 300;
              this.headers = new PolyfillHeaders(options.headers || {});
            }
            arrayBuffer() {
              return Promise.resolve(toArrayBuffer(this._body));
            }
            text() {
              return Promise.resolve(toText(this._body));
            }
          }
          if (typeof globalThis.Headers !== 'function') {
            globalThis.Headers = PolyfillHeaders;
          }
          globalThis.Response = PolyfillResponse;
        }
        if (typeof globalThis.WebAssembly === 'object' && typeof globalThis.WebAssembly.instantiateStreaming === 'function') {
          globalThis.WebAssembly.instantiateStreaming = undefined;
        }
        if (typeof globalThis.WebAssembly === 'object') {
          const nativeInstantiate = globalThis.WebAssembly.instantiate;
          globalThis.WebAssembly.instantiate = function(bufferOrModule, imports) {
            try {
              if (bufferOrModule instanceof ArrayBuffer || (typeof ArrayBuffer !== 'undefined' && ArrayBuffer.isView && ArrayBuffer.isView(bufferOrModule))) {
                const bytes = bufferOrModule instanceof ArrayBuffer ? bufferOrModule : bufferOrModule.buffer.slice(bufferOrModule.byteOffset, bufferOrModule.byteOffset + bufferOrModule.byteLength);
                const module = new WebAssembly.Module(bytes);
                const instance = new WebAssembly.Instance(module, imports || {});
                return Promise.resolve({ module: module, instance: instance });
              }
              if (bufferOrModule instanceof WebAssembly.Module) {
                const instance = new WebAssembly.Instance(bufferOrModule, imports || {});
                return Promise.resolve(instance);
              }
            } catch (syncError) {
              if (typeof console !== 'undefined' && typeof console.error === 'function') {
                console.error('Sync WebAssembly.instantiate failed, falling back to native:', syncError);
              }
            }
            if (typeof nativeInstantiate === 'function') {
              return nativeInstantiate.call(WebAssembly, bufferOrModule, imports);
            }
            return Promise.reject(new Error('WebAssembly.instantiate is unavailable'));
          };
        }
        if (typeof globalThis.performance !== 'object' || !globalThis.performance) {
          const marks = Object.create(null);
          globalThis.performance = {
            now() {
              return Date.now();
            },
            mark(name) {
              marks[String(name)] = Date.now();
            },
            measure(name, startMark, endMark) {
              const start = marks[String(startMark)] || Date.now();
              const end = endMark && marks[String(endMark)] ? marks[String(endMark)] : Date.now();
              return {
                name: String(name),
                startTime: start,
                duration: Math.max(0, end - start)
              };
            }
          };
        }
        globalThis.navigator = globalThis.navigator || { language: 'en-US' };
        globalThis.app_version = appVersion;
        globalThis.shell_version = appVersion;

        const localStore = new Map();
        globalThis.localStorage = {
          getItem(key) {
            return localStore.has(String(key)) ? localStore.get(String(key)) : null;
          },
          setItem(key, value) {
            localStore.set(String(key), String(value));
          },
          removeItem(key) {
            localStore.delete(String(key));
          }
        };
        globalThis.get_location_hash = async () => globalThis.location.hash || '';
        globalThis.local_storage_get_item = async (key) => globalThis.localStorage.getItem(key);
        globalThis.local_storage_set_item = async (key, value) => {
          globalThis.localStorage.setItem(key, value);
          return null;
        };
        globalThis.local_storage_remove_item = async (key) => {
          globalThis.localStorage.removeItem(key);
          return null;
        };

        const nativeAddEventListener = typeof globalThis.addEventListener === 'function'
          ? globalThis.addEventListener.bind(globalThis)
          : null;
        const nativeRemoveEventListener = typeof globalThis.removeEventListener === 'function'
          ? globalThis.removeEventListener.bind(globalThis)
          : null;
        const messageListeners = [];

        globalThis.addEventListener = function(type, callback, options) {
          if (type === 'message' && typeof callback === 'function') {
            messageListeners.push(callback);
            return;
          }
          if (nativeAddEventListener) {
            nativeAddEventListener(type, callback, options);
          }
        };

        globalThis.removeEventListener = function(type, callback, options) {
          if (type === 'message') {
            const index = messageListeners.indexOf(callback);
            if (index >= 0) {
              messageListeners.splice(index, 1);
            }
            return;
          }
          if (nativeRemoveEventListener) {
            nativeRemoveEventListener(type, callback, options);
          }
        };

        globalThis.postMessage = function(payload) {
          const event = { data: payload };
          const listeners = messageListeners.slice();
          for (let index = 0; index < listeners.length; index += 1) {
            try {
              listeners[index](event);
            } catch (error) {
              if (typeof console !== 'undefined' && typeof console.error === 'function') {
                console.error(error);
              }
            }
          }
        };

        if (wasmDataName && typeof android !== 'undefined' && android && typeof android.consumeNamedDataAsArrayBuffer === 'function') {
          const nativeFetch = typeof globalThis.fetch === 'function' ? globalThis.fetch.bind(globalThis) : null;
          const normalizeBuffer = function(value) {
            if (value instanceof ArrayBuffer) {
              return value;
            }
            if (typeof ArrayBuffer !== 'undefined' && ArrayBuffer.isView && ArrayBuffer.isView(value)) {
              const view = value;
              return view.byteOffset === 0 && view.byteLength === view.buffer.byteLength
                ? view.buffer
                : view.buffer.slice(view.byteOffset, view.byteOffset + view.byteLength);
            }
            if (value && typeof value.length === 'number') {
              const length = Number(value.length) || 0;
              const bytes = new Uint8Array(length);
              for (let index = 0; index < length; index += 1) {
                bytes[index] = Number(value[index]) & 0xff;
              }
              return bytes.buffer;
            }
            throw new Error('Unsupported wasm buffer payload type');
          };
          const wasmBytesPromise = Promise
            .resolve(android.consumeNamedDataAsArrayBuffer(wasmDataName))
            .then((value) => normalizeBuffer(value));
          const createWasmResponse = function(buffer) {
            const normalized = normalizeBuffer(buffer);
            if (typeof Response === 'function') {
              return new Response(normalized, {
                status: 200,
                headers: { 'Content-Type': 'application/wasm' }
              });
            }
            return {
              ok: true,
              status: 200,
              headers: {
                get(name) {
                  return String(name).toLowerCase() === 'content-type' ? 'application/wasm' : null;
                }
              },
              arrayBuffer() {
                return Promise.resolve(normalized);
              }
            };
          };

          globalThis.fetch = function(input, init) {
            const url = typeof input === 'string'
              ? input
              : (input && (input.url || input.href) ? (input.url || input.href) : '');
            if (url && url.indexOf('stremio_core_web_bg.wasm') >= 0) {
              return wasmBytesPromise.then((buffer) => createWasmResponse(buffer));
            }
            if (nativeFetch) {
              return nativeFetch(input, init);
            }
            return Promise.reject(new Error('fetch is unavailable'));
          };
        }
      })();
    """.trimIndent()
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
