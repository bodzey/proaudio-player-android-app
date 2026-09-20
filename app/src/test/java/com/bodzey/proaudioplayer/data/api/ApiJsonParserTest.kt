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
              "audio_levels":{
                "master":{
                  "volume":42.5,
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
        assertEquals(89.1, status.music.volumePercent, 0.001)
        assertFalse(status.music.muted)
        assertEquals("Spotify Connect", status.player.source)
        assertEquals("Track", status.player.title)
        assertTrue(status.player.controls.pause)
        assertTrue(status.player.controls.previous)
    }
}
