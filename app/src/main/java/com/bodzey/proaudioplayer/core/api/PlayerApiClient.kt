package com.bodzey.proaudioplayer.core.api

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import kotlinx.coroutines.flow.Flow

interface PlayerApiClient {
    suspend fun health(endpoint: DeviceEndpoint): ApiHealth
    suspend fun capabilities(endpoint: DeviceEndpoint): ApiCapabilities
    suspend fun status(endpoint: DeviceEndpoint): PlayerStatus
    suspend fun playerAction(endpoint: DeviceEndpoint, action: PlayerAction)
    suspend fun setMasterVolume(endpoint: DeviceEndpoint, percent: Double)
    suspend fun setMasterMute(endpoint: DeviceEndpoint, db: Double, muted: Boolean)
    suspend fun radioStations(endpoint: DeviceEndpoint): List<RadioStation>
    suspend fun playStream(endpoint: DeviceEndpoint, url: String)
    suspend fun alertProviderSettings(endpoint: DeviceEndpoint): AlertProviderSettings
    suspend fun alertAudioSettings(endpoint: DeviceEndpoint): AlertAudioSettings
    suspend fun alertMedia(endpoint: DeviceEndpoint): AlertMediaCatalog
    suspend fun saveAlertProviderSettings(
        endpoint: DeviceEndpoint,
        update: AlertProviderUpdate,
    ): AlertProviderSettings
    suspend fun testAlertProviderSettings(
        endpoint: DeviceEndpoint,
        update: AlertProviderUpdate,
    ): AlertProviderTestResult
    suspend fun saveAlertAudioSettings(
        endpoint: DeviceEndpoint,
        update: AlertAudioUpdate,
    ): AlertAudioSettings
    suspend fun uploadAlertMedia(
        endpoint: DeviceEndpoint,
        kind: String,
        bytes: ByteArray,
        contentType: String,
    ): AlertMediaFile
    suspend fun resetAlertMedia(
        endpoint: DeviceEndpoint,
        kind: String,
    ): AlertMediaFile
    fun statusEvents(endpoint: DeviceEndpoint): Flow<PlayerStatus>
}
