package com.example.netbuddy.network

import android.content.Context
import android.net.*
import java.net.Inet4Address
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class NetworkState(
    val connected: Boolean = false,
    val type: String = "Disconnected",
    val ipv4: String = "Unavailable",
    val validated: Boolean = false,
    val warning: String? = null,
    val wifiSignalDbm: Int? = null
)

class NetworkMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    val states = callbackFlow {
        var active: Network? = null
        var caps: NetworkCapabilities? = null
        var links: LinkProperties? = null
        fun emit() {
            val c = caps
            val type = when {
                c == null -> "Disconnected"
                c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Other"
            }
            val signal = if (type == "Wi-Fi" && android.os.Build.VERSION.SDK_INT >= 29) {
                c?.signalStrength?.takeIf { it != Int.MIN_VALUE }
            } else null
            trySend(NetworkState(active != null, type,
                links?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress ?: "Unavailable",
                c?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                wifiSignalDbm = signal))
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                active = network; caps = null; links = null; emit()
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (network == active) { caps = capabilities; emit() }
            }
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                if (network == active) { links = properties; emit() }
            }
            override fun onLost(network: Network) {
                if (network == active) { active = null; caps = null; links = null; emit() }
            }
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
        } catch (error: SecurityException) {
            trySend(NetworkState(warning = "Network information unavailable: ${error.message}"))
        }
        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}
