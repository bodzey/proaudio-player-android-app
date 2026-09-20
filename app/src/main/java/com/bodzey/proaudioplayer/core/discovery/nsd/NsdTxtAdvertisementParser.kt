package com.bodzey.proaudioplayer.core.discovery.nsd

import com.bodzey.proaudioplayer.core.model.DeviceId
import java.nio.charset.StandardCharsets

internal data class ParsedNsdAdvertisement(
    val id: DeviceId,
    val displayName: String,
    val apiMajorVersion: Int,
)

internal object NsdTxtAdvertisementParser {
    fun parse(
        serviceName: String,
        attributes: Map<String, ByteArray>,
    ): ParsedNsdAdvertisement? {
        val rawDeviceId = attributes.decode(NsdDiscoveryContract.TXT_DEVICE_ID)
            ?: return null
        val deviceId = runCatching { DeviceId.parse(rawDeviceId) }
            .getOrNull()
            ?: return null

        val apiMajorVersion = attributes
            .decode(NsdDiscoveryContract.TXT_API_MAJOR_VERSION)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return null

        val displayName = attributes
            .decode(NsdDiscoveryContract.TXT_DISPLAY_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: serviceName.takeIf { it.isNotBlank() }
            ?: return null

        return ParsedNsdAdvertisement(
            id = deviceId,
            displayName = displayName,
            apiMajorVersion = apiMajorVersion,
        )
    }

    private fun Map<String, ByteArray>.decode(key: String): String? =
        get(key)
            ?.toString(StandardCharsets.UTF_8)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
