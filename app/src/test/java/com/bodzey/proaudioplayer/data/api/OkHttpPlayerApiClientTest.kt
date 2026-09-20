package com.bodzey.proaudioplayer.data.api

import com.bodzey.proaudioplayer.core.api.PlayerAction
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
    @Test
    fun playerActionPostsNativeControlContract() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body("""{"action":"pause"}""")
                    .build(),
            )

            val client = OkHttpPlayerApiClient(
                client = OkHttpClient(),
            )
            val endpoint = DeviceEndpoint(
                host = server.hostName,
                port = server.port,
            )

            runBlocking {
                client.playerAction(endpoint, PlayerAction.Pause)
            }

            val request = server.takeRequest()
            assertEquals(
                "POST /api/v1/player HTTP/1.1",
                request.requestLine,
            )
            assertEquals(
                """{"action":"pause"}""",
                request.body?.utf8(),
            )
        }
    }

    @Test
    fun masterVolumeUsesCanonicalAudioLevelEndpoint() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body("""{"volume":64.0,"db":-9.1,"muted":false}""")
                    .build(),
            )

            val client = OkHttpPlayerApiClient(
                client = OkHttpClient(),
            )
            val endpoint = DeviceEndpoint(
                host = server.hostName,
                port = server.port,
            )

            runBlocking {
                client.setMasterVolume(endpoint, 64.0)
            }

            val request = server.takeRequest()
            assertEquals(
                "POST /api/v1/audio/level HTTP/1.1",
                request.requestLine,
            )
            assertEquals(
                """{"target":"master","percent":64.0}""",
                request.body?.utf8(),
            )
        }
    }

    @Test
    fun masterMutePreservesCurrentDb() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body("""{"target":"master","db":-17.25,"muted":true}""")
                    .build(),
            )

            val client = OkHttpPlayerApiClient(
                client = OkHttpClient(),
            )
            val endpoint = DeviceEndpoint(
                host = server.hostName,
                port = server.port,
            )

            runBlocking {
                client.setMasterMute(
                    endpoint = endpoint,
                    db = -17.25,
                    muted = true,
                )
            }

            val request = server.takeRequest()
            assertEquals(
                "POST /api/v1/audio/mixer HTTP/1.1",
                request.requestLine,
            )
            assertEquals(
                """{"target":"master","db":-17.25,"muted":true}""",
                request.body?.utf8(),
            )
        }
    }

}
