package com.bodzey.proaudioplayer.data.api

import com.bodzey.proaudioplayer.core.api.ApiCapabilities
import com.bodzey.proaudioplayer.core.api.ApiHealth
import com.bodzey.proaudioplayer.core.api.AlertAudioSettings
import com.bodzey.proaudioplayer.core.api.AlertMediaCatalog
import com.bodzey.proaudioplayer.core.api.AlertMediaFile
import com.bodzey.proaudioplayer.core.api.AlertProviderSettings
import com.bodzey.proaudioplayer.core.api.AudioLevelState
import com.bodzey.proaudioplayer.core.api.MpdState
import com.bodzey.proaudioplayer.core.api.PlayerControls
import com.bodzey.proaudioplayer.core.api.PriorityState
import com.bodzey.proaudioplayer.core.api.RadioStation
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
            db = null,
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
            db = masterObject?.optionalDouble("db"),
        )

        val priorityObject = root["priority"]?.jsonObject
        val mpdObject = root["mpd"]?.jsonObject

        return PlayerStatus(
            name = root.requiredString("name"),
            master = master,
            music = music,
            priority = PriorityState(
                mode = priorityObject.optionalString("mode").orEmpty(),
                active = priorityObject.optionalBoolean("active") ?: false,
                blocking = priorityObject.optionalBoolean("blocking") ?: false,
                duckOnlyDuringAnnouncement =
                    priorityObject.optionalBoolean("duck_only_during_announcement") ?: false,
                minuteSilenceActive =
                    priorityObject.optionalBoolean("minute_silence_active") ?: false,
                matchedUids = priorityObject
                    ?.get("matched_uids")
                    ?.jsonArray
                    ?.mapNotNull { value ->
                        value.jsonPrimitive.contentOrNull?.toLongOrNull()
                    }
                    .orEmpty(),
                lastSuccessAt = priorityObject.optionalString("last_success_at"),
                lastChangeAt = priorityObject.optionalString("last_change_at"),
                lastError = priorityObject.optionalString("last_error"),
            ),
            mpd = MpdState(
                isStream = mpdObject.optionalBoolean("is_stream") ?: false,
                streamUrl = mpdObject.optionalString("stream_url"),
            ),
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



    fun alertProviderSettings(payload: String): AlertProviderSettings {
        val root = objectRoot(payload)
        return AlertProviderSettings(
            endpoint = root.requiredString("endpoint"),
            locationUid = root.requiredLong("location_uid"),
            locationType = root.requiredString("location_type"),
            pollIntervalSeconds = root.requiredDouble("poll_interval_seconds"),
            requestTimeoutSeconds = root.requiredDouble("request_timeout_seconds"),
            rateLimitBackoffSeconds = root.requiredDouble("rate_limit_backoff_seconds"),
            clearConfirmations = root.requiredPositiveInt("clear_confirmations"),
            tokenConfigured = root.requiredBoolean("token_configured"),
        )
    }

    fun alertAudioSettings(payload: String): AlertAudioSettings {
        val root = objectRoot(payload)
        return AlertAudioSettings(
            airRaidAlertsEnabled = root.requiredBoolean("air_raid_alerts_enabled"),
            duckDb = root.requiredDouble("duck_db"),
            duckFadeSeconds = root.requiredDouble("duck_fade_seconds"),
            restoreFadeSeconds = root.requiredDouble("restore_fade_seconds"),
            alertVolumePercent = root.requiredDouble("alert_volume_percent"),
            defaultRestoreVolumePercent =
                root.requiredDouble("default_restore_volume_percent"),
            minuteSilenceVolumePercent =
                root.requiredDouble("minute_silence_volume_percent"),
            minuteSilenceEnabled = root.requiredBoolean("minute_silence_enabled"),
            minuteSilenceStartTime = root.requiredString("minute_silence_start_time"),
            minuteSilenceTimezone = root.requiredString("minute_silence_timezone"),
            minuteSilenceCatchUpSeconds =
                root.requiredLong("minute_silence_catch_up_seconds"),
            minuteSilenceMusicFadeSeconds =
                root.requiredDouble("minute_silence_music_fade_seconds"),
            alertRepeatIntervalMinutes =
                root.requiredLong("alert_repeat_interval_minutes"),
            duckOnlyDuringAnnouncement =
                root.requiredBoolean("duck_only_during_announcement"),
            sampleRateMode = root.requiredString("sample_rate_mode"),
            sampleRate = root.requiredPositiveInt("sample_rate"),
            allowedSampleRates = root["allowed_sample_rates"]
                ?.jsonArray
                ?.mapNotNull { value -> value.jsonPrimitive.intOrNull }
                .orEmpty(),
        )
    }

    fun alertMedia(payload: String): AlertMediaCatalog {
        val root = objectRoot(payload)
        val items = root["items"]
            ?.jsonArray
            ?.mapNotNull { element ->
                val item = runCatching { element.jsonObject }.getOrNull()
                    ?: return@mapNotNull null
                val kind = item.optionalString("kind")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val label = item.optionalString("label")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val fileName = item.optionalString("file_name")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                AlertMediaFile(
                    kind = kind,
                    label = label,
                    fileName = fileName,
                    configured = item.optionalBoolean("configured") ?: false,
                    sizeBytes = item.optionalLong("size_bytes"),
                    modifiedUnixSeconds = item.optionalLong("modified_unix_seconds"),
                    maxSizeBytes = item.optionalLong("max_size_bytes") ?: 0L,
                    contentType = item.optionalString("content_type").orEmpty(),
                )
            }
            .orEmpty()

        return AlertMediaCatalog(
            items = items,
            acceptedContentTypes = root["accepted_content_types"]
                ?.jsonArray
                ?.mapNotNull { value -> value.jsonPrimitive.contentOrNull }
                ?.toSet()
                .orEmpty(),
            maxSizeBytes = root.requiredLong("max_size_bytes"),
        )
    }

    fun radioStations(payload: String): List<RadioStation> {
        val root = objectRoot(payload)
        return root["items"]
            ?.jsonArray
            ?.mapNotNull { element ->
                val station = runCatching { element.jsonObject }.getOrNull()
                    ?: return@mapNotNull null
                val id = station.optionalString("id")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val name = station.optionalString("name")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val url = station.optionalString("url")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                RadioStation(
                    id = id,
                    name = name,
                    url = url,
                    homepage = station.optionalString("homepage"),
                    favicon = station.optionalString("favicon"),
                    tags = station["tags"]
                        ?.jsonArray
                        ?.mapNotNull { value -> value.jsonPrimitive.contentOrNull }
                        .orEmpty(),
                    codec = station.optionalString("codec"),
                    bitrate = station["bitrate"]?.jsonPrimitive?.intOrNull,
                    votes = station["votes"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.toLongOrNull()
                        ?: 0L,
                )
            }
            .orEmpty()
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

    private fun JsonObject.requiredLong(key: String): Long =
        this[key]?.jsonPrimitive?.contentOrNull
            ?.toLongOrNull()
            ?: throw ApiProtocolException("Missing or invalid '" + key + "'")

    private fun JsonObject.requiredBoolean(key: String): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull
            ?: throw ApiProtocolException("Missing or invalid '" + key + "'")

    private fun JsonObject?.optionalDouble(key: String): Double? =
        this?.get(key)?.jsonPrimitive?.doubleOrNull

    private fun JsonObject?.optionalBoolean(key: String): Boolean? =
        this?.get(key)?.jsonPrimitive?.booleanOrNull

    private fun JsonObject?.optionalLong(key: String): Long? =
        this?.get(key)?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    private fun JsonObject?.optionalString(key: String): String? =
        this?.get(key)?.jsonPrimitive?.contentOrNull

    private fun JsonObject.stringOrEmpty(key: String): String =
        optionalString(key).orEmpty()

    private fun JsonObject?.boolean(key: String): Boolean =
        this?.get(key)?.jsonPrimitive?.booleanOrNull ?: false
}

class ApiProtocolException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
