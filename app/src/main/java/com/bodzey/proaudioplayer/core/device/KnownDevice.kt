package com.bodzey.proaudioplayer.core.device

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import java.time.Instant

data class KnownDevice(
    val id: DeviceId,
    val displayName: String,
    val apiMajorVersion: Int,
    val endpoints: Set<DeviceEndpoint>,
    val lastSeen: Instant,
) {
    init {
        require(displayName.isNotBlank()) { "Display name must not be blank" }
        require(apiMajorVersion > 0) { "API major version must be positive" }
    }
}
