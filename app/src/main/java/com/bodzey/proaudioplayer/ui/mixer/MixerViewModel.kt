package com.bodzey.proaudioplayer.ui.mixer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.api.MixerTarget
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MixerViewModel(
    private val sessionRepository: PlayerSessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MixerUiState())
    val uiState: StateFlow<MixerUiState> = _uiState.asStateFlow()

    fun ensureLoaded(force: Boolean = false) {
        val deviceId = sessionRepository.selectedDeviceId.value ?: return
        val current = _uiState.value
        if (!force &&
            current.deviceId == deviceId &&
            (current.loading || current.mixer != null)
        ) {
            return
        }

        _uiState.value = current.copy(
            deviceId = deviceId,
            loading = true,
            mixer = current.mixer.takeIf { current.deviceId == deviceId },
            pendingTarget = null,
            error = null,
        )

        viewModelScope.launch {
            try {
                val mixer = sessionRepository.mixer(deviceId)
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        mixer = mixer,
                        error = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = error.message ?: "Не вдалося отримати мікшер",
                    )
                }
            }
        }
    }

    fun setLevel(
        target: MixerTarget,
        db: Double,
    ) {
        val mixer = _uiState.value.mixer ?: return
        val muted = when (target) {
            MixerTarget.Music -> mixer.music.muted
            MixerTarget.Alert -> mixer.alert.muted
        }
        mutate(
            target = target,
            db = db,
            muted = muted,
        )
    }

    fun setMuted(
        target: MixerTarget,
        muted: Boolean,
    ) {
        val mixer = _uiState.value.mixer ?: return
        val db = when (target) {
            MixerTarget.Music -> mixer.music.db
            MixerTarget.Alert -> mixer.alert.db
        } ?: return
        mutate(
            target = target,
            db = db,
            muted = muted,
        )
    }

    fun refresh() {
        ensureLoaded(force = true)
    }

    private fun mutate(
        target: MixerTarget,
        db: Double,
        muted: Boolean,
    ) {
        val deviceId = sessionRepository.selectedDeviceId.value ?: return
        if (_uiState.value.pendingTarget != null) return

        _uiState.value = _uiState.value.copy(
            pendingTarget = target,
            error = null,
        )

        viewModelScope.launch {
            try {
                val mixer = sessionRepository.setMixer(
                    expectedDeviceId = deviceId,
                    target = target,
                    db = db.coerceIn(-60.0, 0.0),
                    muted = muted,
                )
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        mixer = mixer,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Не вдалося змінити мікшер",
                    )
                }
            } finally {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        pendingTarget = null,
                    )
                }
            }
        }
    }

    private fun isCurrentDevice(deviceId: DeviceId): Boolean =
        sessionRepository.selectedDeviceId.value == deviceId &&
            _uiState.value.deviceId == deviceId

    companion object {
        fun factory(
            sessionRepository: PlayerSessionRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MixerViewModel(sessionRepository)
                }
            }
    }
}
