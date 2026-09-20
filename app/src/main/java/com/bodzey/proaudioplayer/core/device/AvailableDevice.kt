package com.bodzey.proaudioplayer.core.device

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import java.time.Instant

data class AvailableDevice(
    val id: DeviceId,
    val displayName: String,
    val apiMajorVersion: Int,
    val endpoints: Set<DeviceEndpoint>,
    val lastSeen: Instant,
    val presenceCount: Int,
) {
    init {
        require(displayName.isNotBlank()) { "Display name must not be blank" }
        require(apiMajorVersion > 0) { "API major version must be positive" }
        require(endpoints.isNotEmpty()) { "At least one endpoint is required" }
        require(presenceCount > 0) { "Presence count must be positive" }
    }
}
