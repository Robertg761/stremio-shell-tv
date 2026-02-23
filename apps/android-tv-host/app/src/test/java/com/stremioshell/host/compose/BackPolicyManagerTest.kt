package com.stremioshell.host.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class BackPolicyManagerTest {
  private val manager = BackPolicyManager()

  @Test
  fun `overlay takes precedence`() {
    val decision = manager.resolveDecision(hasOpenOverlay = true, canPopRoute = true)
    assertEquals(BackPolicyManager.BackDecision.CLOSE_OVERLAY, decision)
  }

  @Test
  fun `pops route when no overlay and back stack available`() {
    val decision = manager.resolveDecision(hasOpenOverlay = false, canPopRoute = true)
    assertEquals(BackPolicyManager.BackDecision.POP_ROUTE, decision)
  }

  @Test
  fun `exits app at root`() {
    val decision = manager.resolveDecision(hasOpenOverlay = false, canPopRoute = false)
    assertEquals(BackPolicyManager.BackDecision.EXIT_APP, decision)
  }
}
