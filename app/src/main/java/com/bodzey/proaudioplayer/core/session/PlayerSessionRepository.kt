package com.bodzey.proaudioplayer.core.session

import com.bodzey.proaudioplayer.core.api.ApiCapabilities
import com.bodzey.proaudioplayer.core.api.PlayerAction
import com.bodzey.proaudioplayer.core.api.PlayerApiClient
import com.bodzey.proaudioplayer.core.device.AvailableDevice
import com.bodzey.proaudioplayer.core.device.DeviceRepository
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSessionRepository(
    deviceRepository: DeviceRepository,
    private val endpointResolver: EndpointResolver,
    private val apiClient: PlayerApiClient,
    scope: CoroutineScope,
) {
    private val _selectedDeviceId = MutableStateFlow<DeviceId?>(null)
    val selectedDeviceId: StateFlow<DeviceId?> = _selectedDeviceId.asStateFlow()

    private val target: Flow<ConnectionTarget?> = combine(
        selectedDeviceId,
        deviceRepository.devices,
    ) { selectedId, devices ->
        if (selectedId == null) {
            null
        } else {
            val device = devices.firstOrNull { candidate -> candidate.id == selectedId }
            ConnectionTarget(
                selectedId = selectedId,
                device = device,
                connectionIdentity = device?.toConnectionIdentity(),
            )
        }
    }.distinctUntilChanged()

    val state: StateFlow<PlayerSessionState> = target
        .flatMapLatest { target ->
            when {
                target == null -> flowOf(PlayerSessionState.NoSelection)
                target.device == null -> flowOf(
                    PlayerSessionState.Offline(target.selectedId),
                )
                else -> connect(target.device)
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            initialValue = PlayerSessionState.NoSelection,
        )

    fun select(deviceId: DeviceId) {
        _selectedDeviceId.value = deviceId
    }

    fun clearSelection() {
        _selectedDeviceId.value = null
    }

    suspend fun performAction(action: PlayerAction) {
        val connected = connectedState()
        requireFeature(connected, "player_control")
        apiClient.playerAction(
            endpoint = connected.endpoint,
            action = action,
        )
    }

    suspend fun setMasterVolume(percent: Double) {
        require(percent.isFinite() && percent in 0.0..100.0) {
            "Master volume must be between 0 and 100"
        }
        val connected = connectedState()
        requireFeature(connected, "audio_mixer")
        apiClient.setMasterVolume(
            endpoint = connected.endpoint,
            percent = percent,
        )
    }

    suspend fun setMasterMuted(muted: Boolean) {
        val connected = connectedState()
        requireFeature(connected, "audio_mixer")
        val db = connected.status.master.db
            ?: throw ApiCompatibilityException("Player status does not expose master dB")
        apiClient.setMasterMute(
            endpoint = connected.endpoint,
            db = db,
            muted = muted,
        )
    }

    private fun connectedState(): PlayerSessionState.Connected =
        state.value as? PlayerSessionState.Connected
            ?: throw IllegalStateException("Player session is not connected")

    private fun requireFeature(
        connected: PlayerSessionState.Connected,
        feature: String,
    ) {
        if (feature !in connected.capabilities.features) {
            throw ApiCompatibilityException(
                "Player API does not advertise " + feature + " support",
            )
        }
    }

    private fun connect(device: AvailableDevice): Flow<PlayerSessionState> = flow {
        while (currentCoroutineContext().isActive) {
            emit(
                PlayerSessionState.Connecting(
                    deviceId = device.id,
                    displayName = device.displayName,
                ),
            )

            try {
                val resolved = endpointResolver.resolve(device)
                val capabilities = apiClient.capabilities(resolved.endpoint)
                requireCompatibleCapabilities(
                    expectedApiMajorVersion = device.apiMajorVersion,
                    capabilities = capabilities,
                )
                var status = apiClient.status(resolved.endpoint)

                emit(
                    connectedState(
                        device = device,
                        endpoint = resolved.endpoint,
                        capabilities = capabilities,
                        status = status,
                    ),
                )

                if (capabilities.eventTransport == "sse") {
                    apiClient.statusEvents(resolved.endpoint).collect { eventStatus ->
                        status = eventStatus
                        emit(
                            connectedState(
                                device = device,
                                endpoint = resolved.endpoint,
                                capabilities = capabilities,
                                status = status,
                            ),
                        )
                    }
                    throw IllegalStateException("Player event stream closed")
                }

                while (currentCoroutineContext().isActive) {
                    delay(2_000)
                    status = apiClient.status(resolved.endpoint)
                    emit(
                        connectedState(
                            device = device,
                            endpoint = resolved.endpoint,
                            capabilities = capabilities,
                            status = status,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emit(
                    PlayerSessionState.Failed(
                        deviceId = device.id,
                        displayName = device.displayName,
                        message = error.message ?: "Unable to connect to player",
                    ),
                )
                delay(RECONNECT_DELAY_MILLIS)
            }
        }
    }

    private fun connectedState(
        device: AvailableDevice,
        endpoint: DeviceEndpoint,
        capabilities: ApiCapabilities,
        status: com.bodzey.proaudioplayer.core.api.PlayerStatus,
    ): PlayerSessionState.Connected =
        PlayerSessionState.Connected(
            deviceId = device.id,
            displayName = device.displayName,
            endpoint = endpoint,
            capabilities = capabilities,
            status = status,
        )

    private fun requireCompatibleCapabilities(
        expectedApiMajorVersion: Int,
        capabilities: ApiCapabilities,
    ) {
        if (capabilities.apiMajorVersion != expectedApiMajorVersion) {
            throw ApiCompatibilityException(
                "Capabilities report API v" + capabilities.apiMajorVersion +
                    ", expected v" + expectedApiMajorVersion,
            )
        }
        if ("status" !in capabilities.features) {
            throw ApiCompatibilityException("Player API does not advertise status support")
        }
    }

    private class ConnectionTarget(
        val selectedId: DeviceId,
        val device: AvailableDevice?,
        private val connectionIdentity: ConnectionIdentity?,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ConnectionTarget) return false
            return selectedId == other.selectedId &&
                connectionIdentity == other.connectionIdentity
        }

        override fun hashCode(): Int =
            31 * selectedId.hashCode() + (connectionIdentity?.hashCode() ?: 0)
    }

    private data class ConnectionIdentity(
        val id: DeviceId,
        val displayName: String,
        val apiMajorVersion: Int,
        val endpoints: Set<DeviceEndpoint>,
    )

    private fun AvailableDevice.toConnectionIdentity(): ConnectionIdentity =
        ConnectionIdentity(
            id = id,
            displayName = displayName,
            apiMajorVersion = apiMajorVersion,
            endpoints = endpoints,
        )

    companion object {
        private const val RECONNECT_DELAY_MILLIS = 1_500L
    }
}
