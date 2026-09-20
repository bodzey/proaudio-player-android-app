package com.bodzey.proaudioplayer.core.session

import com.bodzey.proaudioplayer.core.api.ApiCapabilities
import com.bodzey.proaudioplayer.core.api.PlayerStatus
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId

sealed interface PlayerSessionState {
    data object NoSelection : PlayerSessionState

    data class Connecting(
        val deviceId: DeviceId,
        val displayName: String,
    ) : PlayerSessionState

    data class Connected(
        val deviceId: DeviceId,
        val displayName: String,
        val endpoint: DeviceEndpoint,
        val capabilities: ApiCapabilities,
        val status: PlayerStatus,
    ) : PlayerSessionState

    data class Offline(
        val deviceId: DeviceId,
    ) : PlayerSessionState

    data class Failed(
        val deviceId: DeviceId,
        val displayName: String,
        val message: String,
    ) : PlayerSessionState
}
