package com.bodzey.proaudioplayer.data.device

import com.bodzey.proaudioplayer.core.device.AvailableDevice
import com.bodzey.proaudioplayer.core.device.DeviceHistoryStore
import com.bodzey.proaudioplayer.core.device.KnownDevice
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDeviceHistoryStore(
    private val dao: KnownDeviceDao,
) : DeviceHistoryStore {

    override fun devices(): Flow<List<KnownDevice>> =
        dao.observeAll().map { rows ->
            rows.mapNotNull(::toKnownDevice)
        }

    override suspend fun record(devices: List<AvailableDevice>) {
        devices
            .asSequence()
            .filter { device -> device.persistable }
            .forEach { device ->
                dao.replaceObserved(
                    device = KnownDeviceEntity(
                        id = device.id.value,
                        displayName = device.displayName,
                        apiMajorVersion = device.apiMajorVersion,
                        lastSeenEpochMillis = device.lastSeen.toEpochMilli(),
                    ),
                    endpoints = device.endpoints.map { endpoint ->
                        KnownEndpointEntity(
                            deviceId = device.id.value,
                            host = endpoint.host,
                            port = endpoint.port,
                            transport = endpoint.transport.name,
                        )
                    },
                )
            }
    }

    private fun toKnownDevice(
        row: KnownDeviceWithEndpoints,
    ): KnownDevice? {
        val id = runCatching {
            DeviceId.parse(row.device.id)
        }.getOrNull() ?: return null

        val endpoints = row.endpoints.mapNotNull { endpoint ->
            val transport = runCatching {
                DeviceEndpoint.Transport.valueOf(endpoint.transport)
            }.getOrNull() ?: return@mapNotNull null

            runCatching {
                DeviceEndpoint(
                    host = endpoint.host,
                    port = endpoint.port,
                    transport = transport,
                )
            }.getOrNull()
        }.toSet()

        return KnownDevice(
            id = id,
            displayName = row.device.displayName,
            apiMajorVersion = row.device.apiMajorVersion,
            endpoints = endpoints,
            lastSeen = Instant.ofEpochMilli(
                row.device.lastSeenEpochMillis,
            ),
        )
    }
}
