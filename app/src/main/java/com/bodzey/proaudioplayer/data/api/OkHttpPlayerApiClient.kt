package com.bodzey.proaudioplayer.data.api

import com.bodzey.proaudioplayer.core.api.ApiCapabilities
import com.bodzey.proaudioplayer.core.api.ApiHealth
import com.bodzey.proaudioplayer.core.api.AlertAudioSettings
import com.bodzey.proaudioplayer.core.api.AlertAudioUpdate
import com.bodzey.proaudioplayer.core.api.AlertMediaCatalog
import com.bodzey.proaudioplayer.core.api.AlertMediaFile
import com.bodzey.proaudioplayer.core.api.AlertProviderSettings
import com.bodzey.proaudioplayer.core.api.AlertProviderTestResult
import com.bodzey.proaudioplayer.core.api.AlertProviderUpdate
import com.bodzey.proaudioplayer.core.api.PlayerAction
import com.bodzey.proaudioplayer.core.api.PlayerApiClient
import com.bodzey.proaudioplayer.core.api.PlayerStatus
import com.bodzey.proaudioplayer.core.api.RadioStation
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

class OkHttpPlayerApiClient(
    private val client: OkHttpClient = defaultClient(),
) : PlayerApiClient {

    private val parser = ApiJsonParser()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    private val eventClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val providerTestClient: OkHttpClient = client.newBuilder()
        .readTimeout(125, TimeUnit.SECONDS)
        .callTimeout(130, TimeUnit.SECONDS)
        .build()

    private val mediaClient: OkHttpClient = client.newBuilder()
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun health(endpoint: DeviceEndpoint): ApiHealth =
        parser.health(get(endpoint, "/api/v1/health"))

    override suspend fun capabilities(endpoint: DeviceEndpoint): ApiCapabilities =
        parser.capabilities(get(endpoint, "/api/v1/capabilities"))

    override suspend fun status(endpoint: DeviceEndpoint): PlayerStatus =
        parser.status(get(endpoint, "/api/v1/status"))

    override suspend fun playerAction(
        endpoint: DeviceEndpoint,
        action: PlayerAction,
    ) {
        postJson(
            endpoint = endpoint,
            path = "/api/v1/player",
            jsonBody = """{"action":"${action.wireValue}"}""",
        )
    }

    override suspend fun setMasterVolume(
        endpoint: DeviceEndpoint,
        percent: Double,
    ) {
        postJson(
            endpoint = endpoint,
            path = "/api/v1/audio/level",
            jsonBody = """{"target":"master","percent":$percent}""",
        )
    }

    override suspend fun setMasterMute(
        endpoint: DeviceEndpoint,
        db: Double,
        muted: Boolean,
    ) {
        postJson(
            endpoint = endpoint,
            path = "/api/v1/audio/mixer",
            jsonBody = """{"target":"master","db":$db,"muted":$muted}""",
        )
    }

    override suspend fun radioStations(endpoint: DeviceEndpoint): List<RadioStation> =
        parser.radioStations(get(endpoint, "/api/v1/radio/stations"))

    override suspend fun playStream(
        endpoint: DeviceEndpoint,
        url: String,
    ) {
        val encodedUrl = JsonPrimitive(url).toString()
        postJson(
            endpoint = endpoint,
            path = "/api/v1/streams/play",
            jsonBody = """{"url":$encodedUrl}""",
        )
    }

    override suspend fun alertProviderSettings(
        endpoint: DeviceEndpoint,
    ): AlertProviderSettings =
        parser.alertProviderSettings(get(endpoint, "/api/v1/settings/alerts"))

    override suspend fun alertAudioSettings(
        endpoint: DeviceEndpoint,
    ): AlertAudioSettings =
        parser.alertAudioSettings(get(endpoint, "/api/v1/settings/audio"))

    override suspend fun alertMedia(
        endpoint: DeviceEndpoint,
    ): AlertMediaCatalog =
        parser.alertMedia(get(endpoint, "/api/v1/settings/alerts/media"))

    override suspend fun saveAlertProviderSettings(
        endpoint: DeviceEndpoint,
        update: AlertProviderUpdate,
    ): AlertProviderSettings =
        parser.alertProviderSettings(
            putJsonForBody(
                endpoint = endpoint,
                path = "/api/v1/settings/alerts",
                jsonBody = providerUpdateJson(update),
            ),
        )

    override suspend fun testAlertProviderSettings(
        endpoint: DeviceEndpoint,
        update: AlertProviderUpdate,
    ): AlertProviderTestResult =
        parser.alertProviderTest(
            postJsonForBody(
                endpoint = endpoint,
                path = "/api/v1/settings/alerts/test",
                jsonBody = providerUpdateJson(update),
                requestClient = providerTestClient,
            ),
        )

    override suspend fun saveAlertAudioSettings(
        endpoint: DeviceEndpoint,
        update: AlertAudioUpdate,
    ): AlertAudioSettings =
        parser.alertAudioSettings(
            putJsonForBody(
                endpoint = endpoint,
                path = "/api/v1/settings/audio",
                jsonBody = audioUpdateJson(update),
            ),
        )

    override suspend fun uploadAlertMedia(
        endpoint: DeviceEndpoint,
        kind: String,
        bytes: ByteArray,
        contentType: String,
    ): AlertMediaFile {
        val safeKind = requireAlertMediaKind(kind)
        val mediaType = runCatching { contentType.toMediaType() }
            .getOrElse { "application/octet-stream".toMediaType() }
        val request = Request.Builder()
            .url(endpoint.apiUrl("/api/v1/settings/alerts/media/$safeKind"))
            .header("Accept", "application/json")
            .put(bytes.toRequestBody(mediaType))
            .build()
        return parser.alertMediaFile(executeForBody(mediaClient, request))
    }

    override suspend fun resetAlertMedia(
        endpoint: DeviceEndpoint,
        kind: String,
    ): AlertMediaFile {
        val safeKind = requireAlertMediaKind(kind)
        val request = Request.Builder()
            .url(endpoint.apiUrl("/api/v1/settings/alerts/media/$safeKind"))
            .header("Accept", "application/json")
            .delete()
            .build()
        return parser.alertMediaFile(executeForBody(mediaClient, request))
    }

    override fun statusEvents(endpoint: DeviceEndpoint): Flow<PlayerStatus> = channelFlow {
        val request = Request.Builder()
            .url(endpoint.apiUrl("/api/v1/events"))
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()
        val call = eventClient.newCall(request)

        val reader = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw PlayerApiException(
                            statusCode = response.code,
                            message = "Player event stream returned HTTP " + response.code,
                        )
                    }

                    val source = response.body.source()
                    var eventType: String? = null
                    val dataLines = mutableListOf<String>()

                    suspend fun dispatchEvent() {
                        if (eventType == "status" && dataLines.isNotEmpty()) {
                            send(parser.status(dataLines.joinToString("\n")))
                        }
                        eventType = null
                        dataLines.clear()
                    }

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.isEmpty() -> dispatchEvent()
                            line.startsWith(":") -> Unit
                            line.startsWith("event:") ->
                                eventType = line.substringAfter(':').trimStart()
                            line.startsWith("data:") ->
                                dataLines += line.substringAfter(':').trimStart()
                        }
                    }

                    dispatchEvent()
                }
                close()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                close(error)
            }
        }

        awaitClose {
            call.cancel()
            reader.cancel()
        }
    }

    private suspend fun postJson(
        endpoint: DeviceEndpoint,
        path: String,
        jsonBody: String,
    ) {
        postJsonForBody(
            endpoint = endpoint,
            path = path,
            jsonBody = jsonBody,
        )
    }

    private suspend fun postJsonForBody(
        endpoint: DeviceEndpoint,
        path: String,
        jsonBody: String,
        requestClient: OkHttpClient = client,
    ): String {
        val request = Request.Builder()
            .url(endpoint.apiUrl(path))
            .header("Accept", "application/json")
            .post(jsonBody.toJsonRequestBody())
            .build()
        return executeForBody(requestClient, request)
    }

    private suspend fun putJsonForBody(
        endpoint: DeviceEndpoint,
        path: String,
        jsonBody: String,
    ): String {
        val request = Request.Builder()
            .url(endpoint.apiUrl(path))
            .header("Accept", "application/json")
            .put(jsonBody.toJsonRequestBody())
            .build()
        return executeForBody(client, request)
    }

    private suspend fun get(
        endpoint: DeviceEndpoint,
        path: String,
    ): String {
        val request = Request.Builder()
            .url(endpoint.apiUrl(path))
            .header("Accept", "application/json")
            .build()
        return executeForBody(client, request)
    }

    private suspend fun executeForBody(
        requestClient: OkHttpClient,
        request: Request,
    ): String =
        requestClient.newCall(request).executeAsync().use { response ->
            val body = withContext(Dispatchers.IO) {
                response.body.string()
            }

            if (!response.isSuccessful) {
                throw PlayerApiException(
                    statusCode = response.code,
                    message = apiErrorMessage(
                        statusCode = response.code,
                        body = body,
                    ),
                )
            }
            if (body.isBlank()) {
                throw ApiProtocolException("Player API returned an empty response")
            }
            body
        }

    private fun apiErrorMessage(
        statusCode: Int,
        body: String,
    ): String =
        runCatching {
            json.parseToJsonElement(body)
                .jsonObject["error"]
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Player API returned HTTP $statusCode"

    private fun String.toJsonRequestBody() =
        toRequestBody("application/json; charset=utf-8".toMediaType())

    private fun providerUpdateJson(update: AlertProviderUpdate): String =
        buildJsonObject {
            put("endpoint", update.endpoint)
            put("location_uid", update.locationUid)
            put("location_type", update.locationType)
            put("poll_interval_seconds", update.pollIntervalSeconds)
            put("request_timeout_seconds", update.requestTimeoutSeconds)
            put("rate_limit_backoff_seconds", update.rateLimitBackoffSeconds)
            put("clear_confirmations", update.clearConfirmations)
            update.token?.let { token ->
                put("token", token)
            }
        }.toString()

    private fun audioUpdateJson(update: AlertAudioUpdate): String =
        buildJsonObject {
            put("air_raid_alerts_enabled", update.airRaidAlertsEnabled)
            put("duck_db", update.duckDb)
            put("duck_fade_seconds", update.duckFadeSeconds)
            put("restore_fade_seconds", update.restoreFadeSeconds)
            put("alert_volume_percent", update.alertVolumePercent)
            put(
                "default_restore_volume_percent",
                update.defaultRestoreVolumePercent,
            )
            put(
                "minute_silence_volume_percent",
                update.minuteSilenceVolumePercent,
            )
            put("minute_silence_enabled", update.minuteSilenceEnabled)
            put("minute_silence_start_time", update.minuteSilenceStartTime)
            put("minute_silence_timezone", update.minuteSilenceTimezone)
            put(
                "minute_silence_catch_up_seconds",
                update.minuteSilenceCatchUpSeconds,
            )
            put(
                "minute_silence_music_fade_seconds",
                update.minuteSilenceMusicFadeSeconds,
            )
            put(
                "alert_repeat_interval_minutes",
                update.alertRepeatIntervalMinutes,
            )
            put(
                "duck_only_during_announcement",
                update.duckOnlyDuringAnnouncement,
            )
        }.toString()

    private fun requireAlertMediaKind(kind: String): String {
        require(kind in ALERT_MEDIA_KINDS) {
            "Unsupported alert media kind: $kind"
        }
        return kind
    }

    companion object {
        private val ALERT_MEDIA_KINDS =
            setOf("alarm_start", "alarm_end", "minute_silence")

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
    }
}

class PlayerApiException(
    val statusCode: Int,
    message: String,
    cause: IOException? = null,
) : IOException(message, cause)
