package com.bodzey.proaudioplayer.core.device

import com.bodzey.proaudioplayer.core.model.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DeviceRepository(
    deviceRegistry: DeviceRegistry,
    scope: CoroutineScope,
) {
    val devices: StateFlow<List<AvailableDevice>> = deviceRegistry
        .devices()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    fun current(deviceId: DeviceId): AvailableDevice? =
        devices.value.firstOrNull { device -> device.id == deviceId }
}
