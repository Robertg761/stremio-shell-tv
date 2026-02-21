package com.stremioshell.host.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
  @Test
  fun `parseOrNull accepts flavor suffixed version strings`() {
    val parsed = SemVer.parseOrNull("0.1.0-mobile")
    assertNotNull(parsed)
    assertEquals(0, parsed?.major)
    assertEquals(1, parsed?.minor)
    assertEquals(0, parsed?.patch)
  }

  @Test
  fun `compareTo handles semantic ordering`() {
    val current = SemVer.parseOrNull("0.1.0-tv")
    val latest = SemVer.parseOrNull("v0.1.1")
    assertNotNull(current)
    assertNotNull(latest)
    assertTrue(latest!! > current!!)
  }
}
