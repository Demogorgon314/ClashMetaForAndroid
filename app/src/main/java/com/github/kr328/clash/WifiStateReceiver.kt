package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.startClashService

class WifiStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiManager.NETWORK_STATE_CHANGED_ACTION,
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                handleNetworkChange(context)
            }
        }
    }

    private fun handleNetworkChange(context: Context) {
        val store = ServiceStore(context)
        
        if (!store.autoCloseProxyOnWifi || !store.autoRestartProxy) {
            return
        }

        val currentSsid = getCurrentWifiSsid(context)
        val wifiList = store.autoCloseWifiList
        
        // If we're not on a whitelisted Wi-Fi (or not on Wi-Fi at all), start proxy
        val shouldStartProxy = currentSsid == null || currentSsid !in wifiList
        
        if (shouldStartProxy && !StatusProvider.serviceRunning) {
            Log.i("WifiStateReceiver: Auto starting proxy (current wifi: $currentSsid)")
            context.startClashService()
        }
    }

    private fun getCurrentWifiSsid(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val connectivity = context.getSystemService<ConnectivityManager>() ?: return null
                val network = connectivity.activeNetwork ?: return null
                val caps = connectivity.getNetworkCapabilities(network) ?: return null
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    (caps.transportInfo as? WifiInfo)?.ssid?.removePrefix("\"")?.removeSuffix("\"")
                } else null
            } else {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiManager?.connectionInfo?.ssid?.removePrefix("\"")?.removeSuffix("\"")
            }
        } catch (e: Exception) {
            null
        }
    }
}
