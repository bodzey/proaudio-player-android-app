package com.bodzey.proaudioplayer.core.model

import java.util.UUID

@JvmInline
value class DeviceId private constructor(
    val value: String,
) {
    companion object {
        fun parse(rawValue: String): DeviceId {
            val normalized = rawValue.trim().lowercase()
            val parsed = runCatching { UUID.fromString(normalized) }
                .getOrElse { throw IllegalArgumentException("Invalid device ID", it) }

            require(parsed.toString() == normalized) {
                "Device ID must use canonical UUID representation"
            }

            return DeviceId(normalized)
        }
    }

    override fun toString(): String = value
}
