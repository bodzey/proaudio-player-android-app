package com.bodzey.proaudioplayer.core.device

import com.bodzey.proaudioplayer.core.discovery.DeviceDiscoveryEvent
import com.bodzey.proaudioplayer.core.discovery.DiscoveryPresenceId
import com.bodzey.proaudioplayer.core.model.DiscoveredDevice

class DeviceRegistryState private constructor(
    private val presences: Map<DiscoveryPresenceId, DiscoveredDevice>,
) {
    constructor() : this(emptyMap())

    val devices: List<AvailableDevice>
        get() = presences
            .entries
            .groupBy { entry -> entry.value.id }
            .map { (deviceId, entries) ->
                val newest = entries.maxWith(
                    compareBy<Map.Entry<DiscoveryPresenceId, DiscoveredDevice>> {
                        it.value.observedAt
                    }.thenBy {
                        it.key.value
                    },
                )

                AvailableDevice(
                    id = deviceId,
                    displayName = newest.value.displayName,
                    apiMajorVersion = newest.value.apiMajorVersion,
                    endpoints = entries
                        .asSequence()
                        .flatMap { entry -> entry.value.endpoints.asSequence() }
                        .toSet(),
                    lastSeen = entries.maxOf { entry -> entry.value.observedAt },
                    presenceCount = entries.size,
                    persistable = entries.any { entry ->
                        entry.value.persistable
                    },
                )
            }
            .sortedWith(
                compareBy<AvailableDevice> { device -> device.displayName.lowercase() }
                    .thenBy { device -> device.id.value },
            )

    fun reduce(event: DeviceDiscoveryEvent): DeviceRegistryState {
        val updatedPresences = when (event) {
            is DeviceDiscoveryEvent.Available ->
                presences + (event.presenceId to event.device)

            is DeviceDiscoveryEvent.Unavailable ->
                presences - event.presenceId
        }

        return DeviceRegistryState(updatedPresences)
    }
}
