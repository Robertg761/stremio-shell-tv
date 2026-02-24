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

    runtime.close()
  }

  @Test
  fun dispatchesActionsAndEmitsTypedEvents() = runBlocking {
    assumeTrue(JavaScriptSandbox.isSupported())

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val runtime = JsSandboxRuntimeHost(context)
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
        streamBase64 = "c3RyZWFtLXRlc3Q="
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
      assertTrue("Expected auth/library/playback events but got: $events", false)
    } finally {
      job.cancel()
      runtime.close()
    }

    assertTrue(events.any { it is AuthChangedEvent })
    assertTrue(events.any { it is LibraryChangedEvent })
    assertTrue(events.any { it is PlaybackProgressEvent })
  }

  @Test
  fun queriesCustomStateScopes() = runBlocking {
    assumeTrue(JavaScriptSandbox.isSupported())

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val runtime = JsSandboxRuntimeHost(context)
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
    assertTrue((searchData?.optJSONArray("results")?.length() ?: 0) > 0)

    runtime.close()
  }
}
