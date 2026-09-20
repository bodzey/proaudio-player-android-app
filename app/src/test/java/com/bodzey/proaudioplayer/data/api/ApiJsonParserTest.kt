package com.bodzey.proaudioplayer.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiJsonParserTest {
    private val parser = ApiJsonParser()

    @Test
    fun healthParsesStringApiVersion() {
        val health = parser.health(
            """{"status":"ok","api_version":"1","future_field":true}""",
        )

        assertEquals("ok", health.status)
        assertEquals(1, health.apiMajorVersion)
    }

    @Test
    fun capabilitiesIgnoreUnknownFutureFields() {
        val capabilities = parser.capabilities(
            """
            {
              "api_version":"1",
              "events":"sse",
              "features":["status","player_control"],
              "future":{"enabled":true}
            }
            """.trimIndent(),
        )

        assertEquals(1, capabilities.apiMajorVersion)
        assertEquals("sse", capabilities.eventTransport)
        assertEquals(setOf("status", "player_control"), capabilities.features)
    }

    @Test
    fun statusParsesTransportNeutralPlayer() {
        val status = parser.status(
            """
            {
              "name":"ProAudio Player",
              "volume":89.1,
              "muted":false,
              "priority":{
                "mode":"alert",
                "active":true,
                "blocking":true,
                "duck_only_during_announcement":false,
                "minute_silence_active":false,
                "matched_uids":[1133,1144],
                "last_success_at":"2026-09-20T10:00:00Z",
                "last_change_at":"2026-09-20T09:59:00Z",
                "last_error":null
              },
              "mpd":{
                "is_stream":true,
                "stream_url":"https://radio.example/live"
              },
              "audio_levels":{
                "master":{
                  "volume":42.5,
                  "db":-17.25,
                  "muted":true
                },
                "music_bus":89.1
              },
              "player":{
                "source":"Spotify Connect",
                "backend":"spotify-mpris",
                "state":"playing",
                "title":"Track",
                "artist":"Artist",
                "album":"Album",
                "position_seconds":12.5,
                "duration_seconds":180.0,
                "progress":7,
                "controls":{
                  "play":false,
                  "pause":true,
                  "stop":true,
                  "next":true,
                  "prev":true
                }
              },
              "future_field":"ignored"
            }
            """.trimIndent(),
        )

        assertEquals("ProAudio Player", status.name)
        assertEquals(42.5, status.master.volumePercent, 0.001)
        assertTrue(status.master.muted)
        assertEquals(-17.25, status.master.db ?: Double.NaN, 0.001)
        assertEquals(89.1, status.music.volumePercent, 0.001)
        assertFalse(status.music.muted)
        assertEquals("alert", status.priority.mode)
        assertTrue(status.priority.active)
        assertTrue(status.priority.blocking)
        assertFalse(status.priority.duckOnlyDuringAnnouncement)
        assertFalse(status.priority.minuteSilenceActive)
        assertEquals(listOf(1133L, 1144L), status.priority.matchedUids)
        assertEquals("2026-09-20T10:00:00Z", status.priority.lastSuccessAt)
        assertTrue(status.mpd.isStream)
        assertEquals("https://radio.example/live", status.mpd.streamUrl)
        assertEquals("Spotify Connect", status.player.source)
        assertEquals("Track", status.player.title)
        assertTrue(status.player.controls.pause)
        assertTrue(status.player.controls.previous)
    }
    @Test
    fun radioDirectoryParsesNormalizedStations() {
        val stations = parser.radioStations(
            """
            {
              "source":"radio-browser",
              "items":[
                {
                  "id":"station-1",
                  "name":"Test FM",
                  "url":"https://radio.example/live",
                  "homepage":"https://radio.example/",
                  "favicon":"https://radio.example/logo.png",
                  "tags":["pop","ukrainian"],
                  "codec":"MP3",
                  "bitrate":192,
                  "votes":42
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, stations.size)
        assertEquals("station-1", stations.single().id)
        assertEquals("Test FM", stations.single().name)
        assertEquals(listOf("pop", "ukrainian"), stations.single().tags)
        assertEquals("MP3", stations.single().codec)
        assertEquals(192, stations.single().bitrate)
        assertEquals(42L, stations.single().votes)
    }

    @Test
    fun alertProviderSettingsParseRuntimeConfiguration() {
        val settings = parser.alertProviderSettings(
            """
            {
              "endpoint":"https://api.alerts.in.ua/v1/iot/active_air_raid_alerts/{uid}.json",
              "location_uid":1133,
              "location_type":"hromada",
              "poll_interval_seconds":8.0,
              "request_timeout_seconds":7.0,
              "rate_limit_backoff_seconds":60.0,
              "clear_confirmations":2,
              "token_configured":true
            }
            """.trimIndent(),
        )

        assertEquals(1133L, settings.locationUid)
        assertEquals("hromada", settings.locationType)
        assertTrue(settings.tokenConfigured)
        assertEquals(2, settings.clearConfirmations)
    }

    @Test
    fun alertAudioSettingsParsePriorityAudioPolicy() {
        val settings = parser.alertAudioSettings(
            """
            {
              "air_raid_alerts_enabled":true,
              "notifications_enabled":true,
              "duck_db":-12.0,
              "duck_fade_seconds":1.0,
              "restore_fade_seconds":3.0,
              "alert_volume_percent":89.1,
              "default_restore_volume_percent":89.1,
              "minute_silence_volume_percent":100.0,
              "minute_silence_enabled":true,
              "minute_silence_start_time":"08:59:50",
              "minute_silence_timezone":"Europe/Kyiv",
              "minute_silence_catch_up_seconds":120,
              "minute_silence_music_fade_seconds":1.0,
              "alert_repeat_interval_minutes":0,
              "duck_only_during_announcement":false,
              "sample_rate_mode":"fixed",
              "sample_rate":48000,
              "allowed_sample_rates":[44100,48000]
            }
            """.trimIndent(),
        )

        assertTrue(settings.airRaidAlertsEnabled)
        assertEquals(-12.0, settings.duckDb, 0.001)
        assertEquals("Europe/Kyiv", settings.minuteSilenceTimezone)
        assertEquals(listOf(44100, 48000), settings.allowedSampleRates)
    }

    @Test
    fun alertMediaParsesConfiguredFiles() {
        val media = parser.alertMedia(
            """
            {
              "items":[
                {
                  "kind":"alarm_start",
                  "label":"Повітряна тривога",
                  "file_name":"alarm_start.mp3",
                  "configured":true,
                  "size_bytes":123456,
                  "modified_unix_seconds":1770000000,
                  "max_size_bytes":16777216,
                  "content_type":"audio/mpeg"
                }
              ],
              "accepted_content_types":["audio/mpeg","audio/mp3"],
              "max_size_bytes":16777216
            }
            """.trimIndent(),
        )

        assertEquals(1, media.items.size)
        assertEquals("alarm_start", media.items.single().kind)
        assertEquals(123456L, media.items.single().sizeBytes)
        assertEquals(setOf("audio/mpeg", "audio/mp3"), media.acceptedContentTypes)
        assertEquals(16777216L, media.maxSizeBytes)
    }


    @Test
    fun alertProviderTestParsesActiveState() {
        val result = parser.alertProviderTest(
            """{"ok":true,"active":true,"state":"active","location_uid":1133}""",
        )

        assertTrue(result.ok)
        assertTrue(result.active)
        assertEquals("active", result.state)
        assertEquals(1133L, result.locationUid)
    }

    @Test
    fun alertMediaFileParsesMutationResponse() {
        val media = parser.alertMediaFile(
            """
            {
              "kind":"minute_silence",
              "label":"Хвилина мовчання",
              "file_name":"minute_silence.mp3",
              "configured":true,
              "size_bytes":987654,
              "modified_unix_seconds":1770000001,
              "max_size_bytes":16777216,
              "content_type":"audio/mpeg"
            }
            """.trimIndent(),
        )

        assertEquals("minute_silence", media.kind)
        assertEquals("minute_silence.mp3", media.fileName)
        assertTrue(media.configured)
        assertEquals(987654L, media.sizeBytes)
        assertEquals(16777216L, media.maxSizeBytes)
    }


    @Test
    fun meterFrameParsesStereoPeakRmsAndClip() {
        val frame = parser.meterFrame(
            """
            {
              "sequence":42,
              "sample_rate":48000,
              "interval_ms":20,
              "master":{
                "peak":[-3.0,-4.0],
                "rms":[-9.0,-10.0],
                "clip":[false,true],
                "available":true
              },
              "music":{
                "peak":[-12.0,-13.0],
                "rms":[-18.0,-19.0],
                "clip":[false,false],
                "available":true
              },
              "alert":{
                "peak":[-60.0,-60.0],
                "rms":[-60.0,-60.0],
                "clip":[false,false],
                "available":false
              }
            }
            """.trimIndent(),
        )

        assertEquals(42L, frame.sequence)
        assertEquals(48000, frame.sampleRate)
        assertEquals(20L, frame.intervalMillis)
        assertEquals(-3.0, frame.master.peakDb.left, 0.001)
        assertEquals(-10.0, frame.master.rmsDb.right, 0.001)
        assertFalse(frame.master.clipLeft)
        assertTrue(frame.master.clipRight)
        assertFalse(frame.alert.available)
    }

}
