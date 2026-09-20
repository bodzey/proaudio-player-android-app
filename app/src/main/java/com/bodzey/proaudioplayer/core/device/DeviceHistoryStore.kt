package com.bodzey.proaudioplayer.core.device

import kotlinx.coroutines.flow.Flow

interface DeviceHistoryStore {
    fun devices(): Flow<List<KnownDevice>>
    suspend fun record(devices: List<AvailableDevice>)
}
