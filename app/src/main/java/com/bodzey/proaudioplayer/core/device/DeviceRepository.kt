package com.bodzey.proaudioplayer.core.device

import com.bodzey.proaudioplayer.core.model.DeviceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DeviceRepository(
    deviceRegistry: DeviceRegistry,
    private val historyStore: DeviceHistoryStore,
    scope: CoroutineScope,
) {
    private val persistenceRequests =
        Channel<List<AvailableDevice>>(capacity = Channel.CONFLATED)
    private val historyMutex = Mutex()

    val devices: StateFlow<List<AvailableDevice>> = deviceRegistry
        .devices()
        .onEach { snapshot ->
            persistenceRequests.trySend(snapshot)
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000,
                replayExpirationMillis = 0,
            ),
            initialValue = emptyList(),
        )

    val listEntries: StateFlow<List<DeviceListEntry>> =
        combine(
            devices,
            historyStore.devices()
                .catch { emit(emptyList()) },
            ::mergeDeviceListEntries,
        ).stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5_000,
                replayExpirationMillis = 0,
            ),
            initialValue = emptyList(),
        )

    init {
        scope.launch {
            for (snapshot in persistenceRequests) {
                persistLiveSnapshot(snapshot)
            }
        }
    }

    fun current(deviceId: DeviceId): AvailableDevice? =
        devices.value.firstOrNull { device -> device.id == deviceId }

    suspend fun forgetKnownDevice(deviceId: DeviceId) {
        historyMutex.withLock {
            check(current(deviceId) == null) {
                "Online devices cannot be forgotten"
            }

            historyStore.forget(deviceId)

            current(deviceId)?.let { liveDevice ->
                historyStore.record(listOf(liveDevice))
            }
        }
    }

    private suspend fun persistLiveSnapshot(
        devices: List<AvailableDevice>,
    ) {
        try {
            historyMutex.withLock {
                historyStore.record(devices)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // History is advisory. Database failures must not affect live
            // discovery, endpoint resolution or an active player session.
        }
    }
}

internal fun mergeDeviceListEntries(
    live: List<AvailableDevice>,
    known: List<KnownDevice>,
): List<DeviceListEntry> {
    val liveIds = live.asSequence().map { device -> device.id }.toSet()

    return buildList {
        live.forEach { device ->
            add(
                DeviceListEntry(
                    id = device.id,
                    displayName = device.displayName,
                    apiMajorVersion = device.apiMajorVersion,
                    endpoints = device.endpoints,
                    lastSeen = device.lastSeen,
                    online = true,
                ),
            )
        }

        known.asSequence()
            .filterNot { device -> device.id in liveIds }
            .forEach { device ->
                add(
                    DeviceListEntry(
                        id = device.id,
                        displayName = device.displayName,
                        apiMajorVersion = device.apiMajorVersion,
                        endpoints = device.endpoints,
                        lastSeen = device.lastSeen,
                        online = false,
                    ),
                )
            }
    }.sortedWith(
        compareByDescending<DeviceListEntry> { device -> device.online }
            .thenBy { device -> device.displayName.lowercase() }
            .thenBy { device -> device.id.value },
    )
}
