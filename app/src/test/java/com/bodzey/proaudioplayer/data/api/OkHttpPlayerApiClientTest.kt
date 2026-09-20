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

    @Test
    fun radioStationsUseCanonicalDirectoryEndpoint() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body(
                        """
                        {
                          "source":"radio-browser",
                          "items":[
                            {
                              "id":"station-1",
                              "name":"Test FM",
                              "url":"https://radio.example/live",
                              "homepage":null,
                              "favicon":null,
                              "tags":[],
                              "codec":"AAC",
                              "bitrate":128,
                              "votes":7
                            }
                          ]
                        }
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

            val stations = runBlocking {
                client.radioStations(endpoint)
            }

            assertEquals(1, stations.size)
            assertEquals("Test FM", stations.single().name)
            assertEquals(
                "GET /api/v1/radio/stations HTTP/1.1",
                server.takeRequest().requestLine,
            )
        }
    }

    @Test
    fun playStreamEscapesUrlInJsonBody() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body("""{"playing":"https://radio.example/live"}""")
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
                client.playStream(
                    endpoint,
                    "https://radio.example/live?name=\"quoted\"",
                )
            }

            val request = server.takeRequest()
            assertEquals(
                "POST /api/v1/streams/play HTTP/1.1",
                request.requestLine,
            )
            assertEquals(
                """{"url":"https://radio.example/live?name=\"quoted\""}""",
                request.body?.utf8(),
            )
        }
    }

    @Test
    fun alertReadEndpointsUseStableV1Paths() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body(
                        """{"endpoint":"https://api.example/{uid}","location_uid":1,"location_type":"city","poll_interval_seconds":8.0,"request_timeout_seconds":7.0,"rate_limit_backoff_seconds":60.0,"clear_confirmations":2,"token_configured":true}""",
                    )
                    .build(),
            )
            server.enqueue(
                MockResponse.Builder()
                    .body(
                        """{"air_raid_alerts_enabled":true,"duck_db":-12.0,"duck_fade_seconds":1.0,"restore_fade_seconds":3.0,"alert_volume_percent":89.1,"default_restore_volume_percent":89.1,"minute_silence_volume_percent":100.0,"minute_silence_enabled":true,"minute_silence_start_time":"08:59:50","minute_silence_timezone":"Europe/Kyiv","minute_silence_catch_up_seconds":120,"minute_silence_music_fade_seconds":1.0,"alert_repeat_interval_minutes":0,"duck_only_during_announcement":false,"sample_rate_mode":"fixed","sample_rate":48000,"allowed_sample_rates":[44100,48000]}""",
                    )
                    .build(),
            )
            server.enqueue(
                MockResponse.Builder()
                    .body(
                        """{"items":[],"accepted_content_types":["audio/mpeg"],"max_size_bytes":16777216}""",
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

            runBlocking {
                client.alertProviderSettings(endpoint)
                client.alertAudioSettings(endpoint)
                client.alertMedia(endpoint)
            }

            assertEquals(
                "GET /api/v1/settings/alerts HTTP/1.1",
                server.takeRequest().requestLine,
            )
            assertEquals(
                "GET /api/v1/settings/audio HTTP/1.1",
                server.takeRequest().requestLine,
            )
            assertEquals(
                "GET /api/v1/settings/alerts/media HTTP/1.1",
                server.takeRequest().requestLine,
            )
        }
    }

}
