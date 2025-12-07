package com.github.kr328.clash

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.NetworkSettingsDesign
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class NetworkSettingsActivity : BaseActivity<NetworkSettingsDesign>() {
    override suspend fun main() {
        val currentWifiSsid = getCurrentWifiSsid()

        val design = NetworkSettingsDesign(
            this,
            uiStore,
            ServiceStore(this),
            clashRunning,
            currentWifiSsid,
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStart, Event.ClashStop, Event.ServiceRecreated ->
                            recreate()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        NetworkSettingsDesign.Request.StartAccessControlList ->
                            startActivity(AccessControlActivity::class.intent)
                        NetworkSettingsDesign.Request.RequestLocationPermission ->
                            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
                            }
                    }
                }
            }
        }
    }

    private fun getCurrentWifiSsid(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val connectivity = getSystemService<ConnectivityManager>() ?: return null
                val network = connectivity.activeNetwork ?: return null
                val caps = connectivity.getNetworkCapabilities(network) ?: return null
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    (caps.transportInfo as? WifiInfo)?.ssid?.removePrefix("\"")?.removeSuffix("\"")
                } else null
            } else {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiManager?.connectionInfo?.ssid?.removePrefix("\"")?.removeSuffix("\"")
            }
        } catch (e: Exception) {
            null
        }
    }
}
