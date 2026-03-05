package com.stremioshell.host

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostEventQueuePolicyTest {
  @Test
  fun `does not queue back pressed events`() {
    assertFalse(HostEventQueuePolicy.shouldQueue("back.pressed"))
  }

  @Test
  fun `queues non back events`() {
    assertTrue(HostEventQueuePolicy.shouldQueue("network.changed"))
    assertTrue(HostEventQueuePolicy.shouldQueue("playback.result"))
  }
}
