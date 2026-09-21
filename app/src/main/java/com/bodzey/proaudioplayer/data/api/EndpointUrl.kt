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

internal fun DeviceEndpoint.resolveHttpUrl(value: String?): String? {
    val candidate = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val resolved = apiUrl("/").resolve(candidate) ?: return null
    return resolved
        .takeIf { url -> url.scheme == "http" || url.scheme == "https" }
        ?.toString()
}
