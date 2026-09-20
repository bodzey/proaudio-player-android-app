package com.bodzey.proaudioplayer.core.media

data class ImportedAlertMedia(
    val displayName: String,
    val contentType: String,
    val bytes: ByteArray,
)

interface AlertMediaImporter {
    suspend fun read(
        uriText: String,
        maxBytes: Long,
    ): ImportedAlertMedia
}
