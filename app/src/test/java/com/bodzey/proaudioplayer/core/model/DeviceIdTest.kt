package com.bodzey.proaudioplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceIdTest {
    @Test
    fun canonicalUuidIsAccepted() {
        val id = DeviceId.parse("019c2c87-e95f-7b31-8bab-33e45ca6c2af")

        assertEquals(
            "019c2c87-e95f-7b31-8bab-33e45ca6c2af",
            id.value,
        )
    }

    @Test
    fun surroundingWhitespaceAndUppercaseAreNormalized() {
        val id = DeviceId.parse("  019C2C87-E95F-7B31-8BAB-33E45CA6C2AF  ")

        assertEquals(
            "019c2c87-e95f-7b31-8bab-33e45ca6c2af",
            id.value,
        )
    }

    @Test
    fun nonCanonicalUuidIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceId.parse("1-1-1-1-1")
        }
    }

    @Test
    fun arbitraryTextIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceId.parse("player-one")
        }
    }
}
