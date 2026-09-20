package com.bodzey.proaudioplayer.core.model

import java.time.Instant

data class DiscoveredDevice(
    val id: DeviceId,
    val displayName: String,
    val serviceName: String,
    val apiMajorVersion: Int,
    val endpoints: Set<DeviceEndpoint>,
    val observedAt: Instant,
    val persistable: Boolean = true,
) {
    init {
        require(displayName.isNotBlank()) { "Display name must not be blank" }
        require(serviceName.isNotBlank()) { "Service name must not be blank" }
        require(apiMajorVersion > 0) { "API major version must be positive" }
        require(endpoints.isNotEmpty()) { "At least one endpoint is required" }
    }
}
