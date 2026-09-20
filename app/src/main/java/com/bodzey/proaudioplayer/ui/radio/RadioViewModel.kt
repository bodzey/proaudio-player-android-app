package com.bodzey.proaudioplayer.ui.radio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.api.NetworkStreamValidator
import com.bodzey.proaudioplayer.core.api.RadioStation
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RadioViewModel(
    private val sessionRepository: PlayerSessionRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RadioUiState(
            deviceId = sessionRepository.selectedDeviceId.value,
            customUrl = sessionRepository.selectedDeviceId.value
                ?.let(::restoredUrlFor)
                .orEmpty(),
        ),
    )
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

        val sameDevice = current.deviceId == deviceId
        val customUrl = if (sameDevice) current.customUrl else restoredUrlFor(deviceId)

        _uiState.value = current.copy(
            deviceId = deviceId,
            stations = if (sameDevice) current.stations else emptyList(),
            loading = true,
            loadError = null,
            customUrl = customUrl,
            pendingUrl = null,
            feedback = null,
        )
        persistCustomUrl(deviceId, customUrl)

        viewModelScope.launch {
            try {
                val stations = sessionRepository.radioStations(
                    expectedDeviceId = deviceId,
                )
                if (isCurrentDevice(deviceId)) {
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        loadError = error.message
                            ?: "Не вдалося завантажити каталог радіо",
                    )
                }
            }
        }
    }

    fun refresh() {
        ensureLoaded(force = true)
    }

    fun setCustomUrl(value: String) {
        val deviceId = sessionRepository.selectedDeviceId.value ?: return
        _uiState.value = _uiState.value.copy(
            deviceId = deviceId,
            customUrl = value,
            feedback = null,
        )
        persistCustomUrl(deviceId, value)
    }

    fun toggleStation(station: RadioStation) {
        val connected = connectedState() ?: return
        if (!canStartPlayback(connected)) return
        if (_uiState.value.pendingUrl != null) return

        val deviceId = connected.deviceId
        val activeUrl = activeRadioStreamUrl(connected.status)
        runStreamAction(
            deviceId = deviceId,
            url = station.url,
            successMessage = if (isSameRadioStream(activeUrl, station.url)) {
                "Зупинено: " + station.name
            } else {
                "Запущено: " + station.name
            },
        ) {
            if (isSameRadioStream(activeUrl, station.url)) {
                sessionRepository.stopPlayback(deviceId)
            } else {
                sessionRepository.playStream(
                    expectedDeviceId = deviceId,
                    url = station.url,
                )
            }
        }
    }

    fun playCustomStream() {
        val connected = connectedState() ?: return
        if (!canStartPlayback(connected)) return
        if (_uiState.value.pendingUrl != null) return

        val deviceId = connected.deviceId
        val url = try {
            NetworkStreamValidator.normalize(_uiState.value.customUrl)
        } catch (error: IllegalArgumentException) {
            _uiState.value = _uiState.value.copy(
                feedback = RadioFeedback(
                    message = error.message ?: "Некоректна адреса потоку",
                    isError = true,
                ),
            )
            return
        }

        runStreamAction(
            deviceId = deviceId,
            url = url,
            successMessage = "Власний потік запущено",
        ) {
            sessionRepository.playStream(
                expectedDeviceId = deviceId,
                url = url,
            )
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

    private fun canStartPlayback(
        connected: PlayerSessionState.Connected,
    ): Boolean {
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
        deviceId: DeviceId,
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
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        feedback = RadioFeedback(
                            message = successMessage,
                            isError = false,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        feedback = RadioFeedback(
                            message = error.message
                                ?: "Не вдалося змінити радіопотік",
                            isError = true,
                        ),
                    )
                }
            } finally {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        pendingUrl = null,
                    )
                }
            }
        }
    }

    private fun isCurrentDevice(deviceId: DeviceId): Boolean =
        sessionRepository.selectedDeviceId.value == deviceId &&
            _uiState.value.deviceId == deviceId

    private fun restoredUrlFor(deviceId: DeviceId): String {
        val savedDeviceId = savedStateHandle
            .get<String>(KEY_CUSTOM_URL_DEVICE_ID)
            ?.let { value -> runCatching { DeviceId.parse(value) }.getOrNull() }
        return if (savedDeviceId == deviceId) {
            savedStateHandle.get<String>(KEY_CUSTOM_URL).orEmpty()
        } else {
            ""
        }
    }

    private fun persistCustomUrl(
        deviceId: DeviceId,
        value: String,
    ) {
        savedStateHandle[KEY_CUSTOM_URL_DEVICE_ID] = deviceId.value
        savedStateHandle[KEY_CUSTOM_URL] = value
    }

    companion object {
        private const val KEY_CUSTOM_URL = "radio_custom_url"
        private const val KEY_CUSTOM_URL_DEVICE_ID = "radio_custom_url_device_id"

        fun factory(
            sessionRepository: PlayerSessionRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    RadioViewModel(
                        sessionRepository = sessionRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
