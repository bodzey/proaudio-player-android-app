package com.bodzey.proaudioplayer.core.api

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint

interface PlayerApiClient {
    suspend fun health(endpoint: DeviceEndpoint): ApiHealth
    suspend fun capabilities(endpoint: DeviceEndpoint): ApiCapabilities
    suspend fun status(endpoint: DeviceEndpoint): PlayerStatus
}
