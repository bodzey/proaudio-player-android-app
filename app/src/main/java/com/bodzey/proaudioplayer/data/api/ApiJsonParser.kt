package com.bodzey.proaudioplayer.data.api

import com.bodzey.proaudioplayer.core.api.ApiCapabilities
import com.bodzey.proaudioplayer.core.api.ApiHealth
import com.bodzey.proaudioplayer.core.api.AudioLevelState
import com.bodzey.proaudioplayer.core.api.PlayerControls
import com.bodzey.proaudioplayer.core.api.PlayerState
import com.bodzey.proaudioplayer.core.api.PlayerStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class ApiJsonParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    },
) {
    fun health(payload: String): ApiHealth {
        val root = objectRoot(payload)
        return ApiHealth(
            status = root.requiredString("status"),
            apiMajorVersion = root.requiredPositiveInt("api_version"),
        )
    }

    fun capabilities(payload: String): ApiCapabilities {
        val root = objectRoot(payload)
        val features = root["features"]
            ?.jsonArray
            ?.mapNotNull { value -> value.jsonPrimitive.contentOrNull }
            ?.toSet()
            .orEmpty()

        return ApiCapabilities(
            apiMajorVersion = root.requiredPositiveInt("api_version"),
            eventTransport = root["events"]?.jsonPrimitive?.contentOrNull,
            features = features,
        )
    }

    fun status(payload: String): PlayerStatus {
        val root = objectRoot(payload)
        val player = root["player"]?.jsonObject
            ?: throw ApiProtocolException("Missing player object")

        val music = AudioLevelState(
            volumePercent = root.requiredDouble("volume"),
            muted = root.requiredBoolean("muted"),
        )
        val masterObject = root["audio_levels"]
            ?.jsonObject
            ?.get("master")
            ?.jsonObject
        val master = AudioLevelState(
            volumePercent = masterObject?.optionalDouble("volume")
                ?: music.volumePercent,
            muted = masterObject?.optionalBoolean("muted")
                ?: music.muted,
        )

        return PlayerStatus(
            name = root.requiredString("name"),
            master = master,
            music = music,
            player = PlayerState(
                source = player.stringOrEmpty("source"),
                backend = player.stringOrEmpty("backend"),
                state = player.stringOrEmpty("state"),
                title = player.stringOrEmpty("title"),
                artist = player.stringOrEmpty("artist"),
                album = player.stringOrEmpty("album"),
                positionSeconds = player["position_seconds"]?.jsonPrimitive?.doubleOrNull,
                durationSeconds = player["duration_seconds"]?.jsonPrimitive?.doubleOrNull,
                progressPercent = player["progress"]?.jsonPrimitive?.intOrNull
                    ?.coerceIn(0, 100)
                    ?: 0,
                controls = player.controls(),
            ),
        )
    }

    private fun objectRoot(payload: String): JsonObject =
        runCatching { json.parseToJsonElement(payload).jsonObject }
            .getOrElse { error ->
                throw ApiProtocolException("Invalid JSON response", error)
            }

    private fun JsonObject.controls(): PlayerControls {
        val controls = this["controls"]?.jsonObject
        return PlayerControls(
            play = controls.boolean("play"),
            pause = controls.boolean("pause"),
            stop = controls.boolean("stop"),
            next = controls.boolean("next"),
            previous = controls.boolean("prev"),
        )
    }

    private fun JsonObject.requiredString(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw ApiProtocolException("Missing or invalid '" + key + "'")

    private fun JsonObject.requiredPositiveInt(key: String): Int =
        this[key]?.jsonPrimitive?.contentOrNull
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: throw ApiProtocolException("Missing or invalid '" + key + "'")

    private fun JsonObject.requiredDouble(key: String): Double =
        this[key]?.jsonPrimitive?.doubleOrNull
            ?: throw ApiProtocolException("Missing or invalid '" + key + "'")

    private fun JsonObject.requiredBoolean(key: String): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull
            ?: throw ApiProtocolException("Missing or invalid '" + key + "'")

    private fun JsonObject.optionalDouble(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.optionalBoolean(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.stringOrEmpty(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject?.boolean(key: String): Boolean =
        this?.get(key)?.jsonPrimitive?.booleanOrNull ?: false
}

class ApiProtocolException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
