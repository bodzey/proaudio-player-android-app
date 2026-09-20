package com.bodzey.proaudioplayer.data.api

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import kotlinx.coroutines.flow.first
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

    @Test
    fun statusEventsParsesStatusSseEvent() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "text/event-stream")
                    .body(
                        """
                        : keepalive
                        event: status
                        data: {"name":"ProAudio Player","volume":50.0,"muted":false,"player":{"source":"DLNA / UPnP","backend":"dlna-upnp","state":"playing","title":"Track","artist":"","album":"","position_seconds":null,"duration_seconds":null,"progress":0,"controls":{"play":false,"pause":true,"stop":true,"next":false,"prev":false}}}

                        """.trimIndent(),
                    )
                    .build(),
            )

            val client = OkHttpPlayerApiClient(
                client = OkHttpClient(),
            )
            val endpoint = DeviceEndpoint(
                host = server.hostName,
                port = server.port,
            )

            val status = runBlocking {
                client.statusEvents(endpoint).first()
            }

            assertEquals("DLNA / UPnP", status.player.source)
            assertEquals("Track", status.player.title)
            assertEquals(
                "GET /api/v1/events HTTP/1.1",
                server.takeRequest().requestLine,
            )
        }
    }
}
