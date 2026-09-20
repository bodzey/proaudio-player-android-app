package com.bodzey.proaudioplayer.core.discovery.nsd

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NsdTxtAdvertisementParserTest {

    @Test
    fun validAdvertisementIsParsed() {
        val advertisement = NsdTxtAdvertisementParser.parse(
            serviceName = "ProAudio Player",
            attributes = attributes(
                "id" to "019c2c87-e95f-7b31-8bab-33e45ca6c2af",
                "api" to "1",
                "name" to "Server Room",
            ),
        )

        requireNotNull(advertisement)
        assertEquals(
            "019c2c87-e95f-7b31-8bab-33e45ca6c2af",
            advertisement.id.value,
        )
        assertEquals(1, advertisement.apiMajorVersion)
        assertEquals("Server Room", advertisement.displayName)
    }

    @Test
    fun serviceNameIsUsedWhenDisplayNameIsMissing() {
        val advertisement = NsdTxtAdvertisementParser.parse(
            serviceName = "ProAudio Player 7",
            attributes = attributes(
                "id" to "019c2c87-e95f-7b31-8bab-33e45ca6c2af",
                "api" to "1",
            ),
        )

        requireNotNull(advertisement)
        assertEquals("ProAudio Player 7", advertisement.displayName)
    }

    @Test
    fun invalidDeviceIdIsRejected() {
        val advertisement = NsdTxtAdvertisementParser.parse(
            serviceName = "ProAudio Player",
            attributes = attributes(
                "id" to "not-a-uuid",
                "api" to "1",
            ),
        )

        assertNull(advertisement)
    }

    @Test
    fun invalidApiVersionIsRejected() {
        val advertisement = NsdTxtAdvertisementParser.parse(
            serviceName = "ProAudio Player",
            attributes = attributes(
                "id" to "019c2c87-e95f-7b31-8bab-33e45ca6c2af",
                "api" to "0",
            ),
        )

        assertNull(advertisement)
    }

    private fun attributes(vararg values: Pair<String, String>): Map<String, ByteArray> =
        values.associate { (key, value) ->
            key to value.toByteArray(StandardCharsets.UTF_8)
        }
}
