package com.bodzey.proaudioplayer.data.api

import com.bodzey.proaudioplayer.core.api.ApiCapabilities
import com.bodzey.proaudioplayer.core.api.ApiHealth
import com.bodzey.proaudioplayer.core.api.PlayerApiClient
import com.bodzey.proaudioplayer.core.api.PlayerStatus
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

class OkHttpPlayerApiClient(
    private val client: OkHttpClient = defaultClient(),
    private val parser: ApiJsonParser = ApiJsonParser(),
) : PlayerApiClient {

    override suspend fun health(endpoint: DeviceEndpoint): ApiHealth =
        parser.health(get(endpoint, "/api/v1/health"))

    override suspend fun capabilities(endpoint: DeviceEndpoint): ApiCapabilities =
        parser.capabilities(get(endpoint, "/api/v1/capabilities"))

    override suspend fun status(endpoint: DeviceEndpoint): PlayerStatus =
        parser.status(get(endpoint, "/api/v1/status"))

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
