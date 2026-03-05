package com.stremioshell.host

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostCommandPolicyTest {
  @Test
  fun `allows all commands for bundled shell`() {
    assertTrue(
      HostCommandPolicy.isAllowed(
        commandType = "updates.check",
        shellSource = "bundled",
        usingLocalDebugServer = false
      )
    )
  }

  @Test
  fun `allows all commands for local debug shell`() {
    assertTrue(
      HostCommandPolicy.isAllowed(
        commandType = "diagnostics.export",
        shellSource = "debug",
        usingLocalDebugServer = true
      )
    )
  }

  @Test
  fun `restricts remote fallback shell to playback open and back handled`() {
    assertTrue(
      HostCommandPolicy.isAllowed(
        commandType = "playback.open",
        shellSource = "remote",
        usingLocalDebugServer = false
      )
    )
    assertTrue(
      HostCommandPolicy.isAllowed(
        commandType = "back.handled",
        shellSource = "remote",
        usingLocalDebugServer = false
      )
    )
    assertFalse(
      HostCommandPolicy.isAllowed(
        commandType = "updates.check",
        shellSource = "remote",
        usingLocalDebugServer = false
      )
    )
  }
}
