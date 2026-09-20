package com.bodzey.proaudioplayer.ui.output

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.api.AudioOutputDescriptor
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OutputViewModel(
    private val sessionRepository: PlayerSessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OutputUiState())
    val uiState: StateFlow<OutputUiState> = _uiState.asStateFlow()

    fun ensureLoaded(force: Boolean = false) {
        val deviceId = sessionRepository.selectedDeviceId.value ?: return
        val current = _uiState.value
        if (!force &&
            current.deviceId == deviceId &&
            (current.loading || current.outputs.isNotEmpty())
        ) {
            return
        }

        _uiState.value = current.copy(
            deviceId = deviceId,
            loading = true,
            outputs = if (current.deviceId == deviceId) {
                current.outputs
            } else {
                emptyList()
            },
            pendingOutputId = null,
            error = null,
        )

        viewModelScope.launch {
            try {
                val outputs = sessionRepository.audioOutputs(deviceId)
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        outputs = outputs,
                        error = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = error.message ?: "Не вдалося отримати аудіовиходи",
                    )
                }
            }
        }
    }

    fun select(output: AudioOutputDescriptor) {
        val deviceId = sessionRepository.selectedDeviceId.value ?: return
        val current = _uiState.value
        if (current.deviceId != deviceId ||
            current.pendingOutputId != null ||
            output.selected ||
            !output.available
        ) {
            return
        }

        _uiState.value = current.copy(
            pendingOutputId = output.id,
            error = null,
        )

        viewModelScope.launch {
            try {
                sessionRepository.selectAudioOutput(
                    expectedDeviceId = deviceId,
                    id = output.id,
                )
                val outputs = sessionRepository.audioOutputs(deviceId)
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        outputs = outputs,
                        error = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Не вдалося змінити аудіовихід",
                    )
                }
            } finally {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        pendingOutputId = null,
                    )
                }
            }
        }
    }

    fun refresh() {
        ensureLoaded(force = true)
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
                    OutputViewModel(sessionRepository)
                }
            }
    }
}
