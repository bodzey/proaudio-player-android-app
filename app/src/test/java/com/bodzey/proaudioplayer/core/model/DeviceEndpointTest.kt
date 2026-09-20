package com.bodzey.proaudioplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceEndpointTest {
    @Test
    fun validEndpointIsAccepted() {
        val endpoint = DeviceEndpoint(
            host = "192.168.88.50",
            port = 8080,
        )

        assertEquals("192.168.88.50", endpoint.host)
        assertEquals(8080, endpoint.port)
        assertEquals(DeviceEndpoint.Transport.HTTP, endpoint.transport)
    }

    @Test
    fun ipv6AddressIsAcceptedAsHost() {
        val endpoint = DeviceEndpoint(
            host = "fe80::1234",
            port = 8080,
        )

        assertEquals("fe80::1234", endpoint.host)
    }

    @Test
    fun blankHostIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceEndpoint(host = " ", port = 8080)
        }
    }

    @Test
    fun invalidPortIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceEndpoint(host = "player.local", port = 0)
        }
    }
}
