package com.bodzey.proaudioplayer.core.model

data class DeviceEndpoint(
    val host: String,
    val port: Int,
    val transport: Transport = Transport.HTTP,
) {
    init {
        require(host.isNotBlank()) { "Endpoint host must not be blank" }
        require(port in 1..65535) { "Endpoint port must be between 1 and 65535" }
    }

    enum class Transport {
        HTTP,
        HTTPS,
    }
}
