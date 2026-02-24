package com.stremioshell.host.compose

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeDeepLinkRoutingTest {
  @Test
  fun resolvesSearchDeepLinkRoute() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val intent = Intent(context, ComposeMainActivity::class.java).apply {
      action = Intent.ACTION_VIEW
      data = Uri.parse("stremio-shell://search?q=matrix")
    }

    ActivityScenario.launch<ComposeMainActivity>(intent).use { scenario ->
      scenario.onActivity { activity ->
        assertEquals("search", activity.getLastResolvedDeepLinkRoute())
      }
    }
  }

  @Test
  fun resolvesPlayerDeepLinkRouteWithUrl() {
    val deepLink = "stremio-shell://player?url=https%3A%2F%2Fcdn.example%2Fstream.m3u8"
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val intent = Intent(context, ComposeMainActivity::class.java).apply {
      action = Intent.ACTION_VIEW
      data = Uri.parse(deepLink)
    }

    ActivityScenario.launch<ComposeMainActivity>(intent).use { scenario ->
      scenario.onActivity { activity ->
        assertEquals(
          "player?url=https%3A%2F%2Fcdn.example%2Fstream.m3u8",
          activity.getLastResolvedDeepLinkRoute()
        )
      }
    }
  }
}
