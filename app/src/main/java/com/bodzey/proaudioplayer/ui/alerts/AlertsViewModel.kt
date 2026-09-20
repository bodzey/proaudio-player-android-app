package com.bodzey.proaudioplayer.ui.alerts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.api.AlertAudioSettings
import com.bodzey.proaudioplayer.core.api.AlertAudioUpdate
import com.bodzey.proaudioplayer.core.api.AlertMediaCatalog
import com.bodzey.proaudioplayer.core.api.AlertMediaFile
import com.bodzey.proaudioplayer.core.api.AlertProviderSettings
import com.bodzey.proaudioplayer.core.api.AlertSettingsValidator
import com.bodzey.proaudioplayer.core.api.AlertProviderUpdate
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import com.bodzey.proaudioplayer.core.media.AlertMediaImporter
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
    private val savedStateHandle: SavedStateHandle,
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
                val provider = async {
                    capture {
                        sessionRepository.alertProviderSettings(deviceId)
                    }
                }
                val audio = async {
                    capture {
                        sessionRepository.alertAudioSettings(deviceId)
                    }
                }
                val media = async {
                    capture {
                        sessionRepository.alertMedia(deviceId)
                    }
                }
                Triple(provider.await(), audio.await(), media.await())
            }

            if (!isCurrentDevice(deviceId)) {
                return@launch
            }

            val currentState = _uiState.value
            val provider = result.first.getOrNull() ?: currentState.provider
            val audio = result.second.getOrNull() ?: currentState.audio
            val media = result.third.getOrNull() ?: currentState.media
            val restoredProviderForm = restoredProviderDraft(deviceId)
            val restoredAudioForm = restoredAudioDraft(deviceId)
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
                    restoredProviderForm != null -> restoredProviderForm
                    provider != null -> provider.toForm()
                    else -> null
                },
                providerDirty = currentState.providerDirty ||
                    restoredProviderForm != null,
                audio = audio,
                audioForm = when {
                    currentState.audioDirty -> currentState.audioForm
                    restoredAudioForm != null -> restoredAudioForm
                    audio != null -> audio.toForm()
                    else -> null
                },
                audioDirty = currentState.audioDirty ||
                    restoredAudioForm != null,
                media = media,
                loadError = errors.takeIf { it.isNotEmpty() }?.joinToString("; "),
            )
        }
    }

    fun refresh() {
        ensureLoaded(force = true)
    }

    fun updateProviderForm(form: AlertProviderForm) {
        val deviceId = currentDeviceId() ?: return
        _uiState.value = _uiState.value.copy(
            providerForm = form,
            providerDirty = true,
            providerMessage = null,
        )
        persistProviderDraft(deviceId, form)
    }

    fun updateAudioForm(form: AlertAudioForm) {
        val deviceId = currentDeviceId() ?: return
        _uiState.value = _uiState.value.copy(
            audioForm = form,
            audioDirty = true,
            audioMessage = null,
        )
        persistAudioDraft(deviceId, form)
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
                clearProviderDraft()
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
                clearAudioDraft()
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

                val refreshed = sessionRepository.alertMedia(deviceId)
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
                        sessionRepository.alertMedia(deviceId)
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

    private fun persistProviderDraft(
        deviceId: DeviceId,
        form: AlertProviderForm,
    ) {
        savedStateHandle[KEY_PROVIDER_DRAFT_DEVICE_ID] = deviceId.value
        savedStateHandle[KEY_PROVIDER_ENDPOINT] = form.endpoint
        savedStateHandle[KEY_PROVIDER_LOCATION_UID] = form.locationUid
        savedStateHandle[KEY_PROVIDER_LOCATION_TYPE] = form.locationType
        savedStateHandle[KEY_PROVIDER_POLL_INTERVAL] = form.pollIntervalSeconds
        savedStateHandle[KEY_PROVIDER_TIMEOUT] = form.requestTimeoutSeconds
        savedStateHandle[KEY_PROVIDER_BACKOFF] = form.rateLimitBackoffSeconds
        savedStateHandle[KEY_PROVIDER_CLEAR_CONFIRMATIONS] = form.clearConfirmations
    }

    private fun restoredProviderDraft(
        deviceId: DeviceId,
    ): AlertProviderForm? {
        if (savedStateHandle.get<String>(KEY_PROVIDER_DRAFT_DEVICE_ID) != deviceId.value) {
            return null
        }
        val endpoint = savedStateHandle.get<String>(KEY_PROVIDER_ENDPOINT) ?: return null
        val locationUid = savedStateHandle.get<String>(KEY_PROVIDER_LOCATION_UID) ?: return null
        val locationType = savedStateHandle.get<String>(KEY_PROVIDER_LOCATION_TYPE) ?: return null
        val pollInterval = savedStateHandle.get<String>(KEY_PROVIDER_POLL_INTERVAL) ?: return null
        val timeout = savedStateHandle.get<String>(KEY_PROVIDER_TIMEOUT) ?: return null
        val backoff = savedStateHandle.get<String>(KEY_PROVIDER_BACKOFF) ?: return null
        val confirmations =
            savedStateHandle.get<String>(KEY_PROVIDER_CLEAR_CONFIRMATIONS) ?: return null

        return AlertProviderForm(
            endpoint = endpoint,
            locationUid = locationUid,
            locationType = locationType,
            token = "",
            pollIntervalSeconds = pollInterval,
            requestTimeoutSeconds = timeout,
            rateLimitBackoffSeconds = backoff,
            clearConfirmations = confirmations,
        )
    }

    private fun clearProviderDraft() {
        listOf(
            KEY_PROVIDER_DRAFT_DEVICE_ID,
            KEY_PROVIDER_ENDPOINT,
            KEY_PROVIDER_LOCATION_UID,
            KEY_PROVIDER_LOCATION_TYPE,
            KEY_PROVIDER_POLL_INTERVAL,
            KEY_PROVIDER_TIMEOUT,
            KEY_PROVIDER_BACKOFF,
            KEY_PROVIDER_CLEAR_CONFIRMATIONS,
        ).forEach { key -> savedStateHandle.remove<Any?>(key) }
    }

    private fun persistAudioDraft(
        deviceId: DeviceId,
        form: AlertAudioForm,
    ) {
        savedStateHandle[KEY_AUDIO_DRAFT_DEVICE_ID] = deviceId.value
        savedStateHandle[KEY_AUDIO_ENABLED] = form.airRaidAlertsEnabled
        savedStateHandle[KEY_AUDIO_MINUTE_ENABLED] = form.minuteSilenceEnabled
        savedStateHandle[KEY_AUDIO_DUCK_DB] = form.duckDb
        savedStateHandle[KEY_AUDIO_DUCK_FADE] = form.duckFadeSeconds
        savedStateHandle[KEY_AUDIO_RESTORE_FADE] = form.restoreFadeSeconds
        savedStateHandle[KEY_AUDIO_ALERT_VOLUME] = form.alertVolumePercent
        savedStateHandle[KEY_AUDIO_RESTORE_VOLUME] = form.defaultRestoreVolumePercent
        savedStateHandle[KEY_AUDIO_MINUTE_VOLUME] = form.minuteSilenceVolumePercent
        savedStateHandle[KEY_AUDIO_MINUTE_TIME] = form.minuteSilenceStartTime
        savedStateHandle[KEY_AUDIO_TIMEZONE] = form.minuteSilenceTimezone
        savedStateHandle[KEY_AUDIO_CATCH_UP] = form.minuteSilenceCatchUpSeconds
        savedStateHandle[KEY_AUDIO_MINUTE_FADE] = form.minuteSilenceMusicFadeSeconds
        savedStateHandle[KEY_AUDIO_REPEAT] = form.alertRepeatIntervalMinutes
        savedStateHandle[KEY_AUDIO_TALKOVER] = form.duckOnlyDuringAnnouncement
    }

    private fun restoredAudioDraft(
        deviceId: DeviceId,
    ): AlertAudioForm? {
        if (savedStateHandle.get<String>(KEY_AUDIO_DRAFT_DEVICE_ID) != deviceId.value) {
            return null
        }

        return AlertAudioForm(
            airRaidAlertsEnabled = savedStateHandle.get<Boolean>(KEY_AUDIO_ENABLED)
                ?: return null,
            minuteSilenceEnabled =
                savedStateHandle.get<Boolean>(KEY_AUDIO_MINUTE_ENABLED) ?: return null,
            duckDb = savedStateHandle.get<String>(KEY_AUDIO_DUCK_DB) ?: return null,
            duckFadeSeconds =
                savedStateHandle.get<String>(KEY_AUDIO_DUCK_FADE) ?: return null,
            restoreFadeSeconds =
                savedStateHandle.get<String>(KEY_AUDIO_RESTORE_FADE) ?: return null,
            alertVolumePercent =
                savedStateHandle.get<String>(KEY_AUDIO_ALERT_VOLUME) ?: return null,
            defaultRestoreVolumePercent =
                savedStateHandle.get<String>(KEY_AUDIO_RESTORE_VOLUME) ?: return null,
            minuteSilenceVolumePercent =
                savedStateHandle.get<String>(KEY_AUDIO_MINUTE_VOLUME) ?: return null,
            minuteSilenceStartTime =
                savedStateHandle.get<String>(KEY_AUDIO_MINUTE_TIME) ?: return null,
            minuteSilenceTimezone =
                savedStateHandle.get<String>(KEY_AUDIO_TIMEZONE) ?: return null,
            minuteSilenceCatchUpSeconds =
                savedStateHandle.get<String>(KEY_AUDIO_CATCH_UP) ?: return null,
            minuteSilenceMusicFadeSeconds =
                savedStateHandle.get<String>(KEY_AUDIO_MINUTE_FADE) ?: return null,
            alertRepeatIntervalMinutes =
                savedStateHandle.get<String>(KEY_AUDIO_REPEAT) ?: return null,
            duckOnlyDuringAnnouncement =
                savedStateHandle.get<Boolean>(KEY_AUDIO_TALKOVER) ?: return null,
        )
    }

    private fun clearAudioDraft() {
        listOf(
            KEY_AUDIO_DRAFT_DEVICE_ID,
            KEY_AUDIO_ENABLED,
            KEY_AUDIO_MINUTE_ENABLED,
            KEY_AUDIO_DUCK_DB,
            KEY_AUDIO_DUCK_FADE,
            KEY_AUDIO_RESTORE_FADE,
            KEY_AUDIO_ALERT_VOLUME,
            KEY_AUDIO_RESTORE_VOLUME,
            KEY_AUDIO_MINUTE_VOLUME,
            KEY_AUDIO_MINUTE_TIME,
            KEY_AUDIO_TIMEZONE,
            KEY_AUDIO_CATCH_UP,
            KEY_AUDIO_MINUTE_FADE,
            KEY_AUDIO_REPEAT,
            KEY_AUDIO_TALKOVER,
        ).forEach { key -> savedStateHandle.remove<Any?>(key) }
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
        private const val KEY_PROVIDER_DRAFT_DEVICE_ID = "alerts_provider_device_id"
        private const val KEY_PROVIDER_ENDPOINT = "alerts_provider_endpoint"
        private const val KEY_PROVIDER_LOCATION_UID = "alerts_provider_location_uid"
        private const val KEY_PROVIDER_LOCATION_TYPE = "alerts_provider_location_type"
        private const val KEY_PROVIDER_POLL_INTERVAL = "alerts_provider_poll_interval"
        private const val KEY_PROVIDER_TIMEOUT = "alerts_provider_timeout"
        private const val KEY_PROVIDER_BACKOFF = "alerts_provider_backoff"
        private const val KEY_PROVIDER_CLEAR_CONFIRMATIONS =
            "alerts_provider_clear_confirmations"

        private const val KEY_AUDIO_DRAFT_DEVICE_ID = "alerts_audio_device_id"
        private const val KEY_AUDIO_ENABLED = "alerts_audio_enabled"
        private const val KEY_AUDIO_MINUTE_ENABLED = "alerts_audio_minute_enabled"
        private const val KEY_AUDIO_DUCK_DB = "alerts_audio_duck_db"
        private const val KEY_AUDIO_DUCK_FADE = "alerts_audio_duck_fade"
        private const val KEY_AUDIO_RESTORE_FADE = "alerts_audio_restore_fade"
        private const val KEY_AUDIO_ALERT_VOLUME = "alerts_audio_alert_volume"
        private const val KEY_AUDIO_RESTORE_VOLUME = "alerts_audio_restore_volume"
        private const val KEY_AUDIO_MINUTE_VOLUME = "alerts_audio_minute_volume"
        private const val KEY_AUDIO_MINUTE_TIME = "alerts_audio_minute_time"
        private const val KEY_AUDIO_TIMEZONE = "alerts_audio_timezone"
        private const val KEY_AUDIO_CATCH_UP = "alerts_audio_catch_up"
        private const val KEY_AUDIO_MINUTE_FADE = "alerts_audio_minute_fade"
        private const val KEY_AUDIO_REPEAT = "alerts_audio_repeat"
        private const val KEY_AUDIO_TALKOVER = "alerts_audio_talkover"

        fun factory(
            sessionRepository: PlayerSessionRepository,
            mediaImporter: AlertMediaImporter,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AlertsViewModel(
                        sessionRepository = sessionRepository,
                        mediaImporter = mediaImporter,
                        savedStateHandle = createSavedStateHandle(),
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
    val update = AlertProviderUpdate(
        endpoint = endpoint.trim(),
        locationUid = locationUid.requiredLong("UID локації"),
        locationType = locationType.trim(),
        pollIntervalSeconds = pollIntervalSeconds.requiredDouble("Інтервал опитування"),
        requestTimeoutSeconds = requestTimeoutSeconds.requiredDouble("Очікування відповіді"),
        rateLimitBackoffSeconds =
            rateLimitBackoffSeconds.requiredDouble("Пауза після HTTP 429"),
        clearConfirmations = clearConfirmations.requiredInt("Підтвердження відбою"),
        token = token.trim().takeIf { it.isNotEmpty() },
    )
    AlertSettingsValidator.validateProvider(update)
    return update
}

private fun AlertAudioForm.toUpdate(): AlertAudioUpdate {
    val update = AlertAudioUpdate(
        airRaidAlertsEnabled = airRaidAlertsEnabled,
        duckDb = duckDb.requiredDouble("Стишення музики"),
        duckFadeSeconds = duckFadeSeconds.requiredDouble("Плавне стишення"),
        restoreFadeSeconds = restoreFadeSeconds.requiredDouble("Час відновлення"),
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
            minuteSilenceMusicFadeSeconds.requiredDouble(
                "Стишення перед хвилиною мовчання",
            ),
        alertRepeatIntervalMinutes =
            alertRepeatIntervalMinutes.requiredLong("Повторення тривоги"),
        duckOnlyDuringAnnouncement = duckOnlyDuringAnnouncement,
    )
    AlertSettingsValidator.validateAudio(update)
    return update
}

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
