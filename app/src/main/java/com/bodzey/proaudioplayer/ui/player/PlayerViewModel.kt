package com.bodzey.proaudioplayer.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.Immutable
import com.bodzey.proaudioplayer.core.api.PlayerAction
import com.bodzey.proaudioplayer.core.api.PlayerStatus
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import com.bodzey.proaudioplayer.ui.AppSection
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class PlayerTimeline(
    val state: String,
    val positionSeconds: Double?,
    val durationSeconds: Double?,
    val progressPercent: Int,
)

internal fun samePlayerUiState(
    previous: PlayerSessionState,
    next: PlayerSessionState,
): Boolean {
    if (previous === next) return true
    if (previous !is PlayerSessionState.Connected ||
        next !is PlayerSessionState.Connected
    ) {
        return previous == next
    }

    return previous.deviceId == next.deviceId &&
        previous.displayName == next.displayName &&
        previous.endpoint == next.endpoint &&
        previous.capabilities == next.capabilities &&
        samePlayerUiStatus(previous.status, next.status)
}

private fun samePlayerUiStatus(
    previous: PlayerStatus,
    next: PlayerStatus,
): Boolean {
    if (previous.name != next.name ||
        previous.audioTopologyRevision != next.audioTopologyRevision ||
        previous.master != next.master ||
        previous.music != next.music ||
        previous.priority != next.priority ||
        previous.mpd != next.mpd
    ) {
        return false
    }

    val left = previous.player
    val right = next.player
    return left.source == right.source &&
        left.backend == right.backend &&
        left.state == right.state &&
        left.title == right.title &&
        left.artist == right.artist &&
        left.album == right.album &&
        left.artUrl == right.artUrl &&
        left.durationSeconds == right.durationSeconds &&
        left.controls == right.controls
}

