package com.stremioshell.host

object HostEventQueuePolicy {
  fun shouldQueue(eventType: String): Boolean {
    return eventType != "back.pressed"
  }
}
