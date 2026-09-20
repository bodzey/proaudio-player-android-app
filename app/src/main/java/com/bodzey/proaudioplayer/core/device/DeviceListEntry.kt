package com.bodzey.proaudioplayer.core.device

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import java.time.Instant

data class DeviceListEntry(
    val id: DeviceId,
    val displayName: String,
    val apiMajorVersion: Int,
    val endpoints: Set<DeviceEndpoint>,
    val lastSeen: Instant,
    val online: Boolean,
)
