package com.bodzey.proaudioplayer.data.api

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointUrlTest {

    @Test
    fun ipv4EndpointBuildsCanonicalUrl() {
        val endpoint = DeviceEndpoint(
            host = "192.168.88.50",
            port = 5371,
        )

        assertEquals(
            "http://192.168.88.50:5371/api/v1/health",
            endpoint.apiUrl("/api/v1/health").toString(),
        )
    }

    @Test
    fun ipv6EndpointBuildsCanonicalUrl() {
        val endpoint = DeviceEndpoint(
            host = "2001:db8::10",
            port = 5371,
        )

        assertEquals(
            "http://[2001:db8::10]:5371/api/v1/health",
            endpoint.apiUrl("/api/v1/health").toString(),
        )
    }
}
