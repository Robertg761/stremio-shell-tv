package com.stremioshell.host.compose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiParityScreenshotTest {
  @Test
  fun boardHomeLooksCloseToReleaseOracle() {
    ActivityScenario.launch(ComposeMainActivity::class.java).use {
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.waitForIdleSync()
      Thread.sleep(1200L)

      val screenshot = instrumentation.uiAutomation.takeScreenshot()
      assertNotNull("Could not capture screenshot.", screenshot)

      val targetContext = instrumentation.targetContext
      val oracle = targetContext.assets.open("oracle/release-build-home.png").use {
        BitmapFactory.decodeStream(it)
      }
      assertNotNull("Could not decode oracle image.", oracle)

      persistScreenshot(screenshot!!, targetContext.cacheDir)

      val diff = normalizedMeanDiff(
        first = screenshot,
        second = oracle!!,
        width = 64,
        height = 36
      )

      assertTrue(
        "UI diff is too high against release oracle. diff=$diff",
        diff < 95.0
      )
    }
  }

  private fun persistScreenshot(bitmap: Bitmap, cacheDir: File) {
    val output = File(cacheDir, "parity-board-home.png")
    output.outputStream().use { stream ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
  }

  private fun normalizedMeanDiff(first: Bitmap, second: Bitmap, width: Int, height: Int): Double {
    val a = Bitmap.createScaledBitmap(first, width, height, true)
    val b = Bitmap.createScaledBitmap(second, width, height, true)

    var total = 0L
    val samples = width * height

    for (y in 0 until height) {
      for (x in 0 until width) {
        val ap = a.getPixel(x, y)
        val bp = b.getPixel(x, y)

        val dr = abs(android.graphics.Color.red(ap) - android.graphics.Color.red(bp))
        val dg = abs(android.graphics.Color.green(ap) - android.graphics.Color.green(bp))
        val db = abs(android.graphics.Color.blue(ap) - android.graphics.Color.blue(bp))

        total += (dr + dg + db) / 3L
      }
    }

    return total.toDouble() / samples.toDouble()
  }
}
