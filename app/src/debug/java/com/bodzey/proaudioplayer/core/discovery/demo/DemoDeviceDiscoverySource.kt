package com.bodzey.proaudioplayer.core.discovery.demo

import com.bodzey.proaudioplayer.core.discovery.DeviceDiscoveryEvent
import com.bodzey.proaudioplayer.core.discovery.DeviceDiscoverySource
import com.bodzey.proaudioplayer.core.discovery.DiscoveryPresenceId
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.model.DiscoveredDevice
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DemoDeviceDiscoverySource(
    private val controller: DemoDiscoveryController,
    private val clock: () -> Instant = Instant::now,
) : DeviceDiscoverySource {

    private val presences = listOf(
        DemoPresence(
            presenceId = DiscoveryPresenceId("demo:studio:wifi"),
            deviceId = DeviceId.parse("019c2c87-e95f-7b31-8bab-33e45ca6c2af"),
            displayName = "Studio Player",
            host = "192.0.2.10",
        ),
        DemoPresence(
            presenceId = DiscoveryPresenceId("demo:studio:ethernet"),
            deviceId = DeviceId.parse("019c2c87-e95f-7b31-8bab-33e45ca6c2af"),
            displayName = "Studio Player",
            host = "2001:db8::10",
        ),
        DemoPresence(
            presenceId = DiscoveryPresenceId("demo:rack"),
            deviceId = DeviceId.parse("019c2c87-e95f-7b31-8bab-33e45ca6c2b0"),
            displayName = "Rack Player",
            host = "192.0.2.20",
        ),
    )

    override fun events(): Flow<DeviceDiscoveryEvent> = flow {
        controller.enabled.collect { enabled ->
            if (enabled) {
                val observedAt = clock()
                presences.forEach { presence ->
                    emit(
                        DeviceDiscoveryEvent.Available(
                            presenceId = presence.presenceId,
                            device = presence.toDevice(observedAt),
                        ),
                    )
                }
            } else {
                presences.forEach { presence ->
                    emit(DeviceDiscoveryEvent.Unavailable(presence.presenceId))
                }
            }
        }
    }

    private data class DemoPresence(
        val presenceId: DiscoveryPresenceId,
        val deviceId: DeviceId,
        val displayName: String,
        val host: String,
    ) {
        fun toDevice(observedAt: Instant): DiscoveredDevice =
            DiscoveredDevice(
                id = deviceId,
                displayName = displayName,
                serviceName = displayName,
                apiMajorVersion = 1,
                endpoints = setOf(
                    DeviceEndpoint(
                        host = host,
                        port = 8080,
                    ),
                ),
                observedAt = observedAt,
            )
    }
}
