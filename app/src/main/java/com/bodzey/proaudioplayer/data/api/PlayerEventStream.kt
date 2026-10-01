package com.bodzey.proaudioplayer.data.api

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

internal class PlayerEventStream(
    client: OkHttpClient,
    private val errorMessage: (Int, String) -> String,
) {

    private val eventClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun <T> events(
        endpoint: DeviceEndpoint,
        path: String,
        eventName: String,
        parse: (String) -> T,
    ): Flow<T> = channelFlow {
        val request = Request.Builder()
            .url(endpoint.apiUrl(path))
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
                            message = errorMessage(response.code, response.body.string()),
                        )
                    }

                    val source = response.body.source()
                    var currentEvent: String? = null
                    val dataLines = mutableListOf<String>()

                    suspend fun dispatchEvent() {
                        if (currentEvent == eventName && dataLines.isNotEmpty()) {
                            send(parse(dataLines.joinToString("\n")))
                        }
                        currentEvent = null
                        dataLines.clear()
                    }

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.isEmpty() -> dispatchEvent()
                            line.startsWith(":") -> Unit
                            line.startsWith("event:") ->
                                currentEvent = line.substringAfter(':').trimStart()
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
}
