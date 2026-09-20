package com.bodzey.proaudioplayer.core.session

import com.bodzey.proaudioplayer.core.api.ApiCapabilities
import com.bodzey.proaudioplayer.core.api.ApiHealth
import com.bodzey.proaudioplayer.core.api.AudioOutputDescriptor
import com.bodzey.proaudioplayer.core.api.AlertAudioSettings
import com.bodzey.proaudioplayer.core.api.AlertAudioUpdate
import com.bodzey.proaudioplayer.core.api.AlertMediaCatalog
import com.bodzey.proaudioplayer.core.api.AlertMediaFile
import com.bodzey.proaudioplayer.core.api.AlertProviderSettings
import com.bodzey.proaudioplayer.core.api.AlertProviderTestResult
import com.bodzey.proaudioplayer.core.api.AlertProviderUpdate
import com.bodzey.proaudioplayer.core.api.MeterFrame
import com.bodzey.proaudioplayer.core.api.MixerState
import com.bodzey.proaudioplayer.core.api.MixerTarget
import com.bodzey.proaudioplayer.core.api.PlayerAction
import com.bodzey.proaudioplayer.core.api.PlayerApiClient
import com.bodzey.proaudioplayer.core.api.PlayerStatus
import com.bodzey.proaudioplayer.core.api.QueueItem
import com.bodzey.proaudioplayer.core.api.RadioStation
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

        override suspend fun setMasterVolume(
            endpoint: DeviceEndpoint,
            percent: Double,
        ) = error("Not used")

        override suspend fun setMasterMute(
            endpoint: DeviceEndpoint,
            db: Double,
            muted: Boolean,
        ) = error("Not used")

        override suspend fun radioStations(
            endpoint: DeviceEndpoint,
        ): List<RadioStation> = error("Not used")

        override suspend fun playStream(
            endpoint: DeviceEndpoint,
            url: String,
        ) = error("Not used")

        override suspend fun mixer(
            endpoint: DeviceEndpoint,
        ): MixerState = error("Not used")

        override suspend fun setMixer(
            endpoint: DeviceEndpoint,
            target: MixerTarget,
            db: Double,
            muted: Boolean,
        ): MixerState = error("Not used")

        override suspend fun audioOutputs(
            endpoint: DeviceEndpoint,
        ): List<AudioOutputDescriptor> = error("Not used")

        override suspend fun selectAudioOutput(
            endpoint: DeviceEndpoint,
            id: String,
        ): AudioOutputDescriptor = error("Not used")

        override suspend fun library(
            endpoint: DeviceEndpoint,
        ): List<String> = error("Not used")

        override suspend fun refreshLibrary(
            endpoint: DeviceEndpoint,
        ) = error("Not used")

        override suspend fun playLibraryPath(
            endpoint: DeviceEndpoint,
            path: String,
        ) = error("Not used")

        override suspend fun playlists(
            endpoint: DeviceEndpoint,
        ): List<String> = error("Not used")

        override suspend fun loadPlaylist(
            endpoint: DeviceEndpoint,
            name: String,
        ) = error("Not used")

        override suspend fun queue(
            endpoint: DeviceEndpoint,
        ): List<QueueItem> = error("Not used")

        override suspend fun playQueueItem(
            endpoint: DeviceEndpoint,
            position: Int,
        ) = error("Not used")

        override suspend fun removeQueueItem(
            endpoint: DeviceEndpoint,
            position: Int,
        ) = error("Not used")

        override suspend fun clearQueue(
            endpoint: DeviceEndpoint,
        ) = error("Not used")

        override suspend fun alertProviderSettings(
            endpoint: DeviceEndpoint,
        ): AlertProviderSettings = error("Not used")

        override suspend fun alertAudioSettings(
            endpoint: DeviceEndpoint,
        ): AlertAudioSettings = error("Not used")

        override suspend fun alertMedia(
            endpoint: DeviceEndpoint,
        ): AlertMediaCatalog = error("Not used")

        override suspend fun saveAlertProviderSettings(
            endpoint: DeviceEndpoint,
            update: AlertProviderUpdate,
        ): AlertProviderSettings = error("Not used")

        override suspend fun testAlertProviderSettings(
            endpoint: DeviceEndpoint,
            update: AlertProviderUpdate,
        ): AlertProviderTestResult = error("Not used")

        override suspend fun saveAlertAudioSettings(
            endpoint: DeviceEndpoint,
            update: AlertAudioUpdate,
        ): AlertAudioSettings = error("Not used")

        override suspend fun uploadAlertMedia(
            endpoint: DeviceEndpoint,
            kind: String,
            bytes: ByteArray,
            contentType: String,
        ): AlertMediaFile = error("Not used")

        override suspend fun resetAlertMedia(
            endpoint: DeviceEndpoint,
            kind: String,
        ): AlertMediaFile = error("Not used")

        override fun statusEvents(endpoint: DeviceEndpoint): Flow<PlayerStatus> =
            emptyFlow()

        override fun meterEvents(endpoint: DeviceEndpoint): Flow<MeterFrame> =
            emptyFlow()
    }
}
