package com.bodzey.proaudioplayer.core.discovery

import kotlinx.coroutines.flow.Flow

interface DeviceDiscoverySource {
    fun events(): Flow<DeviceDiscoveryEvent>
}
