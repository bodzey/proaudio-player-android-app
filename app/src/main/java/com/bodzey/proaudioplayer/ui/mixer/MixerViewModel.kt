package com.bodzey.proaudioplayer.ui.mixer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.api.AudioLevelState
import com.bodzey.proaudioplayer.core.api.MixerState
import com.bodzey.proaudioplayer.core.api.MixerTarget
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MixerViewModel(
    private val sessionRepository: PlayerSessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MixerUiState())
    val uiState: StateFlow<MixerUiState> = _uiState.asStateFlow()

    private val musicLevelRequests =
        Channel<MixerLevelRequest>(capacity = Channel.CONFLATED)
    private val alertLevelRequests =
        Channel<MixerLevelRequest>(capacity = Channel.CONFLATED)

    init {
        viewModelScope.launch {
            processLevelRequests(
                target = MixerTarget.Music,
                requests = musicLevelRequests,
            )
        }
        viewModelScope.launch {
            processLevelRequests(
                target = MixerTarget.Alert,
                requests = alertLevelRequests,
            )
        }
    }

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
            levelOverrides = current.levelOverrides
                .takeIf { current.deviceId == deviceId }
                ?: emptyMap(),
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
        val deviceId = sessionRepository.selectedDeviceId.value ?: return
        val current = _uiState.value
        if (current.deviceId != deviceId ||
            current.mixer == null ||
            current.pendingTarget != null
        ) {
            return
        }

        val targetDb = db.coerceIn(MIN_LEVEL_DB, MAX_LEVEL_DB)
        _uiState.value = current.copy(
            levelOverrides = current.levelOverrides + (target to targetDb),
            error = null,
        )

        levelRequests(target).trySend(
            MixerLevelRequest(
                deviceId = deviceId,
                db = targetDb,
            ),
        )
    }

    fun setMuted(
        target: MixerTarget,
        muted: Boolean,
    ) {
        val current = _uiState.value
        val mixer = current.mixer ?: return
        if (current.pendingTarget != null || target in current.levelOverrides) {
            return
        }

        val db = mixer.level(target).db ?: return
        mutate(
            target = target,
            db = db,
            muted = muted,
        )
    }

    fun refresh() {
        val current = _uiState.value
        if (current.pendingTarget != null || current.levelOverrides.isNotEmpty()) {
            return
        }
        ensureLoaded(force = true)
    }

    private suspend fun processLevelRequests(
        target: MixerTarget,
        requests: Channel<MixerLevelRequest>,
    ) {
        for (request in requests) {
            if (!isCurrentDevice(request.deviceId)) {
                continue
            }

            val muted = _uiState.value.mixer
                ?.level(target)
                ?.muted
                ?: continue

            try {
                val updated = sessionRepository.setMixer(
                    expectedDeviceId = request.deviceId,
                    target = target,
                    db = request.db,
                    muted = muted,
                )

                if (isCurrentDevice(request.deviceId)) {
                    val current = _uiState.value
                    val latestTarget = current.levelOverrides[target]

                    _uiState.value = current.copy(
                        mixer = mergeMixerMutationResult(
                            current = current.mixer,
                            updated = updated,
                            target = target,
                            protectedTargets = current.protectedTargets(),
                        ),
                        levelOverrides = if (
                            latestTarget != null &&
                            abs(latestTarget - request.db) <= LEVEL_TARGET_TOLERANCE_DB
                        ) {
                            current.levelOverrides - target
                        } else {
                            current.levelOverrides
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentDevice(request.deviceId)) {
                    val current = _uiState.value
                    val latestTarget = current.levelOverrides[target]

                    if (latestTarget != null &&
                        abs(latestTarget - request.db) <= LEVEL_TARGET_TOLERANCE_DB
                    ) {
                        _uiState.value = current.copy(
                            levelOverrides = current.levelOverrides - target,
                            error = error.message ?: "Не вдалося змінити мікшер",
                        )
                    }
                }
            }

            delay(LEVEL_REQUEST_INTERVAL_MILLIS)
        }
    }

    private fun mutate(
        target: MixerTarget,
        db: Double,
        muted: Boolean,
    ) {
        val deviceId = sessionRepository.selectedDeviceId.value ?: return
        val current = _uiState.value
        if (current.deviceId != deviceId ||
            current.pendingTarget != null ||
            target in current.levelOverrides
        ) {
            return
        }

        _uiState.value = current.copy(
            pendingTarget = target,
            error = null,
        )

        viewModelScope.launch {
            try {
                val updated = sessionRepository.setMixer(
                    expectedDeviceId = deviceId,
                    target = target,
                    db = db.coerceIn(MIN_LEVEL_DB, MAX_LEVEL_DB),
                    muted = muted,
                )
                if (isCurrentDevice(deviceId)) {
                    val state = _uiState.value
                    _uiState.value = state.copy(
                        mixer = mergeMixerMutationResult(
                            current = state.mixer,
                            updated = updated,
                            target = target,
                            protectedTargets = state.protectedTargets(),
                        ),
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

    private fun levelRequests(target: MixerTarget): Channel<MixerLevelRequest> =
        when (target) {
            MixerTarget.Music -> musicLevelRequests
            MixerTarget.Alert -> alertLevelRequests
        }

    private fun isCurrentDevice(deviceId: DeviceId): Boolean =
        sessionRepository.selectedDeviceId.value == deviceId &&
            _uiState.value.deviceId == deviceId

    private data class MixerLevelRequest(
        val deviceId: DeviceId,
        val db: Double,
    )

    companion object {
        private const val MIN_LEVEL_DB = -60.0
        private const val MAX_LEVEL_DB = 0.0
        private const val LEVEL_REQUEST_INTERVAL_MILLIS = 75L
        private const val LEVEL_TARGET_TOLERANCE_DB = 0.01

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

private fun MixerState.level(target: MixerTarget): AudioLevelState =
    when (target) {
        MixerTarget.Music -> music
        MixerTarget.Alert -> alert
    }

private fun MixerUiState.protectedTargets(): Set<MixerTarget> =
    buildSet {
        addAll(levelOverrides.keys)
        pendingTarget?.let(::add)
    }

internal fun mergeMixerMutationResult(
    current: MixerState?,
    updated: MixerState,
    target: MixerTarget,
    protectedTargets: Set<MixerTarget>,
): MixerState {
    if (current == null) {
        return updated
    }

    return updated.copy(
        music = if (
            target != MixerTarget.Music &&
            MixerTarget.Music in protectedTargets
        ) {
            current.music
        } else {
            updated.music
        },
        alert = if (
            target != MixerTarget.Alert &&
            MixerTarget.Alert in protectedTargets
        ) {
            current.alert
        } else {
            updated.alert
        },
    )
}
