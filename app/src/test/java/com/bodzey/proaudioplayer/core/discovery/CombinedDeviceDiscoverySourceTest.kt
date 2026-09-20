package com.bodzey.proaudioplayer.core.discovery

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.model.DiscoveredDevice
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CombinedDeviceDiscoverySourceTest {

    @Test
    fun failingSourceDoesNotCancelHealthySource() = runBlocking {
        val expected = DeviceDiscoveryEvent.Available(
            presenceId = DiscoveryPresenceId("healthy"),
            device = DiscoveredDevice(
                id = DeviceId.parse("019c2c87-e95f-7b31-8bab-33e45ca6c2af"),
                displayName = "Test Player",
                serviceName = "test-player",
                apiMajorVersion = 1,
                endpoints = setOf(
                    DeviceEndpoint("192.0.2.10", 5371),
                ),
                observedAt = Instant.parse("2026-09-20T00:00:00Z"),
            ),
        )

        val failing = object : DeviceDiscoverySource {
            override fun events(): Flow<DeviceDiscoveryEvent> = flow {
                throw IOException("NSD unavailable")
            }
        }
        val healthy = object : DeviceDiscoverySource {
            override fun events(): Flow<DeviceDiscoveryEvent> = flowOf(expected)
        }

        val actual = CombinedDeviceDiscoverySource(
            failing,
            healthy,
        ).events().first()

        assertEquals(expected, actual)
    }
}
