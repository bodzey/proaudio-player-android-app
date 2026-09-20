package com.bodzey.proaudioplayer.core.device

import com.bodzey.proaudioplayer.core.discovery.DeviceDiscoverySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold

class DeviceRegistry(
    private val discoverySource: DeviceDiscoverySource,
) {
    fun devices(): Flow<List<AvailableDevice>> =
        discoverySource
            .events()
            .runningFold(DeviceRegistryState()) { state, event ->
                state.reduce(event)
            }
            .map { state -> state.devices }
            .distinctUntilChanged()
}
