package com.bodzey.proaudioplayer.core.device

import com.bodzey.proaudioplayer.core.discovery.DeviceDiscoveryEvent
import com.bodzey.proaudioplayer.core.discovery.DiscoveryPresenceId
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.model.DiscoveredDevice
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRegistryStateTest {

    private val deviceId = DeviceId.parse("019c2c87-e95f-7b31-8bab-33e45ca6c2af")

    @Test
    fun availablePresenceCreatesOneDevice() {
        val state = DeviceRegistryState().reduce(
            available(
                presence = "wifi",
                device = device(
                    id = deviceId,
                    host = "192.168.88.50",
                ),
            ),
        )

        val available = state.devices.single()
        assertEquals(deviceId, available.id)
        assertEquals(1, available.presenceCount)
        assertEquals(setOf(DeviceEndpoint("192.168.88.50", 8080)), available.endpoints)
    }

    @Test
    fun multiplePresencesOfSameDeviceAreMerged() {
        val state = DeviceRegistryState()
            .reduce(
                available(
                    presence = "wifi",
                    device = device(
                        id = deviceId,
                        host = "192.168.88.50",
                    ),
                ),
            )
            .reduce(
                available(
                    presence = "ethernet",
                    device = device(
                        id = deviceId,
                        host = "10.0.0.50",
                    ),
                ),
            )

        val available = state.devices.single()
        assertEquals(2, available.presenceCount)
        assertEquals(
            setOf(
                DeviceEndpoint("192.168.88.50", 8080),
                DeviceEndpoint("10.0.0.50", 8080),
            ),
            available.endpoints,
        )
    }

    @Test
    fun losingOnePresenceKeepsDeviceAvailable() {
        val state = DeviceRegistryState()
            .reduce(
                available(
                    presence = "wifi",
                    device = device(deviceId, "192.168.88.50"),
                ),
            )
            .reduce(
                available(
                    presence = "ethernet",
                    device = device(deviceId, "10.0.0.50"),
                ),
            )
            .reduce(
                DeviceDiscoveryEvent.Unavailable(
                    DiscoveryPresenceId("wifi"),
                ),
            )

        val available = state.devices.single()
        assertEquals(1, available.presenceCount)
        assertEquals(setOf(DeviceEndpoint("10.0.0.50", 8080)), available.endpoints)
    }

    @Test
    fun losingLastPresenceRemovesDevice() {
        val state = DeviceRegistryState()
            .reduce(
                available(
                    presence = "wifi",
                    device = device(deviceId, "192.168.88.50"),
                ),
            )
            .reduce(
                DeviceDiscoveryEvent.Unavailable(
                    DiscoveryPresenceId("wifi"),
                ),
            )

        assertTrue(state.devices.isEmpty())
    }

    @Test
    fun samePresenceCanMoveToAnotherDeviceIdentity() {
        val replacementId = DeviceId.parse("019c2c87-e95f-7b31-8bab-33e45ca6c2b0")
        val state = DeviceRegistryState()
            .reduce(
                available(
                    presence = "service",
                    device = device(deviceId, "192.168.88.50"),
                ),
            )
            .reduce(
                available(
                    presence = "service",
                    device = device(replacementId, "192.168.88.51"),
                ),
            )

        val available = state.devices.single()
        assertEquals(replacementId, available.id)
        assertEquals(setOf(DeviceEndpoint("192.168.88.51", 8080)), available.endpoints)
    }

    @Test
    fun newestPresenceProvidesDisplayMetadata() {
        val older = device(
            id = deviceId,
            host = "192.168.88.50",
            displayName = "Old Name",
            apiMajorVersion = 1,
            observedAt = Instant.parse("2026-09-20T00:00:00Z"),
        )
        val newer = device(
            id = deviceId,
            host = "10.0.0.50",
            displayName = "Server Room",
            apiMajorVersion = 2,
            observedAt = Instant.parse("2026-09-20T00:00:10Z"),
        )

        val state = DeviceRegistryState()
            .reduce(available("older", older))
            .reduce(available("newer", newer))

        val available = state.devices.single()
        assertEquals("Server Room", available.displayName)
        assertEquals(2, available.apiMajorVersion)
        assertEquals(Instant.parse("2026-09-20T00:00:10Z"), available.lastSeen)
    }

    private fun available(
        presence: String,
        device: DiscoveredDevice,
    ): DeviceDiscoveryEvent.Available =
        DeviceDiscoveryEvent.Available(
            presenceId = DiscoveryPresenceId(presence),
            device = device,
        )

    private fun device(
        id: DeviceId,
        host: String,
        displayName: String = "ProAudio Player",
        apiMajorVersion: Int = 1,
        observedAt: Instant = Instant.parse("2026-09-20T00:00:00Z"),
    ): DiscoveredDevice =
        DiscoveredDevice(
            id = id,
            displayName = displayName,
            serviceName = "ProAudio Player",
            apiMajorVersion = apiMajorVersion,
            endpoints = setOf(DeviceEndpoint(host, 8080)),
            observedAt = observedAt,
        )
}
