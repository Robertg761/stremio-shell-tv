package com.stremioshell.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackHandshakeControllerTest {
  @Test
  fun `acknowledge handled resolves without fallback`() {
    val controller = BackHandshakeController(timeoutMs = 250L)
    val pending = controller.begin(requestId = "req-1", nowMs = 1_000L)

    assertNotNull(pending)
    val resolution = controller.acknowledge(
      requestId = "req-1",
      handled = true,
      reason = "overlay_closed",
      nowMs = 1_050L
    )

    assertTrue(resolution is BackHandshakeResolution.Handled)
    val handled = resolution as BackHandshakeResolution.Handled
    assertEquals("req-1", handled.requestId)
    assertEquals("overlay_closed", handled.reason)
    assertNull(controller.pendingRequestId())
  }

  @Test
  fun `acknowledge unhandled triggers native fallback`() {
    val controller = BackHandshakeController(timeoutMs = 250L)
    controller.begin(requestId = "req-2", nowMs = 2_000L)

    val resolution = controller.acknowledge(
      requestId = "req-2",
      handled = false,
      reason = "unhandled",
      nowMs = 2_100L
    )

    assertTrue(resolution is BackHandshakeResolution.RunNativeFallback)
    val fallback = resolution as BackHandshakeResolution.RunNativeFallback
    assertEquals("req-2", fallback.requestId)
    assertEquals("unhandled", fallback.reason)
    assertNull(controller.pendingRequestId())
  }

  @Test
  fun `timeout triggers native fallback when pending request expires`() {
    val controller = BackHandshakeController(timeoutMs = 250L)
    controller.begin(requestId = "req-3", nowMs = 3_000L)

    val resolution = controller.onTimeout(requestId = "req-3", nowMs = 3_260L)

    assertTrue(resolution is BackHandshakeResolution.RunNativeFallback)
    val fallback = resolution as BackHandshakeResolution.RunNativeFallback
    assertEquals("req-3", fallback.requestId)
    assertEquals("ack_timeout", fallback.reason)
    assertNull(controller.pendingRequestId())
  }

  @Test
  fun `acknowledge with mismatched request id is ignored`() {
    val controller = BackHandshakeController(timeoutMs = 250L)
    controller.begin(requestId = "req-4", nowMs = 4_000L)

    val resolution = controller.acknowledge(
      requestId = "req-other",
      handled = true,
      reason = "ignored",
      nowMs = 4_050L
    )

    assertTrue(resolution is BackHandshakeResolution.Ignored)
    val ignored = resolution as BackHandshakeResolution.Ignored
    assertEquals("request_id_mismatch", ignored.reason)
    assertEquals("req-4", controller.pendingRequestId())
  }

  @Test
  fun `begin returns null while another request is pending`() {
    val controller = BackHandshakeController(timeoutMs = 250L)
    controller.begin(requestId = "req-5", nowMs = 5_000L)

    val second = controller.begin(requestId = "req-6", nowMs = 5_010L)

    assertNull(second)
    assertEquals("req-5", controller.pendingRequestId())
  }
}
