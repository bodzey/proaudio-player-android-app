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
                "active":true,
                "blocking":true
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
        assertTrue(status.priority.active)
        assertTrue(status.priority.blocking)
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

}
