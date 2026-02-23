package com.stremioshell.host

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTvSmokeTest {
  @Test
  fun launchesAndInitializesWebView() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val webView = activity.findViewById<android.webkit.WebView>(R.id.webView)
        assertNotNull(webView)
        assertTrue(webView.isFocusable)
      }
    }
  }

  @Test
  fun backPressFallsBackToHostExitWhenWebDoesNotAck() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        activity.onBackPressedDispatcher.onBackPressed()
      }

      // Back handling waits for a short web-ack window before host fallback.
      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      Thread.sleep(240L)

      scenario.onActivity { activity ->
        assertTrue(activity.isFinishing || activity.isDestroyed)
      }
    }
  }
}
