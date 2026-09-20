package com.bodzey.proaudioplayer.data.api

import com.bodzey.proaudioplayer.core.api.AlertAudioUpdate
import com.bodzey.proaudioplayer.core.api.AlertProviderUpdate
import com.bodzey.proaudioplayer.core.api.PlayerAction
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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


    @Test
    fun providerSettingsSaveUsesPutAndCanonicalFields() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body(
                        """{"endpoint":"https://api.example/{uid}","location_uid":1133,"location_type":"hromada","poll_interval_seconds":8.0,"request_timeout_seconds":7.0,"rate_limit_backoff_seconds":60.0,"clear_confirmations":2,"token_configured":true}""",
                    )
                    .build(),
            )

            val client = OkHttpPlayerApiClient(client = OkHttpClient())
            val endpoint = DeviceEndpoint(server.hostName, server.port)

            val saved = runBlocking {
                client.saveAlertProviderSettings(
                    endpoint,
                    AlertProviderUpdate(
                        endpoint = "https://api.example/{uid}",
                        locationUid = 1133,
                        locationType = "hromada",
                        pollIntervalSeconds = 8.0,
                        requestTimeoutSeconds = 7.0,
                        rateLimitBackoffSeconds = 60.0,
                        clearConfirmations = 2,
                        token = "secret-token",
                    ),
                )
            }

            assertEquals(1133L, saved.locationUid)
            val request = server.takeRequest()
            assertEquals(
                "PUT /api/v1/settings/alerts HTTP/1.1",
                request.requestLine,
            )
            val body = request.body?.utf8().orEmpty()
            assertTrue(body.contains("\"location_uid\":1133"))
            assertTrue(body.contains("\"token\":\"secret-token\""))
        }
    }

    @Test
    fun providerSettingsTestDoesNotPersistAndUsesPost() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body(
                        """{"ok":true,"active":false,"state":"clear","location_uid":1133}""",
                    )
                    .build(),
            )

            val client = OkHttpPlayerApiClient(client = OkHttpClient())
            val endpoint = DeviceEndpoint(server.hostName, server.port)

            val result = runBlocking {
                client.testAlertProviderSettings(
                    endpoint,
                    AlertProviderUpdate(
                        endpoint = "https://api.example/{uid}",
                        locationUid = 1133,
                        locationType = "city",
                        pollIntervalSeconds = 8.0,
                        requestTimeoutSeconds = 7.0,
                        rateLimitBackoffSeconds = 60.0,
                        clearConfirmations = 2,
                    ),
                )
            }

            assertTrue(result.ok)
            assertEquals("clear", result.state)
            assertEquals(
                "POST /api/v1/settings/alerts/test HTTP/1.1",
                server.takeRequest().requestLine,
            )
        }
    }

    @Test
    fun audioSettingsSaveUsesCanonicalPutContract() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body(
                        """{"air_raid_alerts_enabled":false,"duck_db":-10.0,"duck_fade_seconds":1.5,"restore_fade_seconds":2.5,"alert_volume_percent":88.0,"default_restore_volume_percent":87.0,"minute_silence_volume_percent":100.0,"minute_silence_enabled":true,"minute_silence_start_time":"09:00:00","minute_silence_timezone":"Europe/Kyiv","minute_silence_catch_up_seconds":120,"minute_silence_music_fade_seconds":1.0,"alert_repeat_interval_minutes":15,"duck_only_during_announcement":true,"sample_rate_mode":"fixed","sample_rate":48000,"allowed_sample_rates":[44100,48000]}""",
                    )
                    .build(),
            )

            val client = OkHttpPlayerApiClient(client = OkHttpClient())
            val endpoint = DeviceEndpoint(server.hostName, server.port)

            runBlocking {
                client.saveAlertAudioSettings(
                    endpoint,
                    AlertAudioUpdate(
                        airRaidAlertsEnabled = false,
                        duckDb = -10.0,
                        duckFadeSeconds = 1.5,
                        restoreFadeSeconds = 2.5,
                        alertVolumePercent = 88.0,
                        defaultRestoreVolumePercent = 87.0,
                        minuteSilenceVolumePercent = 100.0,
                        minuteSilenceEnabled = true,
                        minuteSilenceStartTime = "09:00:00",
                        minuteSilenceTimezone = "Europe/Kyiv",
                        minuteSilenceCatchUpSeconds = 120,
                        minuteSilenceMusicFadeSeconds = 1.0,
                        alertRepeatIntervalMinutes = 15,
                        duckOnlyDuringAnnouncement = true,
                    ),
                )
            }

            val request = server.takeRequest()
            assertEquals(
                "PUT /api/v1/settings/audio HTTP/1.1",
                request.requestLine,
            )
            val body = request.body?.utf8().orEmpty()
            assertTrue(body.contains("\"air_raid_alerts_enabled\":false"))
            assertTrue(body.contains("\"duck_only_during_announcement\":true"))
        }
    }

    @Test
    fun alertMediaUploadUsesRawMp3Payload() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body(
                        """{"kind":"alarm_start","label":"Повітряна тривога","file_name":"alarm_start.mp3","configured":true,"size_bytes":4,"modified_unix_seconds":1770000000,"max_size_bytes":16777216,"content_type":"audio/mpeg"}""",
                    )
                    .build(),
            )

            val client = OkHttpPlayerApiClient(client = OkHttpClient())
            val endpoint = DeviceEndpoint(server.hostName, server.port)
            val payload = byteArrayOf(0x49, 0x44, 0x33, 0x04)

            val media = runBlocking {
                client.uploadAlertMedia(
                    endpoint = endpoint,
                    kind = "alarm_start",
                    bytes = payload,
                    contentType = "audio/mpeg",
                )
            }

            assertEquals("alarm_start", media.kind)
            val request = server.takeRequest()
            assertEquals(
                "PUT /api/v1/settings/alerts/media/alarm_start HTTP/1.1",
                request.requestLine,
            )
            assertEquals("audio/mpeg", request.headers["Content-Type"])
            assertTrue(request.body?.readByteArray()?.contentEquals(payload) == true)
        }
    }

    @Test
    fun alertMediaResetUsesDelete() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .body(
                        """{"kind":"alarm_end","label":"Відбій тривоги","file_name":"alarm_end.mp3","configured":true,"size_bytes":1000,"modified_unix_seconds":1770000000,"max_size_bytes":16777216,"content_type":"audio/mpeg"}""",
                    )
                    .build(),
            )

            val client = OkHttpPlayerApiClient(client = OkHttpClient())
            val endpoint = DeviceEndpoint(server.hostName, server.port)

            runBlocking {
                client.resetAlertMedia(endpoint, "alarm_end")
            }

            assertEquals(
                "DELETE /api/v1/settings/alerts/media/alarm_end HTTP/1.1",
                server.takeRequest().requestLine,
            )
        }
    }

    @Test
    fun backendErrorMessageIsPreserved() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(400)
                    .body("""{"error":"provider.location_uid має бути додатним"}""")
                    .build(),
            )

            val client = OkHttpPlayerApiClient(client = OkHttpClient())
            val endpoint = DeviceEndpoint(server.hostName, server.port)

            val error = assertThrows(PlayerApiException::class.java) {
                runBlocking {
                    client.saveAlertProviderSettings(
                        endpoint,
                        AlertProviderUpdate(
                            endpoint = "https://api.example/{uid}",
                            locationUid = 0,
                            locationType = "city",
                            pollIntervalSeconds = 8.0,
                            requestTimeoutSeconds = 7.0,
                            rateLimitBackoffSeconds = 60.0,
                            clearConfirmations = 2,
                        ),
                    )
                }
            }

            assertEquals(
                "provider.location_uid має бути додатним",
                error.message,
            )
        }
    }


    @Test
    fun meterEventsUseDedicatedV1SsePath() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "text/event-stream")
                    .body(
                        """
                        : meter-keepalive
                        event: meter
                        data: {"sequence":7,"sample_rate":48000,"interval_ms":20,"master":{"peak":[-1.0,-2.0],"rms":[-7.0,-8.0],"clip":[false,false],"available":true},"music":{"peak":[-10.0,-11.0],"rms":[-16.0,-17.0],"clip":[false,false],"available":true},"alert":{"peak":[-60.0,-60.0],"rms":[-60.0,-60.0],"clip":[false,false],"available":false}}

                        """.trimIndent(),
                    )
                    .build(),
            )

            val api = OkHttpPlayerApiClient(
                client = OkHttpClient(),
            )
            val endpoint = DeviceEndpoint(
                host = server.hostName,
                port = server.port,
            )

            val frame = runBlocking {
                api.meterEvents(endpoint).first()
            }

            assertEquals(7L, frame.sequence)
            assertEquals(-1.0, frame.master.peakDb.left, 0.001)
            assertEquals(
                "GET /api/v1/meters HTTP/1.1",
                server.takeRequest().requestLine,
            )
        }
    }

    @Test
    fun extendedControlPathsUseStableV1Contract() {
        MockWebServer().use { server ->
            server.start()
            repeat(8) {
                server.enqueue(
                    MockResponse.Builder()
                        .body(
                            when (it) {
                                0 -> """{"items":[]}"""
                                1 -> """{"selected":{"id":"out","name":"Output","state":"RUNNING","device_class":"sound","alsa_card":null,"selected":true,"available":true,"capabilities":{"sample_format":null,"sample_rate":48000,"channels":2,"channel_map":[],"alsa_device":null,"device_api":"alsa","device_bus":"usb"}},"applying":false,"applied":true}"""
                                2 -> """{"items":["music/track.flac"]}"""
                                3 -> """{"updating":true}"""
                                4 -> """{"playing":"music/track.flac"}"""
                                5 -> """{"items":["mix"]}"""
                                6 -> """{"playing_playlist":"mix"}"""
                                else -> """{"items":[{"position":1,"file":"music/track.flac","title":"Track","artist":"","album":""}]}"""
                            },
                        )
                        .build(),
                )
            }

            val api = OkHttpPlayerApiClient(client = OkHttpClient())
            val endpoint = DeviceEndpoint(server.hostName, server.port)

            runBlocking {
                api.audioOutputs(endpoint)
                api.selectAudioOutput(endpoint, "out")
                api.library(endpoint)
                api.refreshLibrary(endpoint)
                api.playLibraryPath(endpoint, "music/track.flac")
                api.playlists(endpoint)
                api.loadPlaylist(endpoint, "mix")
                api.queue(endpoint)
            }

            val lines = List(8) { server.takeRequest().requestLine }
            assertEquals("GET /api/v1/audio/outputs HTTP/1.1", lines[0])
            assertEquals("POST /api/v1/audio/outputs HTTP/1.1", lines[1])
            assertEquals("GET /api/v1/library HTTP/1.1", lines[2])
            assertEquals("POST /api/v1/library/update HTTP/1.1", lines[3])
            assertEquals("POST /api/v1/library/play HTTP/1.1", lines[4])
            assertEquals("GET /api/v1/playlists HTTP/1.1", lines[5])
            assertEquals("POST /api/v1/playlists/load HTTP/1.1", lines[6])
            assertEquals("GET /api/v1/queue HTTP/1.1", lines[7])
        }
    }

    @Test
    fun queueMutationsUsePositionContract() {
        MockWebServer().use { server ->
            server.start()
            repeat(3) {
                server.enqueue(
                    MockResponse.Builder()
                        .body(
                            when (it) {
                                0 -> """{"playing_position":2}"""
                                1 -> """{"removed_position":2}"""
                                else -> """{"cleared":true}"""
                            },
                        )
                        .build(),
                )
            }

            val api = OkHttpPlayerApiClient(client = OkHttpClient())
            val endpoint = DeviceEndpoint(server.hostName, server.port)

            runBlocking {
                api.playQueueItem(endpoint, 2)
                api.removeQueueItem(endpoint, 2)
                api.clearQueue(endpoint)
            }

            val play = server.takeRequest()
            val remove = server.takeRequest()
            val clear = server.takeRequest()

            assertEquals("POST /api/v1/queue/play HTTP/1.1", play.requestLine)
            assertEquals("""{"position":2}""", play.body?.utf8())
            assertEquals("POST /api/v1/queue/remove HTTP/1.1", remove.requestLine)
            assertEquals("""{"position":2}""", remove.body?.utf8())
            assertEquals("POST /api/v1/queue/clear HTTP/1.1", clear.requestLine)
            assertEquals("{}", clear.body?.utf8())
        }
    }

}
