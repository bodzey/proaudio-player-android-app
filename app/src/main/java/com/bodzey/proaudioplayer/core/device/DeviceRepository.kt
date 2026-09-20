package com.bodzey.proaudioplayer.core.device

import com.bodzey.proaudioplayer.core.model.DeviceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class DeviceRepository(
    deviceRegistry: DeviceRegistry,
    private val historyStore: DeviceHistoryStore,
    scope: CoroutineScope,
) {
    val devices: StateFlow<List<AvailableDevice>> = deviceRegistry
        .devices()
        .onEach(::persistLiveSnapshot)
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

    fun current(deviceId: DeviceId): AvailableDevice? =
        devices.value.firstOrNull { device -> device.id == deviceId }

    private suspend fun persistLiveSnapshot(
        devices: List<AvailableDevice>,
    ) {
        try {
            historyStore.record(devices)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Persistence is secondary to live discovery. A database failure
            // must not tear down mDNS discovery or an active player session.
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
