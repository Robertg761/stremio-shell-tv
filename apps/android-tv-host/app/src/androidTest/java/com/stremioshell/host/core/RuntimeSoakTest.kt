package com.stremioshell.host.core

import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stremioshell.host.core.runtime.JsSandboxRuntimeHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeSoakTest {
  @Ignore("Manual gate: run on API 26 and API 34 devices for the full 30-minute soak.")
  @Test
  fun thirtyMinuteRuntimeSoak() = runBlocking {
    assumeTrue(JavaScriptSandbox.isSupported())

    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val runtime = JsSandboxRuntimeHost(context)

    runtime.initializeRuntime(RuntimeInitializeAction(source = "soak-test"))
    val startMs = System.currentTimeMillis()

    while ((System.currentTimeMillis() - startMs) < 30 * 60 * 1000L) {
      runtime.dispatch(LibrarySyncAction(force = true))
      runtime.dispatch(
        PlaybackReportProgressAction(
          streamId = "soak-stream",
          progressMs = (System.currentTimeMillis() - startMs)
        )
      )
      runtime.getState(CoreStateQuery(scope = "session"))
      runtime.getState(CoreStateQuery(scope = "library"))
      runtime.getState(CoreStateQuery(scope = "player"))
      delay(2_000L)
    }

    runtime.close()
  }
}
