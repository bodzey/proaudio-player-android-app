package com.bodzey.proaudioplayer.core.session

import com.bodzey.proaudioplayer.core.api.ApiCapabilities
import com.bodzey.proaudioplayer.core.api.ApiHealth
import com.bodzey.proaudioplayer.core.api.PlayerAction
import com.bodzey.proaudioplayer.core.api.PlayerApiClient
import com.bodzey.proaudioplayer.core.api.PlayerStatus
import com.bodzey.proaudioplayer.core.device.AvailableDevice
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointResolverTest {

    @Test
    fun fallsBackToAnotherReachableEndpoint() = runBlocking {
        val ipv4 = DeviceEndpoint("192.0.2.10", 5371)
        val ipv6 = DeviceEndpoint("2001:db8::10", 5371)
        val client = FakeApiClient(
            health = mapOf(
                ipv4 to Result.failure(IOException("IPv4 unavailable")),
                ipv6 to Result.success(ApiHealth("ok", 1)),
            ),
        )

        val resolved = EndpointResolver(client).resolve(
            device(setOf(ipv4, ipv6)),
        )

        assertEquals(ipv6, resolved.endpoint)
    }

    @Test
    fun rejectsEndpointWithDifferentApiMajor() {
        val endpoint = DeviceEndpoint("192.0.2.10", 5371)
        val client = FakeApiClient(
            health = mapOf(
                endpoint to Result.success(ApiHealth("ok", 2)),
            ),
        )

        assertThrows(EndpointResolutionException::class.java) {
            runBlocking {
                EndpointResolver(client).resolve(device(setOf(endpoint)))
            }
        }
    }

    private fun device(endpoints: Set<DeviceEndpoint>): AvailableDevice =
        AvailableDevice(
            id = DeviceId.parse("019c2c87-e95f-7b31-8bab-33e45ca6c2af"),
            displayName = "Test Player",
            apiMajorVersion = 1,
            endpoints = endpoints,
            lastSeen = Instant.parse("2026-09-20T00:00:00Z"),
            presenceCount = endpoints.size,
        )

    private class FakeApiClient(
        private val health: Map<DeviceEndpoint, Result<ApiHealth>>,
    ) : PlayerApiClient {
        override suspend fun health(endpoint: DeviceEndpoint): ApiHealth =
            health.getValue(endpoint).getOrThrow()

        override suspend fun capabilities(endpoint: DeviceEndpoint): ApiCapabilities =
            error("Not used")

        override suspend fun status(endpoint: DeviceEndpoint): PlayerStatus =
            error("Not used")

        override suspend fun playerAction(
            endpoint: DeviceEndpoint,
            action: PlayerAction,
        ) = error("Not used")

        override fun statusEvents(endpoint: DeviceEndpoint): Flow<PlayerStatus> =
            emptyFlow()
    }
}
