package com.bodzey.proaudioplayer.data.api

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpPlayerApiClientTest {

    @Test
    fun healthUsesStableV1Path() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body("""{"status":"ok","api_version":"1"}""")
                    .build(),
            )

            val client = OkHttpPlayerApiClient(
                client = OkHttpClient(),
            )
            val endpoint = DeviceEndpoint(
                host = server.hostName,
                port = server.port,
            )

            val health = runBlocking {
                client.health(endpoint)
            }

            assertEquals("ok", health.status)
            assertEquals(1, health.apiMajorVersion)
            assertEquals(
                "GET /api/v1/health HTTP/1.1",
                server.takeRequest().requestLine,
            )
        }
    }
}
