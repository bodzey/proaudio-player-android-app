package com.bodzey.proaudioplayer.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class AlertsViewModel(
    private val sessionRepository: PlayerSessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    fun ensureLoaded(force: Boolean = false) {
        val deviceId = sessionRepository.selectedDeviceId.value ?: return
        val current = _uiState.value

        if (!force &&
            current.deviceId == deviceId &&
            (current.loading ||
                current.provider != null ||
                current.audio != null ||
                current.media != null)
        ) {
            return
        }

        val sameDevice = current.deviceId == deviceId
        _uiState.value = current.copy(
            deviceId = deviceId,
            loading = true,
            provider = if (sameDevice) current.provider else null,
            audio = if (sameDevice) current.audio else null,
            media = if (sameDevice) current.media else null,
            loadError = null,
        )

        viewModelScope.launch {
            val result = supervisorScope {
                val provider = async { capture { sessionRepository.alertProviderSettings() } }
                val audio = async { capture { sessionRepository.alertAudioSettings() } }
                val media = async { capture { sessionRepository.alertMedia() } }
                Triple(provider.await(), audio.await(), media.await())
            }

            if (sessionRepository.selectedDeviceId.value != deviceId) {
                return@launch
            }

            val errors = buildList {
                result.first.exceptionOrNull()?.message?.let { add("API тривог: " + it) }
                result.second.exceptionOrNull()?.message?.let { add("аудіопараметри: " + it) }
                result.third.exceptionOrNull()?.message?.let { add("файли сповіщень: " + it) }
            }

            _uiState.value = _uiState.value.copy(
                deviceId = deviceId,
                loading = false,
                provider = result.first.getOrNull() ?: _uiState.value.provider,
                audio = result.second.getOrNull() ?: _uiState.value.audio,
                media = result.third.getOrNull() ?: _uiState.value.media,
                loadError = errors.takeIf { it.isNotEmpty() }?.joinToString("; "),
            )
        }
    }

    fun refresh() {
        ensureLoaded(force = true)
    }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }

    companion object {
        fun factory(
            sessionRepository: PlayerSessionRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AlertsViewModel(sessionRepository)
                }
            }
    }
}