class PlayerViewModel(
    private val sessionRepository: PlayerSessionRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val selectedDeviceId = sessionRepository.selectedDeviceId

    val state: StateFlow<PlayerSessionState> = sessionRepository.state
        .distinctUntilChanged(::samePlayerUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 0,
                replayExpirationMillis = 0,
            ),
            initialValue = sessionRepository.state.value,
        )

    val timeline: StateFlow<PlayerTimeline?> = sessionRepository.state
        .map { session ->
            (session as? PlayerSessionState.Connected)
                ?.status
                ?.player
                ?.let { player ->
                    PlayerTimeline(
                        state = player.state,
                        positionSeconds = player.positionSeconds,
                        durationSeconds = player.durationSeconds,
                        progressPercent = player.progressPercent,
                    )
                }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 0,
                replayExpirationMillis = 0,
            ),
            initialValue = (sessionRepository.state.value as? PlayerSessionState.Connected)
                ?.status
                ?.player
                ?.let { player ->
                    PlayerTimeline(
                        state = player.state,
                        positionSeconds = player.positionSeconds,
                        durationSeconds = player.durationSeconds,
                        progressPercent = player.progressPercent,
                    )
                },
        )

    private val _pendingAction = MutableStateFlow<PlayerAction?>(null)
    val pendingAction: StateFlow<PlayerAction?> = _pendingAction.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _section = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SECTION)
            ?.let { value -> runCatching { AppSection.valueOf(value) }.getOrNull() }
            ?: AppSection.Player,
    )
    val section: StateFlow<AppSection> = _section.asStateFlow()

    private val _masterMuteBusy = MutableStateFlow(false)
    val masterMuteBusy: StateFlow<Boolean> = _masterMuteBusy.asStateFlow()

    private val _masterVolumeOverride = MutableStateFlow<Double?>(null)
    val masterVolumeOverride: StateFlow<Double?> = _masterVolumeOverride.asStateFlow()

    private val _lastSentMasterVolume = MutableStateFlow<Double?>(null)

    private val masterVolumeRequests =
        Channel<MasterVolumeRequest>(capacity = Channel.CONFLATED)

    init {
        savedStateHandle.get<String>(KEY_SELECTED_DEVICE_ID)
            ?.let { value -> runCatching { DeviceId.parse(value) }.getOrNull() }
            ?.let(sessionRepository::select)

        viewModelScope.launch {
            for (request in masterVolumeRequests) {
                if (selectedDeviceId.value != request.deviceId) {
                    continue
                }

                try {
                    sessionRepository.setMasterVolume(
                        expectedDeviceId = request.deviceId,
                        percent = request.percent,
                    )
                    if (selectedDeviceId.value == request.deviceId) {
                        _lastSentMasterVolume.value = request.percent
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (selectedDeviceId.value == request.deviceId &&
                        _masterVolumeOverride.value?.let { value ->
                            abs(value - request.percent) < 0.01
                        } == true
                    ) {
                        _masterVolumeOverride.value = null
                        _actionError.value =
                            error.message ?: "Не вдалося змінити гучність MASTER"
                    }
                }

                delay(MASTER_VOLUME_REQUEST_INTERVAL_MILLIS)
            }
        }

        viewModelScope.launch {
            sessionRepository.state.collect { sessionState ->
                val target = _masterVolumeOverride.value ?: return@collect
                val sentTarget = _lastSentMasterVolume.value ?: return@collect
                val connected = sessionState as? PlayerSessionState.Connected
                    ?: return@collect

                if (abs(sentTarget - target) <= MASTER_VOLUME_TARGET_TOLERANCE_PERCENT &&
                    abs(connected.status.master.volumePercent - target) <=
                    MASTER_VOLUME_ACK_TOLERANCE_PERCENT
                ) {
                    _masterVolumeOverride.value = null
                    _lastSentMasterVolume.value = null
                }
            }
        }
    }

    fun rememberSelectedDevice(deviceId: DeviceId) {
        if (sessionRepository.selectedDeviceId.value != deviceId) {
            sessionRepository.select(deviceId)
        }
        savedStateHandle[KEY_SELECTED_DEVICE_ID] = deviceId.value
    }

    fun performAction(action: PlayerAction) {
        if (_pendingAction.value != null) {
            return
        }
        val deviceId = selectedDeviceId.value ?: return
        viewModelScope.launch {
            _pendingAction.value = action
            _actionError.value = null
            try {
                sessionRepository.performAction(
                    expectedDeviceId = deviceId,
                    action = action,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _actionError.value = error.message ?: "Не вдалося виконати команду"
            } finally {
                _pendingAction.value = null
            }
        }
    }

    fun selectSection(section: AppSection) {
        _section.value = section
        savedStateHandle[KEY_SECTION] = section.name
    }

    fun setMasterVolume(percent: Double) {
        val deviceId = selectedDeviceId.value ?: return
        val target = percent.coerceIn(0.0, 100.0)

        _actionError.value = null
        _masterVolumeOverride.value = target
        _lastSentMasterVolume.value = null
        masterVolumeRequests.trySend(
            MasterVolumeRequest(
                deviceId = deviceId,
                percent = target,
            ),
        )
    }

    fun setMasterMuted(muted: Boolean) {
        if (_masterMuteBusy.value) {
            return
        }
        val deviceId = selectedDeviceId.value ?: return
        viewModelScope.launch {
            _masterMuteBusy.value = true
            _actionError.value = null
            try {
                sessionRepository.setMasterMuted(
                    expectedDeviceId = deviceId,
                    muted = muted,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _actionError.value = error.message ?: "Не вдалося змінити MASTER"
            } finally {
                _masterMuteBusy.value = false
            }
        }
    }

    fun close() {
        _actionError.value = null
        _masterVolumeOverride.value = null
        _lastSentMasterVolume.value = null
        _section.value = AppSection.Player
        savedStateHandle.remove<String>(KEY_SELECTED_DEVICE_ID)
        savedStateHandle[KEY_SECTION] = AppSection.Player.name
        sessionRepository.clearSelection()
    }

    private data class MasterVolumeRequest(
        val deviceId: DeviceId,
        val percent: Double,
    )

    companion object {
        private const val MASTER_VOLUME_REQUEST_INTERVAL_MILLIS = 75L
        private const val MASTER_VOLUME_ACK_TOLERANCE_PERCENT = 0.75
        private const val MASTER_VOLUME_TARGET_TOLERANCE_PERCENT = 0.01
        private const val KEY_SELECTED_DEVICE_ID = "selected_device_id"
        private const val KEY_SECTION = "player_section"

        fun factory(
            sessionRepository: PlayerSessionRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlayerViewModel(
                        sessionRepository = sessionRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
