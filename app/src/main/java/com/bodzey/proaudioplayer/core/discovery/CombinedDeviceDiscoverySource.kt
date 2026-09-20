package com.bodzey.proaudioplayer.core.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CombinedDeviceDiscoverySource(
    private vararg val sources: DeviceDiscoverySource,
) : DeviceDiscoverySource {
    init {
        require(sources.isNotEmpty()) { "At least one discovery source is required" }
    }

    override fun events(): Flow<DeviceDiscoveryEvent> = channelFlow {
        sources.forEach { source ->
            launch {
                while (currentCoroutineContext().isActive) {
                    try {
                        source.events().collect { event ->
                            send(event)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // A platform discovery failure must not cancel other
                        // discovery adapters. Restart this source after backoff.
                    }

                    if (currentCoroutineContext().isActive) {
                        delay(RESTART_DELAY_MILLIS)
                    }
                }
            }
        }
    }

    private companion object {
        const val RESTART_DELAY_MILLIS = 2_000L
    }
}
