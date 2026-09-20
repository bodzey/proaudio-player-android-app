package com.bodzey.proaudioplayer.ui.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.api.RadioStation
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RadioViewModel(
    private val sessionRepository: PlayerSessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()

    fun ensureLoaded(force: Boolean = false) {
        val deviceId = sessionRepository.selectedDeviceId.value ?: return
        val current = _uiState.value

        if (!force &&
            current.deviceId == deviceId &&
            (current.loading || current.stations.isNotEmpty())
        ) {
            return
        }

        val preservedStations =
            if (current.deviceId == deviceId) current.stations else emptyList()
        val preservedCustomUrl =
            if (current.deviceId == deviceId) current.customUrl else ""

        _uiState.value = current.copy(
            deviceId = deviceId,
            stations = preservedStations,
            loading = true,
            loadError = null,
            customUrl = preservedCustomUrl,
            pendingUrl = null,
            feedback = null,
        )

        viewModelScope.launch {
            try {
                val stations = sessionRepository.radioStations()
                if (sessionRepository.selectedDeviceId.value == deviceId) {
                    _uiState.value = _uiState.value.copy(
                        deviceId = deviceId,
                        stations = stations,
                        loading = false,
                        loadError = if (stations.isEmpty()) {
                            "Каталог не повернув жодної станції"
                        } else {
                            null
                        },
                    )
                }
            } catch (error: Exception) {
                if (sessionRepository.selectedDeviceId.value == deviceId) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        loadError = error.message ?: "Не вдалося завантажити каталог радіо",
                    )
                }
            }
        }
    }

    fun refresh() {
        ensureLoaded(force = true)
    }

    fun setCustomUrl(value: String) {
        _uiState.value = _uiState.value.copy(
            customUrl = value,
            feedback = null,
        )
    }

    fun toggleStation(station: RadioStation) {
        val connected = connectedState() ?: return
        if (!canStartPlayback(connected)) return
        if (_uiState.value.pendingUrl != null) return

        val activeUrl = activeRadioStreamUrl(connected.status)
        runStreamAction(
            url = station.url,
            successMessage = if (isSameRadioStream(activeUrl, station.url)) {
                "Зупинено: " + station.name
            } else {
                "Запущено: " + station.name
            },
        ) {
            if (isSameRadioStream(activeUrl, station.url)) {
                sessionRepository.stopPlayback()
            } else {
                sessionRepository.playStream(station.url)
            }
        }
    }

    fun playCustomStream() {
        val connected = connectedState() ?: return
        if (!canStartPlayback(connected)) return
        if (_uiState.value.pendingUrl != null) return

        val url = _uiState.value.customUrl.trim()
        if (url.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                feedback = RadioFeedback(
                    message = "Вкажіть адресу аудіопотоку",
                    isError = true,
                ),
            )
            return
        }

        runStreamAction(
            url = url,
            successMessage = "Власний потік запущено",
        ) {
            sessionRepository.playStream(url)
        }
    }

    private fun connectedState(): PlayerSessionState.Connected? {
        val connected = sessionRepository.state.value as? PlayerSessionState.Connected
        if (connected == null) {
            _uiState.value = _uiState.value.copy(
                feedback = RadioFeedback(
                    message = "Плеєр недоступний",
                    isError = true,
                ),
            )
        }
        return connected
    }

    private fun canStartPlayback(connected: PlayerSessionState.Connected): Boolean {
        if (connected.status.priority.blocking) {
            _uiState.value = _uiState.value.copy(
                feedback = RadioFeedback(
                    message = "Запуск потоку заблоковано активним пріоритетним оповіщенням",
                    isError = true,
                ),
            )
            return false
        }
        if ("network_streams" !in connected.capabilities.features) {
            _uiState.value = _uiState.value.copy(
                feedback = RadioFeedback(
                    message = "Цей плеєр не підтримує мережеві потоки",
                    isError = true,
                ),
            )
            return false
        }
        return true
    }

    private fun runStreamAction(
        url: String,
        successMessage: String,
        block: suspend () -> Unit,
    ) {
        _uiState.value = _uiState.value.copy(
            pendingUrl = url,
            feedback = null,
        )
        viewModelScope.launch {
            try {
                block()
                _uiState.value = _uiState.value.copy(
                    feedback = RadioFeedback(
                        message = successMessage,
                        isError = false,
                    ),
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    feedback = RadioFeedback(
                        message = error.message ?: "Не вдалося змінити радіопотік",
                        isError = true,
                    ),
                )
            } finally {
                _uiState.value = _uiState.value.copy(
                    pendingUrl = null,
                )
            }
        }
    }

    companion object {
        fun factory(
            sessionRepository: PlayerSessionRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    RadioViewModel(sessionRepository)
                }
            }
    }
}
