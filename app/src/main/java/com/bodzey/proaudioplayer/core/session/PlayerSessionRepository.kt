package com.bodzey.proaudioplayer.core.session

import com.bodzey.proaudioplayer.core.api.ApiCapabilities
import com.bodzey.proaudioplayer.core.api.MixerState
import com.bodzey.proaudioplayer.core.api.MixerTarget
import com.bodzey.proaudioplayer.core.api.AudioOutputDescriptor
import com.bodzey.proaudioplayer.core.api.AlertAudioSettings
import com.bodzey.proaudioplayer.core.api.AlertAudioUpdate
import com.bodzey.proaudioplayer.core.api.AlertMediaCatalog
import com.bodzey.proaudioplayer.core.api.AlertMediaFile
import com.bodzey.proaudioplayer.core.api.AlertProviderSettings
import com.bodzey.proaudioplayer.core.api.AlertProviderTestResult
import com.bodzey.proaudioplayer.core.api.AlertProviderUpdate
import com.bodzey.proaudioplayer.core.api.PlayerAction
import com.bodzey.proaudioplayer.core.api.PlayerApiClient
import com.bodzey.proaudioplayer.core.api.PlayerFeature
import com.bodzey.proaudioplayer.core.api.QueueItem
import com.bodzey.proaudioplayer.core.api.RadioStation
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
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 0,
                replayExpirationMillis = 0,
            ),
            initialValue = PlayerSessionState.NoSelection,
        )

    fun select(deviceId: DeviceId) {
        _selectedDeviceId.value = deviceId
    }

    fun clearSelection() {
        _selectedDeviceId.value = null
    }

    suspend fun performAction(
        expectedDeviceId: DeviceId,
        action: PlayerAction,
    ) {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, "player_control")
        apiClient.playerAction(
            endpoint = connected.endpoint,
            action = action,
        )
    }

    suspend fun setMasterVolume(
        expectedDeviceId: DeviceId,
        percent: Double,
    ) {
        require(percent.isFinite() && percent in 0.0..100.0) {
            "Master volume must be between 0 and 100"
        }
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.AUDIO_MIXER)
        apiClient.setMasterVolume(
            endpoint = connected.endpoint,
            percent = percent,
        )
    }

    suspend fun setMasterMuted(
        expectedDeviceId: DeviceId,
        muted: Boolean,
    ) {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.AUDIO_MIXER)
        val db = connected.status.master.db
            ?: throw ApiCompatibilityException("Player status does not expose master dB")
        apiClient.setMasterMute(
            endpoint = connected.endpoint,
            db = db,
            muted = muted,
        )
    }

    suspend fun radioStations(
        expectedDeviceId: DeviceId,
    ): List<RadioStation> {
        val connected = connectedState(expectedDeviceId)
        return apiClient.radioStations(connected.endpoint)
    }

    suspend fun playStream(
        expectedDeviceId: DeviceId,
        url: String,
    ) {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.NETWORK_STREAMS)
        apiClient.playStream(
            endpoint = connected.endpoint,
            url = url,
        )
    }

    suspend fun stopPlayback(expectedDeviceId: DeviceId) {
        performAction(
            expectedDeviceId = expectedDeviceId,
            action = PlayerAction.Stop,
        )
    }

    suspend fun mixer(
        expectedDeviceId: DeviceId,
    ): MixerState {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.AUDIO_MIXER)
        return apiClient.mixer(connected.endpoint)
    }

    suspend fun setMixer(
        expectedDeviceId: DeviceId,
        target: MixerTarget,
        db: Double,
        muted: Boolean,
    ): MixerState {
        require(db.isFinite() && db in -60.0..0.0) {
            "Mixer level must be in -60..0 dB"
        }
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.AUDIO_MIXER)
        return apiClient.setMixer(
            endpoint = connected.endpoint,
            target = target,
            db = db,
            muted = muted,
        )
    }

    suspend fun audioOutputs(
        expectedDeviceId: DeviceId,
    ): List<AudioOutputDescriptor> {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.AUDIO_OUTPUTS)
        return apiClient.audioOutputs(connected.endpoint)
    }

    suspend fun selectAudioOutput(
        expectedDeviceId: DeviceId,
        id: String,
    ): AudioOutputDescriptor {
        require(id.isNotBlank()) {
            "Audio output ID must not be blank"
        }
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.AUDIO_OUTPUTS)
        return apiClient.selectAudioOutput(
            endpoint = connected.endpoint,
            id = id,
        )
    }

    suspend fun library(
        expectedDeviceId: DeviceId,
    ): List<String> {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.LIBRARY)
        return apiClient.library(connected.endpoint)
    }

    suspend fun refreshLibrary(expectedDeviceId: DeviceId) {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.LIBRARY)
        apiClient.refreshLibrary(connected.endpoint)
    }

    suspend fun playLibraryPath(
        expectedDeviceId: DeviceId,
        path: String,
    ) {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.LIBRARY)
        apiClient.playLibraryPath(
            endpoint = connected.endpoint,
            path = path,
        )
    }

    suspend fun playlists(
        expectedDeviceId: DeviceId,
    ): List<String> {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.PLAYLISTS)
        return apiClient.playlists(connected.endpoint)
    }

    suspend fun loadPlaylist(
        expectedDeviceId: DeviceId,
        name: String,
    ) {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.PLAYLISTS)
        apiClient.loadPlaylist(
            endpoint = connected.endpoint,
            name = name,
        )
    }

    suspend fun queue(
        expectedDeviceId: DeviceId,
    ): List<QueueItem> {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.QUEUE)
        return apiClient.queue(connected.endpoint)
    }

    suspend fun playQueueItem(
        expectedDeviceId: DeviceId,
        position: Int,
    ) {
        require(position > 0) {
            "Queue position must be positive"
        }
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.QUEUE)
        apiClient.playQueueItem(
            endpoint = connected.endpoint,
            position = position,
        )
    }

    suspend fun removeQueueItem(
        expectedDeviceId: DeviceId,
        position: Int,
    ) {
        require(position > 0) {
            "Queue position must be positive"
        }
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.QUEUE)
        apiClient.removeQueueItem(
            endpoint = connected.endpoint,
            position = position,
        )
    }

    suspend fun clearQueue(expectedDeviceId: DeviceId) {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, PlayerFeature.QUEUE)
        apiClient.clearQueue(connected.endpoint)
    }

    suspend fun alertProviderSettings(
        expectedDeviceId: DeviceId,
    ): AlertProviderSettings {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, "alert_settings")
        return apiClient.alertProviderSettings(connected.endpoint)
    }

    suspend fun alertAudioSettings(
        expectedDeviceId: DeviceId,
    ): AlertAudioSettings {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, "audio_settings")
        return apiClient.alertAudioSettings(connected.endpoint)
    }

    suspend fun alertMedia(
        expectedDeviceId: DeviceId,
    ): AlertMediaCatalog {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, "alert_media")
        return apiClient.alertMedia(connected.endpoint)
    }

    suspend fun saveAlertProviderSettings(
        expectedDeviceId: DeviceId,
        update: AlertProviderUpdate,
    ): AlertProviderSettings {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, "alert_settings")
        return apiClient.saveAlertProviderSettings(
            endpoint = connected.endpoint,
            update = update,
        )
    }

    suspend fun testAlertProviderSettings(
        expectedDeviceId: DeviceId,
        update: AlertProviderUpdate,
    ): AlertProviderTestResult {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, "alert_settings")
        return apiClient.testAlertProviderSettings(
            endpoint = connected.endpoint,
            update = update,
        )
    }

    suspend fun saveAlertAudioSettings(
        expectedDeviceId: DeviceId,
        update: AlertAudioUpdate,
    ): AlertAudioSettings {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, "audio_settings")
        return apiClient.saveAlertAudioSettings(
            endpoint = connected.endpoint,
            update = update,
        )
    }

    suspend fun uploadAlertMedia(
        expectedDeviceId: DeviceId,
        kind: String,
        bytes: ByteArray,
        contentType: String,
    ): AlertMediaFile {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, "alert_media")
        return apiClient.uploadAlertMedia(
            endpoint = connected.endpoint,
            kind = kind,
            bytes = bytes,
            contentType = contentType,
        )
    }

    suspend fun resetAlertMedia(
        expectedDeviceId: DeviceId,
        kind: String,
    ): AlertMediaFile {
        val connected = connectedState(expectedDeviceId)
        requireFeature(connected, "alert_media")
        return apiClient.resetAlertMedia(
            endpoint = connected.endpoint,
            kind = kind,
        )
    }

    private fun connectedState(): PlayerSessionState.Connected =
        state.value as? PlayerSessionState.Connected
            ?: throw IllegalStateException("Player session is not connected")

    private fun connectedState(
        expectedDeviceId: DeviceId,
    ): PlayerSessionState.Connected {
        val connected = connectedState()
        if (connected.deviceId != expectedDeviceId) {
            throw IllegalStateException("Selected player changed")
        }
        return connected
    }

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
        if (PlayerFeature.STATUS !in capabilities.features) {
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
