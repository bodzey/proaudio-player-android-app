package com.bodzey.proaudioplayer.ui.media

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.api.PlayerFeature
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class MediaViewModel(
    private val sessionRepository: PlayerSessionRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MediaUiState(
            libraryQuery = restoredLibraryQuery(
                sessionRepository.selectedDeviceId.value,
            ),
        ),
    )
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    fun ensureLoaded(force: Boolean = false) {
        val connected = sessionRepository.state.value as? PlayerSessionState.Connected
            ?: return
        val deviceId = connected.deviceId
        val current = _uiState.value

        if (!force &&
            current.deviceId == deviceId &&
            (current.loading ||
                current.library.isNotEmpty() ||
                current.playlists.isNotEmpty() ||
                current.queue.isNotEmpty())
        ) {
            return
        }

        _uiState.value = current.copy(
            deviceId = deviceId,
            loading = true,
            library = if (current.deviceId == deviceId) current.library else emptyList(),
            playlists = if (current.deviceId == deviceId) current.playlists else emptyList(),
            queue = if (current.deviceId == deviceId) current.queue else emptyList(),
            libraryQuery = if (current.deviceId == deviceId) {
                current.libraryQuery
            } else {
                restoredLibraryQuery(deviceId)
            },
            busyAction = null,
            error = null,
        )

        viewModelScope.launch {
            val features = connected.capabilities.features
            val result = supervisorScope {
                val library = async {
                    if (PlayerFeature.LIBRARY in features) {
                        capture { sessionRepository.library(deviceId) }
                    } else {
                        Result.success(emptyList())
                    }
                }
                val playlists = async {
                    if (PlayerFeature.PLAYLISTS in features) {
                        capture { sessionRepository.playlists(deviceId) }
                    } else {
                        Result.success(emptyList())
                    }
                }
                val queue = async {
                    if (PlayerFeature.QUEUE in features) {
                        capture { sessionRepository.queue(deviceId) }
                    } else {
                        Result.success(emptyList())
                    }
                }
                Triple(library.await(), playlists.await(), queue.await())
            }

            if (!isCurrentDevice(deviceId)) return@launch

            val failures = listOfNotNull(
                result.first.exceptionOrNull()?.message,
                result.second.exceptionOrNull()?.message,
                result.third.exceptionOrNull()?.message,
            ).distinct()

            _uiState.value = _uiState.value.copy(
                loading = false,
                library = result.first.getOrElse { _uiState.value.library },
                playlists = result.second.getOrElse { _uiState.value.playlists },
                queue = result.third.getOrElse { _uiState.value.queue },
                error = failures.takeIf { it.isNotEmpty() }?.joinToString(" · "),
            )
        }
    }

    fun setLibraryQuery(value: String) {
        val deviceId = sessionRepository.selectedDeviceId.value ?: return
        _uiState.value = _uiState.value.copy(
            libraryQuery = value,
        )
        savedStateHandle[KEY_QUERY_DEVICE_ID] = deviceId.value
        savedStateHandle[KEY_LIBRARY_QUERY] = value
    }

    fun refresh() {
        ensureLoaded(force = true)
    }

    fun refreshLibrary() {
        val deviceId = currentDeviceId() ?: return
        if (_uiState.value.libraryRefreshing) return

        _uiState.value = _uiState.value.copy(
            libraryRefreshing = true,
            error = null,
        )
        viewModelScope.launch {
            try {
                sessionRepository.refreshLibrary(deviceId)
                val library = sessionRepository.library(deviceId)
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        library = library,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                setErrorIfCurrent(
                    deviceId = deviceId,
                    error = error,
                    fallback = "Не вдалося оновити медіатеку",
                )
            } finally {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        libraryRefreshing = false,
                    )
                }
            }
        }
    }

    fun playLibraryPath(path: String) {
        runAction(
            key = "library:$path",
            fallback = "Не вдалося відтворити файл",
        ) { deviceId ->
            sessionRepository.playLibraryPath(
                expectedDeviceId = deviceId,
                path = path,
            )
            refreshQueue(deviceId)
        }
    }

    fun loadPlaylist(name: String) {
        runAction(
            key = "playlist:$name",
            fallback = "Не вдалося завантажити плейліст",
        ) { deviceId ->
            sessionRepository.loadPlaylist(
                expectedDeviceId = deviceId,
                name = name,
            )
            refreshQueue(deviceId)
        }
    }

    fun playQueueItem(position: Int) {
        runAction(
            key = "queue-play:$position",
            fallback = "Не вдалося відтворити елемент черги",
        ) { deviceId ->
            sessionRepository.playQueueItem(
                expectedDeviceId = deviceId,
                position = position,
            )
        }
    }

    fun removeQueueItem(position: Int) {
        runAction(
            key = "queue-remove:$position",
            fallback = "Не вдалося видалити елемент черги",
        ) { deviceId ->
            sessionRepository.removeQueueItem(
                expectedDeviceId = deviceId,
                position = position,
            )
            refreshQueue(deviceId)
        }
    }

    fun clearQueue() {
        runAction(
            key = "queue-clear",
            fallback = "Не вдалося очистити чергу",
        ) { deviceId ->
            sessionRepository.clearQueue(deviceId)
            if (isCurrentDevice(deviceId)) {
                _uiState.value = _uiState.value.copy(
                    queue = emptyList(),
                )
            }
        }
    }

    private fun runAction(
        key: String,
        fallback: String,
        block: suspend (DeviceId) -> Unit,
    ) {
        val deviceId = currentDeviceId() ?: return
        if (_uiState.value.busyAction != null) return

        _uiState.value = _uiState.value.copy(
            busyAction = key,
            error = null,
        )
        viewModelScope.launch {
            try {
                block(deviceId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                setErrorIfCurrent(deviceId, error, fallback)
            } finally {
                if (isCurrentDevice(deviceId)) {
                    _uiState.value = _uiState.value.copy(
                        busyAction = null,
                    )
                }
            }
        }
    }

    private suspend fun refreshQueue(deviceId: DeviceId) {
        val queue = sessionRepository.queue(deviceId)
        if (isCurrentDevice(deviceId)) {
            _uiState.value = _uiState.value.copy(
                queue = queue,
            )
        }
    }

    private fun restoredLibraryQuery(deviceId: DeviceId?): String {
        if (deviceId == null) return ""
        val savedDeviceId = savedStateHandle
            .get<String>(KEY_QUERY_DEVICE_ID)
            ?.let { value ->
                runCatching { DeviceId.parse(value) }.getOrNull()
            }
        return if (savedDeviceId == deviceId) {
            savedStateHandle.get<String>(KEY_LIBRARY_QUERY).orEmpty()
        } else {
            ""
        }
    }

    private fun currentDeviceId(): DeviceId? {
        val deviceId = _uiState.value.deviceId ?: return null
        return deviceId.takeIf {
            sessionRepository.selectedDeviceId.value == deviceId
        }
    }

    private fun isCurrentDevice(deviceId: DeviceId): Boolean =
        sessionRepository.selectedDeviceId.value == deviceId &&
            _uiState.value.deviceId == deviceId

    private fun setErrorIfCurrent(
        deviceId: DeviceId,
        error: Exception,
        fallback: String,
    ) {
        if (isCurrentDevice(deviceId)) {
            _uiState.value = _uiState.value.copy(
                error = error.message ?: fallback,
            )
        }
    }

    private suspend fun <T> capture(
        block: suspend () -> T,
    ): Result<T> =
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }

    companion object {
        private const val KEY_QUERY_DEVICE_ID = "media_query_device_id"
        private const val KEY_LIBRARY_QUERY = "media_library_query"

        fun factory(
            sessionRepository: PlayerSessionRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MediaViewModel(
                        sessionRepository = sessionRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
