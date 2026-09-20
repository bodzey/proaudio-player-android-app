package com.bodzey.proaudioplayer.core.discovery

import com.bodzey.proaudioplayer.core.model.DiscoveredDevice

sealed interface DeviceDiscoveryEvent {
    data class Available(
        val presenceId: DiscoveryPresenceId,
        val device: DiscoveredDevice,
    ) : DeviceDiscoveryEvent

    data class Unavailable(
        val presenceId: DiscoveryPresenceId,
    ) : DeviceDiscoveryEvent
}
