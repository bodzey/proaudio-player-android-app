package com.bodzey.proaudioplayer.data.device

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class KnownDeviceDao {
    @Transaction
    @Query(
        """
        SELECT * FROM known_devices
        ORDER BY display_name COLLATE NOCASE, id
        """,
    )
    abstract fun observeAll(): Flow<List<KnownDeviceWithEndpoints>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertDevice(device: KnownDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertEndpoints(
        endpoints: List<KnownEndpointEntity>,
    )

    @Query(
        """
        DELETE FROM known_device_endpoints
        WHERE device_id = :deviceId
        """,
    )
    protected abstract suspend fun deleteEndpoints(deviceId: String)

    @Transaction
    open suspend fun replaceObserved(
        device: KnownDeviceEntity,
        endpoints: List<KnownEndpointEntity>,
    ) {
        upsertDevice(device)
        deleteEndpoints(device.id)
        if (endpoints.isNotEmpty()) {
            upsertEndpoints(endpoints)
        }
    }
}
