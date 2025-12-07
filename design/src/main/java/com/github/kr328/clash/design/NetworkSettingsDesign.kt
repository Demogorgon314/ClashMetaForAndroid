package com.github.kr328.clash.design

import android.content.Context
import android.os.Build
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.*
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.launch

class NetworkSettingsDesign(
    context: Context,
    uiStore: UiStore,
    srvStore: ServiceStore,
    running: Boolean,
    currentWifiSsid: String?,
) : Design<NetworkSettingsDesign.Request>(context) {
    enum class Request {
        StartAccessControlList,
        RequestLocationPermission,
    }

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        val screen = preferenceScreen(context) {
            val vpnDependencies: MutableList<Preference> = mutableListOf()

            val vpn = switch(
                value = uiStore::enableVpn,
                icon = R.drawable.ic_baseline_vpn_lock,
                title = R.string.route_system_traffic,
                summary = R.string.routing_via_vpn_service
            ) {
                listener = OnChangedListener {
                    vpnDependencies.forEach {
                        it.enabled = uiStore.enableVpn
                    }
                }
            }

            category(R.string.vpn_service_options)

            switch(
                value = srvStore::bypassPrivateNetwork,
                title = R.string.bypass_private_network,
                summary = R.string.bypass_private_network_summary,
                configure = vpnDependencies::add,
            )

            switch(
                value = srvStore::dnsHijacking,
                title = R.string.dns_hijacking,
                summary = R.string.dns_hijacking_summary,
                configure = vpnDependencies::add,
            )

            switch(
                value = srvStore::allowBypass,
                title = R.string.allow_bypass,
                summary = R.string.allow_bypass_summary,
                configure = vpnDependencies::add,
            )

            switch(
                value = srvStore::allowIpv6,
                title = R.string.allow_ipv6,
                summary = R.string.allow_ipv6_summary,
                configure = vpnDependencies::add,
            )

            if (Build.VERSION.SDK_INT >= 29) {
                switch(
                    value = srvStore::systemProxy,
                    title = R.string.system_proxy,
                    summary = R.string.system_proxy_summary,
                    configure = vpnDependencies::add,
                )
            }

            selectableList(
                value = srvStore::tunStackMode,
                values = arrayOf(
                    "system",
                    "gvisor",
                    "mixed"
                ),
                valuesText = arrayOf(
                    R.string.tun_stack_system,
                    R.string.tun_stack_gvisor,
                    R.string.tun_stack_mixed
                ),
                title = R.string.tun_stack_mode,
                configure = vpnDependencies::add,
            )

            selectableList(
                value = srvStore::accessControlMode,
                values = AccessControlMode.values(),
                valuesText = arrayOf(
                    R.string.allow_all_apps,
                    R.string.allow_selected_apps,
                    R.string.deny_selected_apps
                ),
                title = R.string.access_control_mode,
                configure = vpnDependencies::add,
            )

            clickable(
                title = R.string.access_control_packages,
                summary = R.string.access_control_packages_summary,
            ) {
                clicked {
                    requests.trySend(Request.StartAccessControlList)
                }
            }

            category(R.string.auto_close_proxy_on_wifi)

            // Show current Wi-Fi SSID
            val currentWifiText = if (currentWifiSsid != null) {
                context.getString(R.string.current_wifi, currentWifiSsid)
            } else {
                context.getString(R.string.current_wifi_not_connected)
            }
            tips(text = currentWifiText)

            val autoCloseDependencies: MutableList<Preference> = mutableListOf()

            val autoCloseWifiListProxy = object {
                var list: List<String>?
                    get() = srvStore.autoCloseWifiList.sorted()
                    set(value) {
                        srvStore.autoCloseWifiList = value?.toSet() ?: emptySet()
                    }
            }

            val autoCloseSwitch = switch(
                value = srvStore::autoCloseProxyOnWifi,
                title = R.string.auto_close_proxy_on_wifi,
                summary = R.string.auto_close_proxy_on_wifi_summary,
            ) {
                listener = OnChangedListener {
                    if (srvStore.autoCloseProxyOnWifi) {
                        requests.trySend(Request.RequestLocationPermission)
                    }
                    autoCloseDependencies.forEach {
                        it.enabled = srvStore.autoCloseProxyOnWifi
                    }
                }
            }

            switch(
                value = srvStore::autoRestartProxy,
                title = R.string.auto_restart_proxy,
                summary = R.string.auto_restart_proxy_summary,
                configure = autoCloseDependencies::add,
            )

            editableTextList(
                value = autoCloseWifiListProxy::list,
                adapter = TextAdapter.String,
                title = R.string.auto_close_wifi_list,
                placeholder = R.string.auto_close_wifi_list_placeholder,
                configure = autoCloseDependencies::add
            )

            if (!srvStore.autoCloseProxyOnWifi) {
                autoCloseDependencies.forEach {
                    it.enabled = false
                }
            }

            if (running) {
                vpn.enabled = false

                vpnDependencies.forEach {
                    it.enabled = false
                }
            } else {
                vpn.listener?.onChanged()
            }
        }

        binding.content.addView(screen.root)

        if (running) {
            launch {
                showToast(R.string.options_unavailable, ToastDuration.Indefinite)
            }
        }
    }
}