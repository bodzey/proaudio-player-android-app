package com.bodzey.proaudioplayer.data.api

import com.bodzey.proaudioplayer.core.api.ApiCapabilities
import com.bodzey.proaudioplayer.core.api.ApiHealth
import com.bodzey.proaudioplayer.core.api.PlayerApiClient
import com.bodzey.proaudioplayer.core.api.PlayerStatus
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

class OkHttpPlayerApiClient(
    private val client: OkHttpClient = defaultClient(),
    private val parser: ApiJsonParser = ApiJsonParser(),
) : PlayerApiClient {

    private val eventClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun health(endpoint: DeviceEndpoint): ApiHealth =
        parser.health(get(endpoint, "/api/v1/health"))

    override suspend fun capabilities(endpoint: DeviceEndpoint): ApiCapabilities =
        parser.capabilities(get(endpoint, "/api/v1/capabilities"))

    override suspend fun status(endpoint: DeviceEndpoint): PlayerStatus =
        parser.status(get(endpoint, "/api/v1/status"))

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

    private suspend fun get(
        endpoint: DeviceEndpoint,
        path: String,
    ): String {
        val request = Request.Builder()
            .url(endpoint.apiUrl(path))
            .header("Accept", "application/json")
            .build()

        client.newCall(request).executeAsync().use { response ->
            val body = withContext(Dispatchers.IO) {
                response.body.string()
            }

            if (!response.isSuccessful) {
                throw PlayerApiException(
                    statusCode = response.code,
                    message = "Player API returned HTTP " + response.code,
                )
            }
            if (body.isBlank()) {
                throw ApiProtocolException("Player API returned an empty response")
            }
            return body
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .writeTimeout(4, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
    }
}

class PlayerApiException(
    val statusCode: Int,
    message: String,
    cause: IOException? = null,
) : IOException(message, cause)
