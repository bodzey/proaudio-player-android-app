package com.bodzey.proaudioplayer.core.discovery.nsd

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.annotation.RequiresApi
import com.bodzey.proaudioplayer.core.discovery.DiscoveryPresenceId
import java.util.concurrent.Executor

@RequiresApi(34)
internal class ModernNsdServiceTracker(
    private val nsdManager: NsdManager,
    private val executor: Executor,
    private val onUpdated: (NsdServiceInfo) -> Unit,
    private val onLost: (DiscoveryPresenceId) -> Unit,
) {
    private val callbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()

    fun track(serviceInfo: NsdServiceInfo) {
        val serviceKey = serviceInfo.modernServiceKey()
        if (callbacks.containsKey(serviceKey)) {
            return
        }

        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(updatedInfo: NsdServiceInfo) {
                onUpdated(updatedInfo)
            }

            override fun onServiceLost() {
                callbacks.remove(serviceKey)
                onLost(DiscoveryPresenceId("nsd:$serviceKey"))
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                callbacks.remove(serviceKey)
            }

            override fun onServiceInfoCallbackUnregistered() {
                callbacks.remove(serviceKey)
            }
        }

        callbacks[serviceKey] = callback

        try {
            nsdManager.registerServiceInfoCallback(
                serviceInfo,
                executor,
                callback,
            )
        } catch (error: RuntimeException) {
            callbacks.remove(serviceKey)
            throw error
        }
    }

    fun stop() {
        val registeredCallbacks = callbacks.values.toList()
        callbacks.clear()

        registeredCallbacks.forEach { callback ->
            try {
                nsdManager.unregisterServiceInfoCallback(callback)
            } catch (_: IllegalArgumentException) {
                // The platform may already have removed a failed registration.
            }
        }
    }

    private fun NsdServiceInfo.modernServiceKey(): String =
        "$serviceName|$serviceType|${network?.networkHandle ?: "any"}"
}
