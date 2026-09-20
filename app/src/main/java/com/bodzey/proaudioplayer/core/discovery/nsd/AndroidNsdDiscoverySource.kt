package com.bodzey.proaudioplayer.core.discovery.nsd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.bodzey.proaudioplayer.core.discovery.DeviceDiscoveryEvent
import com.bodzey.proaudioplayer.core.discovery.DeviceDiscoverySource
import com.bodzey.proaudioplayer.core.discovery.DiscoveryPresenceId
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
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
        val legacyServices = mutableSetOf<String>()
        val legacyQueue = ArrayDeque<NsdServiceInfo>()
        var legacyResolutionActive = false

        val multicastLock = acquireLegacyMulticastLockIfNeeded()

        fun emitAvailable(serviceInfo: NsdServiceInfo) {
            val device = serviceInfo.toDiscoveredDevice(clock()) ?: return
            trySend(
                DeviceDiscoveryEvent.Available(
                    presenceId = serviceInfo.presenceId(),
                    device = device,
                ),
            )
        }

        fun emitUnavailable(presenceId: DiscoveryPresenceId) {
            trySend(DeviceDiscoveryEvent.Unavailable(presenceId))
        }

        val modernTracker =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ModernNsdServiceTracker(
                    nsdManager = nsdManager,
                    executor = mainExecutor,
                    onUpdated = ::emitAvailable,
                    onLost = ::emitUnavailable,
                )
            } else {
                null
            }

        lateinit var resolveNextLegacyService: () -> Unit
        resolveNextLegacyService = {
            if (!legacyResolutionActive) {
                var nextService: NsdServiceInfo? = null
                while (legacyQueue.isNotEmpty() && nextService == null) {
                    val candidate = legacyQueue.removeFirst()
                    if (candidate.legacyServiceKey() in legacyServices) {
                        nextService = candidate
                    }
                }

                if (nextService != null) {
                    legacyResolutionActive = true
                    val resolvingKey = nextService.legacyServiceKey()

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

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        modernTracker?.track(serviceInfo)
                    } catch (_: RuntimeException) {
                        // Continue discovery if tracking one service fails.
                    }
                    return
                }

                val serviceKey = serviceInfo.legacyServiceKey()
                if (legacyServices.add(serviceKey)) {
                    legacyQueue.addLast(serviceInfo)
                    resolveNextLegacyService()
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    return
                }

                val serviceKey = serviceInfo.legacyServiceKey()
                legacyServices.remove(serviceKey)
                emitUnavailable(serviceInfo.legacyPresenceId())
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
                modernTracker?.stop()
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
            modernHostAddresses()
        } else {
            listOfNotNull(host?.hostAddress)
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun NsdServiceInfo.modernHostAddresses(): List<String> =
        hostAddresses.mapNotNull { address -> address.hostAddress }

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

    private fun NsdServiceInfo.presenceId(): DiscoveryPresenceId =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            DiscoveryPresenceId(
                "nsd:$serviceName|$serviceType|${network?.networkHandle ?: "any"}",
            )
        } else {
            legacyPresenceId()
        }

    private fun NsdServiceInfo.legacyPresenceId(): DiscoveryPresenceId =
        DiscoveryPresenceId("nsd:${legacyServiceKey()}")

    private fun NsdServiceInfo.legacyServiceKey(): String =
        "$serviceName|$serviceType|legacy"

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
