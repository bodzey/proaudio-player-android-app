package com.bodzey.proaudioplayer.core.api

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

object NetworkStreamValidator {

    fun normalize(value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) {
            "Вкажіть адресу аудіопотоку"
        }

        val uri = runCatching { URI(trimmed) }
            .getOrElse {
                throw IllegalArgumentException("Некоректна адреса потоку")
            }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") {
            "Підтримуються лише HTTP/HTTPS-потоки"
        }
        val host = uri.host
        require(!host.isNullOrBlank() && uri.userInfo == null) {
            "Потік має містити коректний host без облікових даних у URL"
        }

        val hostLower = host.lowercase()
        require(
            hostLower != "localhost" &&
                hostLower != "localhost.localdomain" &&
                !hostLower.endsWith(".local")
        ) {
            "Локальні адреси потоків заборонені"
        }

        require(!isUnsafeLiteralAddress(host)) {
            "Локальні та службові IP-адреси потоків заборонені"
        }

        return trimmed
    }

    private fun isUnsafeLiteralAddress(host: String): Boolean {
        parseIpv4(host)?.let { octets ->
            val first = octets[0]
            val second = octets[1]
            return first == 0 ||
                first == 10 ||
                first == 127 ||
                first in 224..239 ||
                (first == 169 && second == 254) ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168)
        }

        if (':' !in host) {
            return false
        }

        val address = runCatching { InetAddress.getByName(host) }.getOrNull()
            as? Inet6Address
            ?: return false
        val bytes = address.address
        val uniqueLocal = bytes.isNotEmpty() &&
            (bytes[0].toInt() and 0xFE) == 0xFC

        return address.isLoopbackAddress ||
            address.isAnyLocalAddress ||
            address.isMulticastAddress ||
            address.isLinkLocalAddress ||
            uniqueLocal
    }

    private fun parseIpv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val values = IntArray(4)
        for (index in parts.indices) {
            val part = parts[index]
            if (part.isEmpty() || part.length > 3) return null
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            values[index] = value
        }
        return values
    }
}
