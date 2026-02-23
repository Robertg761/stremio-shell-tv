package com.stremioshell.host.core

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stremioshell.host.core.runtime.JsSandboxRuntimeHost
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

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
}
