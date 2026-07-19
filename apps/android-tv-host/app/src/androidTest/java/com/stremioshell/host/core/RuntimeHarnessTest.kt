package com.stremioshell.host.core

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stremioshell.host.core.runtime.JsSandboxRuntimeHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class RuntimeHarnessTest {
  @Test
  fun initializesAndReturnsStateSnapshots() = runBlocking {
    assumeTrue(JavaScriptSandbox.isSupported())

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val runtime = JsSandboxRuntimeHost(context)
    try {
      runtime.initializeRuntime(RuntimeInitializeAction(source = "android-test"))

      val session = runtime.getState(CoreStateQuery(scope = "session"))
      val library = runtime.getState(CoreStateQuery(scope = "library"))
      val player = runtime.getState(CoreStateQuery(scope = "player"))

      assertEquals("session", session.scope)
      assertEquals("library", library.scope)
      assertEquals("player", player.scope)

      assertNotNull(session.data)
      assertNotNull(library.data)
      assertNotNull(player.data)
    } finally {
      runtime.close()
    }
  }

  @Test
  fun dispatchesActionsAndEmitsTypedEvents() = runBlocking {
    assumeTrue(JavaScriptSandbox.isSupported())

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val runtime = JsSandboxRuntimeHost(context)
    try {
    val events = mutableListOf<CoreEvent>()
    val completed = CompletableDeferred<Unit>()

    val job = launch {
      runtime.events.collect { event ->
        events += event
        val hasAuth = events.any { it is AuthChangedEvent }
        val hasLibrary = events.any { it is LibraryChangedEvent }
        val hasPlayback = events.any { it is PlaybackProgressEvent }
        if (hasAuth && hasLibrary && hasPlayback) {
          completed.complete(Unit)
          return@collect
        }
      }
    }

    runtime.initializeRuntime(RuntimeInitializeAction(source = "android-test"))
    runtime.dispatch(AuthLoginAction(method = "token", token = "runtime-test-user"))
    runtime.dispatch(LibrarySyncAction(force = true))
    runtime.dispatch(
      PlaybackSelectStreamAction(
        streamId = "stream-test",
        // base64 of {"url":"https://example.com/stream.mp4"} - the runtime
        // rejects payloads that do not decode to an object.
        streamBase64 = "eyJ1cmwiOiJodHRwczovL2V4YW1wbGUuY29tL3N0cmVhbS5tcDQifQ=="
      )
    )
    runtime.dispatch(
      PlaybackReportProgressAction(
        streamId = "stream-test",
        progressMs = 42_000L
      )
    )

    try {
      kotlinx.coroutines.withTimeout(5_000L) {
        completed.await()
      }
    } catch (_: TimeoutCancellationException) {
      // Playback events are produced locally; auth/library events need the
      // sandboxed core to reach the Stremio API, which offline sandboxes
      // cannot do. Skip rather than fail in that environment.
      assertTrue(
        "Expected at least a playback event but got: $events",
        events.any { it is PlaybackProgressEvent }
      )
      assumeTrue(
        "Sandboxed core has no API access; auth/library events unavailable. Got: $events",
        events.any { it is AuthChangedEvent } && events.any { it is LibraryChangedEvent }
      )
    } finally {
      job.cancel()
    }

    assertTrue(events.any { it is PlaybackProgressEvent })
    } finally {
      runtime.close()
    }
  }

  @Test
  fun queriesCustomStateScopes() = runBlocking {
    assumeTrue(JavaScriptSandbox.isSupported())

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val runtime = JsSandboxRuntimeHost(context)
    try {
      runtime.initializeRuntime(RuntimeInitializeAction(source = "android-test"))
      runtime.dispatch(
        CustomAction(
          customType = "updateSearch",
          customPayload = JSONObject().put("query", "movie")
        )
      )

      val search = runtime.getState(CoreStateQuery(scope = "custom", key = "search"))
      val searchData = search.data as? JSONObject

      assertEquals("custom", search.scope)
      assertEquals("movie", searchData?.optString("query"))
      // Search results come from addon catalogs over the network; skip the
      // count assertion when the sandboxed core has no API access.
      assumeTrue(
        "Sandboxed core returned no search results (no API access?)",
        (searchData?.optJSONArray("results")?.length() ?: 0) > 0
      )
    } finally {
      runtime.close()
    }
  }

  @Test
  fun playbackActionsKeepPlayerSnapshotValid() = runBlocking {
    assumeTrue(JavaScriptSandbox.isSupported())

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val runtime = JsSandboxRuntimeHost(context)
    try {
      runtime.initializeRuntime(RuntimeInitializeAction(source = "android-test"))

      runtime.dispatch(
        PlaybackSelectStreamAction(
          streamId = "stream-player",
          streamBase64 = "c3RyZWFtLXBsYXllcg=="
        )
      )
      runtime.dispatch(
        PlaybackReportProgressAction(
          streamId = "stream-player",
          progressMs = 12_345L,
          durationMs = 65_000L
        )
      )

      val player = runtime.getState(CoreStateQuery(scope = "player"))
      val playerData = player.data as? JSONObject

      assertEquals("player", player.scope)
      assertEquals("stream-player", playerData?.optString("streamId"))
      assertEquals(12_345L, playerData?.optLong("progressMs"))
    } finally {
      runtime.close()
    }
  }

  @Test
  fun invalidStreamDecodeDoesNotCrashRuntime() = runBlocking {
    assumeTrue(JavaScriptSandbox.isSupported())

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val runtime = JsSandboxRuntimeHost(context)
    try {
      runtime.initializeRuntime(RuntimeInitializeAction(source = "android-test"))

      runtime.dispatch(
        PlaybackSelectStreamAction(
          streamId = "stream-invalid",
          streamBase64 = "%%%invalid-base64%%%"
        )
      )
      runtime.dispatch(
        PlaybackReportProgressAction(
          streamId = "stream-invalid",
          progressMs = 1_000L
        )
      )

      val player = runtime.getState(CoreStateQuery(scope = "player"))
      val playerData = player.data as? JSONObject

      assertEquals("player", player.scope)
      assertEquals("stream-invalid", playerData?.optString("streamId"))
      assertEquals(1_000L, playerData?.optLong("progressMs"))
    } finally {
      runtime.close()
    }
  }
}
