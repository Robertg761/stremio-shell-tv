package com.stremioshell.host

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class NetworkMonitor(
  context: Context,
  private val onNetworkChanged: (connected: Boolean, transport: String) -> Unit
) {
  private val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  private var started = false

  private val callback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      emitCurrentState()
    }

    override fun onLost(network: Network) {
      emitCurrentState()
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
      emitCurrentState()
    }
  }

  fun start() {
    if (started) {
      return
    }
    started = true
    connectivityManager.registerDefaultNetworkCallback(callback)
    emitCurrentState()
  }

  fun stop() {
    if (!started) {
      return
    }
    started = false
    runCatching {
      connectivityManager.unregisterNetworkCallback(callback)
    }
  }

  private fun emitCurrentState() {
    val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    val connected = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    val transport = when {
      capabilities == null -> "unknown"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
      else -> "unknown"
    }

    onNetworkChanged(connected, transport)
  }
}
