package com.bodzey.proaudioplayer.data.device

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        KnownDeviceEntity::class,
        KnownEndpointEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ProAudioDatabase : RoomDatabase() {
    abstract fun knownDeviceDao(): KnownDeviceDao

    companion object {
        fun create(context: Context): ProAudioDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ProAudioDatabase::class.java,
                DATABASE_NAME,
            ).build()

        private const val DATABASE_NAME = "proaudio-player.db"
    }
}
