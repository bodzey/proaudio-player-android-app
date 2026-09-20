package com.bodzey.proaudioplayer.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.api.AlertAudioSettings
import com.bodzey.proaudioplayer.core.api.AlertAudioUpdate
import com.bodzey.proaudioplayer.core.api.AlertMediaCatalog
import com.bodzey.proaudioplayer.core.api.AlertMediaFile
import com.bodzey.proaudioplayer.core.api.AlertProviderSettings
import com.bodzey.proaudioplayer.core.api.AlertProviderUpdate
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import com.bodzey.proaudioplayer.data.media.AlertMediaImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class AlertsViewModel(
    private val sessionRepository: PlayerSessionRepository,
    private val mediaImporter: AlertMediaImporter,
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
        _uiState.value = if (sameDevice) {
            current.copy(
                loading = true,
                loadError = null,
            )
        } else {
            AlertsUiState(
                deviceId = deviceId,
                loading = true,
            )
        }

        viewModelScope.launch {
            val result = supervisorScope {
                val provider = async { capture { sessionRepository.alertProviderSettings() } }
                val audio = async { capture { sessionRepository.alertAudioSettings() } }
                val media = async { capture { sessionRepository.alertMedia() } }
                Triple(provider.await(), audio.await(), media.await())
            }

            if (!isCurrentDevice(deviceId)) {
                return@launch
            }

            val currentState = _uiState.value
            val provider = result.first.getOrNull() ?: currentState.provider
            val audio = result.second.getOrNull() ?: currentState.audio
            val media = result.third.getOrNull() ?: currentState.media
            val errors = buildList {
                result.first.exceptionOrNull()?.message?.let { add("API тривог: " + it) }
                result.second.exceptionOrNull()?.message?.let { add("аудіопараметри: " + it) }
                result.third.exceptionOrNull()?.message?.let { add("файли сповіщень: " + it) }
            }

            _uiState.value = currentState.copy(
                deviceId = deviceId,
                loading = false,
                provider = provider,
                providerForm = when {
                    currentState.providerDirty -> currentState.providerForm
                    provider != null -> provider.toForm()
                    else -> null
                },
                audio = audio,
                audioForm = when {
                    currentState.audioDirty -> currentState.audioForm
                    audio != null -> audio.toForm()
                    else -> null
                },
                media = media,
                loadError = errors.takeIf { it.isNotEmpty() }?.joinToString("; "),
            )
        }
    }

    fun refresh() {
        ensureLoaded(force = true)
    }

    fun updateProviderForm(form: AlertProviderForm) {
        _uiState.value = _uiState.value.copy(
            providerForm = form,
            providerDirty = true,
            providerMessage = null,
        )
    }

    fun updateAudioForm(form: AlertAudioForm) {
        _uiState.value = _uiState.value.copy(
            audioForm = form,
            audioDirty = true,
            audioMessage = null,
        )
    }

    fun saveProvider() {
        if (_uiState.value.busyAction != null) return
        val deviceId = currentDeviceId() ?: return
        val form = _uiState.value.providerForm ?: return
        val update = parseProviderUpdate(form) ?: return

        _uiState.value = _uiState.value.copy(
            busyAction = AlertsBusyAction.ProviderSave,
            providerMessage = null,
        )

        viewModelScope.launch {
            try {
                val saved = sessionRepository.saveAlertProviderSettings(
                    expectedDeviceId = deviceId,
                    update = update,
                )
                if (!isCurrentDevice(deviceId)) return@launch
                _uiState.value = _uiState.value.copy(
                    provider = saved,
                    providerForm = saved.toForm(),
                    providerDirty = false,
                    providerMessage = AlertsMessage(
                        text = "Налаштування API збережено",
                        isError = false,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(deviceId)) {
                    setProviderError(error, "Не вдалося зберегти налаштування API")
                }
            } finally {
                clearBusyIfCurrent(deviceId)
            }
        }
    }

    fun testProvider() {
        if (_uiState.value.busyAction != null) return
        val deviceId = currentDeviceId() ?: return
        val form = _uiState.value.providerForm ?: return
        val update = parseProviderUpdate(form) ?: return

        _uiState.value = _uiState.value.copy(
            busyAction = AlertsBusyAction.ProviderTest,
            providerMessage = null,
        )

        viewModelScope.launch {
            try {
                val result = sessionRepository.testAlertProviderSettings(
                    expectedDeviceId = deviceId,
                    update = update,
                )
                if (!isCurrentDevice(deviceId)) return@launch
                _uiState.value = _uiState.value.copy(
                    providerMessage = AlertsMessage(
                        text = if (result.active) {
                            "API доступний. Для UID " +
                                result.locationUid +
                                " зараз активна тривога."
                        } else {
                            "API доступний. Для UID " +
                                result.locationUid +
                                " зараз відбій."
                        },
                        isError = false,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(deviceId)) {
                    setProviderError(error, "Помилка перевірки API тривог")
                }
            } finally {
                clearBusyIfCurrent(deviceId)
            }
        }
    }

    fun saveAudio() {
        if (_uiState.value.busyAction != null) return
        val deviceId = currentDeviceId() ?: return
        val form = _uiState.value.audioForm ?: return
        val update = try {
            form.toUpdate()
        } catch (error: IllegalArgumentException) {
            _uiState.value = _uiState.value.copy(
                audioMessage = AlertsMessage(
                    text = error.message ?: "Некоректні параметри аудіо",
                    isError = true,
                ),
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            busyAction = AlertsBusyAction.AudioSave,
            audioMessage = null,
        )

        viewModelScope.launch {
            try {
                val saved = sessionRepository.saveAlertAudioSettings(
                    expectedDeviceId = deviceId,
                    update = update,
                )
                if (!isCurrentDevice(deviceId)) return@launch
                _uiState.value = _uiState.value.copy(
                    audio = saved,
                    audioForm = saved.toForm(),
                    audioDirty = false,
                    audioMessage = AlertsMessage(
                        text = "Налаштування аудіо збережено",
                        isError = false,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        audioMessage = AlertsMessage(
                            text = error.message
                                ?: "Не вдалося зберегти аудіопараметри",
                            isError = true,
                        ),
                    )
                }
            } finally {
                clearBusyIfCurrent(deviceId)
            }
        }
    }

    fun uploadMedia(
        kind: String,
        uriText: String,
    ) {
        if (_uiState.value.busyAction != null) return
        val deviceId = currentDeviceId() ?: return
        val media = _uiState.value.media ?: return

        _uiState.value = _uiState.value.copy(
            busyAction = AlertsBusyAction.MediaUpload(kind),
            mediaMessage = null,
        )

        viewModelScope.launch {
            try {
                val imported = mediaImporter.read(
                    uriText = uriText,
                    maxBytes = media.maxSizeBytes,
                )
                if (!isCurrentDevice(deviceId)) return@launch

                val saved = sessionRepository.uploadAlertMedia(
                    expectedDeviceId = deviceId,
                    kind = kind,
                    bytes = imported.bytes,
                    contentType = imported.contentType,
                )
                if (!isCurrentDevice(deviceId)) return@launch

                _uiState.value = _uiState.value.copy(
                    media = _uiState.value.media?.replace(saved),
                    mediaMessage = AlertsMessage(
                        text = "Завантажено " + imported.displayName,
                        isError = false,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        mediaMessage = AlertsMessage(
                            text = error.message ?: "Не вдалося завантажити MP3",
                            isError = true,
                        ),
                    )
                }
            } finally {
                clearBusyIfCurrent(deviceId)
            }
        }
    }

    fun resetMedia(kind: String) {
        if (_uiState.value.busyAction != null) return
        val deviceId = currentDeviceId() ?: return

        _uiState.value = _uiState.value.copy(
            busyAction = AlertsBusyAction.MediaReset(kind),
            mediaMessage = null,
        )

        viewModelScope.launch {
            try {
                val saved = sessionRepository.resetAlertMedia(
                    expectedDeviceId = deviceId,
                    kind = kind,
                )
                if (!isCurrentDevice(deviceId)) return@launch

                _uiState.value = _uiState.value.copy(
                    media = _uiState.value.media?.replace(saved),
                    mediaMessage = AlertsMessage(
                        text = "Стандартний файл відновлено",
                        isError = false,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        mediaMessage = AlertsMessage(
                            text = error.message ?: "Не вдалося відновити файл",
                            isError = true,
                        ),
                    )
                }
            } finally {
                clearBusyIfCurrent(deviceId)
            }
        }
    }

    fun resetAllMedia() {
        if (_uiState.value.busyAction != null) return
        val deviceId = currentDeviceId() ?: return
        val kinds = _uiState.value.media
            ?.items
            ?.map { it.kind }
            .orEmpty()
        if (kinds.isEmpty()) return

        _uiState.value = _uiState.value.copy(
            busyAction = AlertsBusyAction.MediaResetAll,
            mediaMessage = null,
        )

        viewModelScope.launch {
            try {
                kinds.forEach { kind ->
                    sessionRepository.resetAlertMedia(
                        expectedDeviceId = deviceId,
                        kind = kind,
                    )
                }
                if (!isCurrentDevice(deviceId)) return@launch

                val refreshed = sessionRepository.alertMedia()
                if (!isCurrentDevice(deviceId)) return@launch

                _uiState.value = _uiState.value.copy(
                    media = refreshed,
                    mediaMessage = AlertsMessage(
                        text = "Стандартні файли сповіщень відновлено",
                        isError = false,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(deviceId)) {
                    val refreshed = capture {
                        sessionRepository.alertMedia()
                    }.getOrNull()
                    _uiState.value = _uiState.value.copy(
                        media = refreshed ?: _uiState.value.media,
                        mediaMessage = AlertsMessage(
                            text = error.message
                                ?: "Не вдалося відновити всі файли",
                            isError = true,
                        ),
                    )
                }
            } finally {
                clearBusyIfCurrent(deviceId)
            }
        }
    }

    private fun parseProviderUpdate(
        form: AlertProviderForm,
    ): AlertProviderUpdate? =
        try {
            form.toUpdate()
        } catch (error: IllegalArgumentException) {
            _uiState.value = _uiState.value.copy(
                providerMessage = AlertsMessage(
                    text = error.message ?: "Некоректні параметри API",
                    isError = true,
                ),
            )
            null
        }

    private fun setProviderError(
        error: Exception,
        fallback: String,
    ) {
        _uiState.value = _uiState.value.copy(
            providerMessage = AlertsMessage(
                text = error.message ?: fallback,
                isError = true,
            ),
        )
    }

    private fun currentDeviceId(): DeviceId? {
        val stateDeviceId = _uiState.value.deviceId ?: return null
        return stateDeviceId.takeIf {
            sessionRepository.selectedDeviceId.value == stateDeviceId
        }
    }

    private fun isCurrentDevice(deviceId: DeviceId): Boolean =
        _uiState.value.deviceId == deviceId &&
            sessionRepository.selectedDeviceId.value == deviceId

    private fun clearBusyIfCurrent(deviceId: DeviceId) {
        if (isCurrentDevice(deviceId)) {
            _uiState.value = _uiState.value.copy(
                busyAction = null,
            )
        }
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
            mediaImporter: AlertMediaImporter,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AlertsViewModel(
                        sessionRepository = sessionRepository,
                        mediaImporter = mediaImporter,
                    )
                }
            }
    }
}

private fun AlertProviderSettings.toForm(): AlertProviderForm =
    AlertProviderForm(
        endpoint = endpoint,
        locationUid = locationUid.toString(),
        locationType = locationType,
        pollIntervalSeconds = pollIntervalSeconds.cleanNumber(),
        requestTimeoutSeconds = requestTimeoutSeconds.cleanNumber(),
        rateLimitBackoffSeconds = rateLimitBackoffSeconds.cleanNumber(),
        clearConfirmations = clearConfirmations.toString(),
    )

private fun AlertAudioSettings.toForm(): AlertAudioForm =
    AlertAudioForm(
        airRaidAlertsEnabled = airRaidAlertsEnabled,
        minuteSilenceEnabled = minuteSilenceEnabled,
        duckDb = duckDb.cleanNumber(),
        duckFadeSeconds = duckFadeSeconds.cleanNumber(),
        restoreFadeSeconds = restoreFadeSeconds.cleanNumber(),
        alertVolumePercent = alertVolumePercent.cleanNumber(),
        defaultRestoreVolumePercent = defaultRestoreVolumePercent.cleanNumber(),
        minuteSilenceVolumePercent = minuteSilenceVolumePercent.cleanNumber(),
        minuteSilenceStartTime = minuteSilenceStartTime,
        minuteSilenceTimezone = minuteSilenceTimezone,
        minuteSilenceCatchUpSeconds = minuteSilenceCatchUpSeconds.toString(),
        minuteSilenceMusicFadeSeconds = minuteSilenceMusicFadeSeconds.cleanNumber(),
        alertRepeatIntervalMinutes = alertRepeatIntervalMinutes.toString(),
        duckOnlyDuringAnnouncement = duckOnlyDuringAnnouncement,
    )

private fun AlertProviderForm.toUpdate(): AlertProviderUpdate {
    val uid = locationUid.requiredLong("UID локації")
    require(uid in 1..4_294_967_295L) {
        "UID локації має бути в межах 1..4294967295"
    }
    return AlertProviderUpdate(
        endpoint = endpoint.trim(),
        locationUid = uid,
        locationType = locationType.trim(),
        pollIntervalSeconds = pollIntervalSeconds.requiredDouble("Інтервал опитування"),
        requestTimeoutSeconds = requestTimeoutSeconds.requiredDouble("Timeout"),
        rateLimitBackoffSeconds =
            rateLimitBackoffSeconds.requiredDouble("Пауза після HTTP 429"),
        clearConfirmations = clearConfirmations.requiredInt("Підтвердження відбою"),
        token = token.trim().takeIf { it.isNotEmpty() },
    )
}

private fun AlertAudioForm.toUpdate(): AlertAudioUpdate =
    AlertAudioUpdate(
        airRaidAlertsEnabled = airRaidAlertsEnabled,
        duckDb = duckDb.requiredDouble("Ducking"),
        duckFadeSeconds = duckFadeSeconds.requiredDouble("Плавне стишення"),
        restoreFadeSeconds = restoreFadeSeconds.requiredDouble("Відновлення"),
        alertVolumePercent = alertVolumePercent.requiredDouble("Гучність ALERT"),
        defaultRestoreVolumePercent =
            defaultRestoreVolumePercent.requiredDouble("Рівень відновлення"),
        minuteSilenceVolumePercent =
            minuteSilenceVolumePercent.requiredDouble("Гучність хвилини мовчання"),
        minuteSilenceEnabled = minuteSilenceEnabled,
        minuteSilenceStartTime = minuteSilenceStartTime.trim(),
        minuteSilenceTimezone = minuteSilenceTimezone.trim(),
        minuteSilenceCatchUpSeconds =
            minuteSilenceCatchUpSeconds.requiredLong("Допустиме запізнення"),
        minuteSilenceMusicFadeSeconds =
            minuteSilenceMusicFadeSeconds.requiredDouble("Стишення хвилини мовчання"),
        alertRepeatIntervalMinutes =
            alertRepeatIntervalMinutes.requiredLong("Повторення тривоги"),
        duckOnlyDuringAnnouncement = duckOnlyDuringAnnouncement,
    )

private fun AlertMediaCatalog.replace(item: AlertMediaFile): AlertMediaCatalog =
    copy(
        items = items.map { current ->
            if (current.kind == item.kind) item else current
        },
    )

private fun String.requiredDouble(label: String): Double =
    trim().toDoubleOrNull()
        ?: throw IllegalArgumentException("$label має містити число")

private fun String.requiredLong(label: String): Long =
    trim().toLongOrNull()
        ?: throw IllegalArgumentException("$label має містити ціле число")

private fun String.requiredInt(label: String): Int =
    trim().toIntOrNull()
        ?: throw IllegalArgumentException("$label має містити ціле число")

private fun Double.cleanNumber(): String =
    if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        toString()
    }
