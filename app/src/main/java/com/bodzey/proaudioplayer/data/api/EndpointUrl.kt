package com.bodzey.proaudioplayer.data.api

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun DeviceEndpoint.apiUrl(path: String): HttpUrl {
    require(path.startsWith('/')) { "API path must be absolute" }

    val scheme = when (transport) {
        DeviceEndpoint.Transport.HTTP -> "http"
        DeviceEndpoint.Transport.HTTPS -> "https"
    }
    val urlHost = if (':' in host) {
        val escapedZone = host.replace("%", "%25")
        "[" + escapedZone + "]"
    } else {
        host
    }

    return (scheme + "://" + urlHost + ":" + port + path).toHttpUrl()
}
