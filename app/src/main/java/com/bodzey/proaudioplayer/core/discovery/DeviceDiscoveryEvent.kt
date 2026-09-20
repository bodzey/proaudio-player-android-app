package com.bodzey.proaudioplayer.core.discovery

import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.model.DiscoveredDevice

sealed interface DeviceDiscoveryEvent {
    data class Available(
        val device: DiscoveredDevice,
    ) : DeviceDiscoveryEvent

    data class Unavailable(
        val deviceId: DeviceId,
    ) : DeviceDiscoveryEvent
}
