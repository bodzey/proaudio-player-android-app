package com.bodzey.proaudioplayer.data.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

class AndroidAlertMediaImporter(
    context: Context,
) : AlertMediaImporter {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun read(
        uriText: String,
        maxBytes: Long,
    ): ImportedAlertMedia = withContext(Dispatchers.IO) {
        require(maxBytes in 1..Int.MAX_VALUE.toLong()) {
            "Некоректне обмеження розміру файла"
        }

        val uri = Uri.parse(uriText)
        val metadata = queryMetadata(uri)
        metadata.sizeBytes?.let { size ->
            require(size <= maxBytes) {
                "MP3-файл перевищує дозволений розмір"
            }
        }

        val initialCapacity = metadata.sizeBytes
            ?.coerceAtMost(maxBytes)
            ?.toInt()
            ?: DEFAULT_INITIAL_CAPACITY
        val output = ByteArrayOutputStream(initialCapacity)

        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maxBytes) {
                    "MP3-файл перевищує дозволений розмір"
                }
                output.write(buffer, 0, read)
            }
        } ?: throw IllegalArgumentException("Не вдалося відкрити вибраний файл")

        val bytes = output.toByteArray()
        require(bytes.isNotEmpty()) {
            "Вибраний файл порожній"
        }

        ImportedAlertMedia(
            displayName = metadata.displayName ?: "alert.mp3",
            contentType = normalizedContentType(uri),
            bytes = bytes,
        )
    }

    private fun queryMetadata(uri: Uri): MediaMetadata {
        resolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return MediaMetadata()
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            return MediaMetadata(
                displayName = nameIndex
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let(cursor::getString),
                sizeBytes = sizeIndex
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let(cursor::getLong),
            )
        }
        return MediaMetadata()
    }

    private fun normalizedContentType(uri: Uri): String =
        resolver.getType(uri)
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.equals("audio/mpeg", ignoreCase = true) ||
                it.equals("audio/mp3", ignoreCase = true)
            }
            ?: "application/octet-stream"

    private data class MediaMetadata(
        val displayName: String? = null,
        val sizeBytes: Long? = null,
    )

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val DEFAULT_INITIAL_CAPACITY = 256 * 1024
    }
}
