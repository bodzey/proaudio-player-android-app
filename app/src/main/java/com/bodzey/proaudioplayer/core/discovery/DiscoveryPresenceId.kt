package com.bodzey.proaudioplayer.core.discovery

@JvmInline
value class DiscoveryPresenceId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Discovery presence ID must not be blank" }
    }

    override fun toString(): String = value
}
