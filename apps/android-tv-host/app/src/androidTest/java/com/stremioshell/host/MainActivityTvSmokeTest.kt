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
  fun launchesAndInitializesWebShell() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertNotNull(activity)
        assertTrue(!activity.isFinishing)
      }
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
      }
    }
  }
}
