package com.greenlight.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Exposes whether the device currently has a validated internet connection. */
class Connectivity(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(isOnlineNow())
    val online: StateFlow<Boolean> = _online

    init {
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _online.value = true }
            override fun onLost(network: Network) { _online.value = isOnlineNow() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) || isOnlineNow()
            }
        })
    }

    private fun isOnlineNow(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
