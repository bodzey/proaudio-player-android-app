package com.bodzey.proaudioplayer.core.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
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
                source.events().collect { event ->
                    send(event)
                }
            }
        }
    }
}
