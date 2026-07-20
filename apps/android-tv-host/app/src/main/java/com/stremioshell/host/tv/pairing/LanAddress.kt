package com.stremioshell.host.tv.pairing

import java.net.Inet4Address
import java.net.NetworkInterface

/** Best-guess LAN IPv4 address the phone can reach, or null if not on a network. */
fun findLanIpv4(): String? {
  return runCatching {
    NetworkInterface.getNetworkInterfaces().asSequence()
      .filter { it.isUp && !it.isLoopback && !it.isVirtual }
      .flatMap { it.inetAddresses.asSequence() }
      .filterIsInstance<Inet4Address>()
      .firstOrNull { it.isSiteLocalAddress }
      ?.hostAddress
  }.getOrNull()
}
