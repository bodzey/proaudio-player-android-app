package com.bodzey.proaudioplayer.core.api

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import kotlinx.coroutines.flow.Flow

interface PlayerApiClient {
    suspend fun health(endpoint: DeviceEndpoint): ApiHealth
    suspend fun capabilities(endpoint: DeviceEndpoint): ApiCapabilities
    suspend fun status(endpoint: DeviceEndpoint): PlayerStatus
    suspend fun playerAction(endpoint: DeviceEndpoint, action: PlayerAction)
    fun statusEvents(endpoint: DeviceEndpoint): Flow<PlayerStatus>
}
