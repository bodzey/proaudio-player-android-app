package com.bodzey.proaudioplayer.core.device

import com.bodzey.proaudioplayer.core.model.DeviceId
import kotlinx.coroutines.flow.Flow

interface DeviceHistoryStore {
    fun devices(): Flow<List<KnownDevice>>
    suspend fun record(devices: List<AvailableDevice>)
    suspend fun forget(deviceId: DeviceId)
}
