package com.bodzey.proaudioplayer.core.discovery.nsd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.bodzey.proaudioplayer.core.discovery.DeviceDiscoveryEvent
import com.bodzey.proaudioplayer.core.discovery.DeviceDiscoverySource
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.model.DiscoveredDevice
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidNsdDiscoverySource(
    context: Context,
    private val clock: () -> Instant = Instant::now,
) : DeviceDiscoverySource {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val mainExecutor = Executor { command ->
        Handler(Looper.getMainLooper()).post(command)
    }

    override fun events(): Flow<DeviceDiscoveryEvent> = callbackFlow {
        val serviceCallbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()
        val deviceIdsByService = mutableMapOf<String, DeviceId>()
        val legacyServices = mutableSetOf<String>()
        val legacyQueue = ArrayDeque<NsdServiceInfo>()
        var legacyResolutionActive = false

        val multicastLock = acquireLegacyMulticastLockIfNeeded()

        fun emitAvailable(serviceInfo: NsdServiceInfo) {
            val device = serviceInfo.toDiscoveredDevice(clock()) ?: return
            deviceIdsByService[serviceInfo.serviceKey()] = device.id
            trySend(DeviceDiscoveryEvent.Available(device))
        }

        fun emitUnavailable(serviceKey: String) {
            deviceIdsByService.remove(serviceKey)?.let { deviceId ->
                trySend(DeviceDiscoveryEvent.Unavailable(deviceId))
            }
        }

        lateinit var resolveNextLegacyService: () -> Unit
        resolveNextLegacyService = {
            if (!legacyResolutionActive) {
                var nextService: NsdServiceInfo? = null
                while (legacyQueue.isNotEmpty() && nextService == null) {
                    val candidate = legacyQueue.removeFirst()
                    if (candidate.serviceKey() in legacyServices) {
                        nextService = candidate
                    }
                }

                if (nextService != null) {
                    legacyResolutionActive = true
                    val resolvingKey = nextService.serviceKey()

                    @Suppress("DEPRECATION")
                    val resolveListener = object : NsdManager.ResolveListener {
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            legacyResolutionActive = false
                            if (resolvingKey in legacyServices) {
                                emitAvailable(serviceInfo)
                            }
                            resolveNextLegacyService()
                        }

                        override fun onResolveFailed(
                            serviceInfo: NsdServiceInfo,
                            errorCode: Int,
                        ) {
                            legacyResolutionActive = false
                            resolveNextLegacyService()
                        }
                    }

                    @Suppress("DEPRECATION")
                    try {
                        nsdManager.resolveService(nextService, resolveListener)
                    } catch (_: RuntimeException) {
                        legacyResolutionActive = false
                        resolveNextLegacyService()
                    }
                }
            }
        }

        fun registerModernServiceTracking(serviceInfo: NsdServiceInfo) {
            val serviceKey = serviceInfo.serviceKey()
            if (serviceCallbacks.containsKey(serviceKey)) {
                return
            }

            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(updatedInfo: NsdServiceInfo) {
                    emitAvailable(updatedInfo)
                }

                override fun onServiceLost() {
                    emitUnavailable(serviceKey)
                }

                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    serviceCallbacks.remove(serviceKey)
                }

                override fun onServiceInfoCallbackUnregistered() = Unit
            }

            serviceCallbacks[serviceKey] = callback

            try {
                nsdManager.registerServiceInfoCallback(
                    serviceInfo,
                    mainExecutor,
                    callback,
                )
            } catch (_: RuntimeException) {
                serviceCallbacks.remove(serviceKey)
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    registerModernServiceTracking(serviceInfo)
                    return
                }

                val serviceKey = serviceInfo.serviceKey()
                if (legacyServices.add(serviceKey)) {
                    legacyQueue.addLast(serviceInfo)
                    resolveNextLegacyService()
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    return
                }

                val serviceKey = serviceInfo.serviceKey()
                legacyServices.remove(serviceKey)
                emitUnavailable(serviceKey)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(
                serviceType: String,
                errorCode: Int,
            ) {
                close(NsdDiscoveryException("Unable to start NSD discovery", errorCode))
            }

            override fun onStopDiscoveryFailed(
                serviceType: String,
                errorCode: Int,
            ) = Unit
        }

        try {
            nsdManager.discoverServices(
                NsdDiscoveryContract.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener,
            )
        } catch (error: RuntimeException) {
            multicastLock?.releaseSafely()
            close(error)
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (_: RuntimeException) {
                // Discovery may already be stopped by the platform.
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceCallbacks.values.forEach { callback ->
                    try {
                        nsdManager.unregisterServiceInfoCallback(callback)
                    } catch (_: RuntimeException) {
                        // Callback may have failed registration or already been removed.
                    }
                }
            }

            multicastLock?.releaseSafely()
        }
    }

    private fun acquireLegacyMulticastLockIfNeeded(): WifiManager.MulticastLock? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return null
        }

        val wifiManager = appContext.getSystemService(WifiManager::class.java)
        return wifiManager
            .createMulticastLock("ProAudioPlayer.NsdDiscovery")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    @Suppress("DEPRECATION")
    private fun NsdServiceInfo.hostAddressesCompat(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hostAddresses.mapNotNull { address -> address.hostAddress }
        } else {
            listOfNotNull(host?.hostAddress)
        }

    private fun NsdServiceInfo.toDiscoveredDevice(observedAt: Instant): DiscoveredDevice? {
        val advertisement = NsdTxtAdvertisementParser.parse(
            serviceName = serviceName,
            attributes = attributes,
        ) ?: return null

        if (port !in 1..65535) {
            return null
        }

        val endpoints = hostAddressesCompat()
            .asSequence()
            .filter { it.isNotBlank() }
            .map { host ->
                DeviceEndpoint(
                    host = host,
                    port = port,
                )
            }
            .toSet()

        if (endpoints.isEmpty()) {
            return null
        }

        return DiscoveredDevice(
            id = advertisement.id,
            displayName = advertisement.displayName,
            serviceName = serviceName,
            apiMajorVersion = advertisement.apiMajorVersion,
            endpoints = endpoints,
            observedAt = observedAt,
        )
    }

    private fun NsdServiceInfo.serviceKey(): String =
        "$serviceName|$serviceType"

    private fun WifiManager.MulticastLock.releaseSafely() {
        if (isHeld) {
            release()
        }
    }
}

class NsdDiscoveryException(
    message: String,
    val errorCode: Int,
) : IllegalStateException("$message (error=$errorCode)")
