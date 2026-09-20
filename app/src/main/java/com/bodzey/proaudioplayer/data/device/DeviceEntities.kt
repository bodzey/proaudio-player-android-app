package com.bodzey.proaudioplayer.data.device

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "known_devices",
)
data class KnownDeviceEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "api_major_version")
    val apiMajorVersion: Int,
    @ColumnInfo(name = "last_seen_epoch_millis")
    val lastSeenEpochMillis: Long,
)

@Entity(
    tableName = "known_device_endpoints",
    primaryKeys = ["device_id", "host", "port", "transport"],
    foreignKeys = [
        ForeignKey(
            entity = KnownDeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("device_id"),
    ],
)
data class KnownEndpointEntity(
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    val host: String,
    val port: Int,
    val transport: String,
)

data class KnownDeviceWithEndpoints(
    @Embedded
    val device: KnownDeviceEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "device_id",
    )
    val endpoints: List<KnownEndpointEntity>,
)
