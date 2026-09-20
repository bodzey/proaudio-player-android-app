package com.bodzey.proaudioplayer.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkStreamValidatorTest {

    @Test
    fun acceptsPublicHttpAndHttpsStreams() {
        assertEquals(
            "https://radio.example/live.mp3",
            NetworkStreamValidator.normalize(" https://radio.example/live.mp3 "),
        )
        assertEquals(
            "http://radio.example:8000/stream",
            NetworkStreamValidator.normalize("http://radio.example:8000/stream"),
        )
    }

    @Test
    fun rejectsCredentialsAndUnsupportedSchemes() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkStreamValidator.normalize("ftp://radio.example/live")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkStreamValidator.normalize("https://user:pass@radio.example/live")
        }
    }

    @Test
    fun rejectsLocalHostnames() {
        listOf(
            "http://localhost/stream",
            "http://localhost.localdomain/stream",
            "http://speaker.local/stream",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                NetworkStreamValidator.normalize(value)
            }
        }
    }

    @Test
    fun rejectsPrivateAndServiceIpv4Addresses() {
        listOf(
            "http://0.0.0.0/stream",
            "http://10.0.0.1/stream",
            "http://127.0.0.1/stream",
            "http://169.254.1.1/stream",
            "http://172.16.0.1/stream",
            "http://192.168.1.1/stream",
            "http://224.0.0.1/stream",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                NetworkStreamValidator.normalize(value)
            }
        }
    }

    @Test
    fun rejectsUnsafeIpv6Addresses() {
        listOf(
            "http://[::]/stream",
            "http://[::1]/stream",
            "http://[fe80::1]/stream",
            "http://[fc00::1]/stream",
            "http://[ff02::1]/stream",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                NetworkStreamValidator.normalize(value)
            }
        }
    }
}
