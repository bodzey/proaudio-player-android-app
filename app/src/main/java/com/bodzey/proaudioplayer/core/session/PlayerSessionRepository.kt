package com.bodzey.proaudioplayer.core.session

import com.bodzey.proaudioplayer.core.api.PlayerApiClient
import com.bodzey.proaudioplayer.core.device.AvailableDevice
import com.bodzey.proaudioplayer.core.device.DeviceRepository
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

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

    private fun connect(device: AvailableDevice): Flow<PlayerSessionState> = flow {
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
                capabilitiesApiMajorVersion = capabilities.apiMajorVersion,
                features = capabilities.features,
            )
            val status = apiClient.status(resolved.endpoint)

            emit(
                PlayerSessionState.Connected(
                    deviceId = device.id,
                    displayName = device.displayName,
                    endpoint = resolved.endpoint,
                    capabilities = capabilities,
                    status = status,
                ),
            )
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
        }
    }

    private fun requireCompatibleCapabilities(
        expectedApiMajorVersion: Int,
        capabilitiesApiMajorVersion: Int,
        features: Set<String>,
    ) {
        if (capabilitiesApiMajorVersion != expectedApiMajorVersion) {
            throw ApiCompatibilityException(
                "Capabilities report API v" + capabilitiesApiMajorVersion +
                    ", expected v" + expectedApiMajorVersion,
            )
        }
        if ("status" !in features) {
            throw ApiCompatibilityException("Player API does not advertise status support")
        }
    }

    private data class ConnectionTarget(
        val selectedId: DeviceId,
        val device: AvailableDevice?,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ConnectionTarget) return false
            if (selectedId != other.selectedId) return false

            val left = device?.connectionIdentity()
            val right = other.device?.connectionIdentity()
            return left == right
        }

        override fun hashCode(): Int =
            31 * selectedId.hashCode() + (device?.connectionIdentity()?.hashCode() ?: 0)
    }

    private data class ConnectionIdentity(
        val id: DeviceId,
        val displayName: String,
        val apiMajorVersion: Int,
        val endpoints: Set<DeviceEndpoint>,
    )

    private fun AvailableDevice.connectionIdentity(): ConnectionIdentity =
        ConnectionIdentity(
            id = id,
            displayName = displayName,
            apiMajorVersion = apiMajorVersion,
            endpoints = endpoints,
        )
}
