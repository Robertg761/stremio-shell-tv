package com.stremioshell.host

import android.webkit.WebView
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
  fun launchesAndInitializesWebShell() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      assertTrue(
        "Expected focused bundled WebView shell to load.",
        scenario.waitForActivityState { activity ->
          val webView = activity.findViewById<WebView>(R.id.webView)
          !activity.isFinishing &&
            webView.hasFocus() &&
            webView.url.orEmpty().startsWith(BUNDLED_WEB_URL_PREFIX)
        }
      )
    }
  }

  @Test
  fun backPressHandledWithoutCrash() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        activity.onBackPressedDispatcher.onBackPressed()
      }

      InstrumentationRegistry.getInstrumentation().waitForIdleSync()

      scenario.onActivity { activity ->
        assertNotNull(activity)
        assertNotNull(activity.findViewById<WebView>(R.id.webView))
      }
    }
  }

  private fun ActivityScenario<MainActivity>.waitForActivityState(
    timeoutMs: Long = 15_000L,
    predicate: (MainActivity) -> Boolean
  ): Boolean {
    val deadlineMs = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadlineMs) {
      var matches = false
      onActivity { activity ->
        matches = predicate(activity)
      }
      if (matches) {
        return true
      }
      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      Thread.sleep(250L)
    }
    return false
  }

  companion object {
    private const val BUNDLED_WEB_URL_PREFIX = "https://appassets.androidplatform.net/assets/web/"
  }
}
