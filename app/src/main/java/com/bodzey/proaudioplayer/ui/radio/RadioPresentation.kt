package com.bodzey.proaudioplayer.ui.radio

import com.bodzey.proaudioplayer.core.api.PlayerStatus
import com.bodzey.proaudioplayer.core.api.RadioStation
import java.util.Locale

fun filterRadioStations(
    stations: List<RadioStation>,
    query: String,
): List<RadioStation> {
    val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+"))
        .filter(String::isNotEmpty)
    if (words.isEmpty()) return stations
    return stations.filter { station ->
        val text = (station.name + " " + station.tags.joinToString(" "))
            .lowercase(Locale.ROOT)
        words.all { word -> text.contains(word) }
    }
}

fun activeRadioStreamUrl(status: PlayerStatus): String? =
    if (status.player.backend == "mpd" &&
        status.player.source == "Інтернет-радіо"
    ) {
        status.mpd.streamUrl
    } else {
        null
    }

fun isSameRadioStream(
    left: String?,
    right: String,
): Boolean =
    left
        ?.trim()
        ?.trimEnd('/')
        ?.lowercase()
        ?.let { normalized -> normalized == right.trim().trimEnd('/').lowercase() }
        ?: false

fun radioStationGenre(tags: List<String>): String =
    tags
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .take(2)
        .joinToString(" / ")
        .ifBlank { "Internet Radio" }

fun radioStationQuality(
    codec: String?,
    bitrate: Int?,
): String =
    listOfNotNull(
        codec?.trim()?.takeIf(String::isNotEmpty)?.uppercase(),
        bitrate?.takeIf { it > 0 }?.let { value -> value.toString() + " kbps" },
    ).joinToString(" • ").ifBlank { "STREAM" }

fun radioStationShortName(name: String): String {
    val words = name
        .replace(Regex("\\([^)]*\\)"), " ")
        .split(Regex("\\s+"))
        .map(String::trim)
        .filter(String::isNotEmpty)

    if (words.isEmpty()) return "RADIO"
    if (words.size == 1) return words.first().take(8).uppercase()

    val initials = words
        .filterNot { word -> word.equals("fm", ignoreCase = true) }
        .mapNotNull { word -> word.firstOrNull()?.toString() }
        .joinToString("")
        .take(6)
        .uppercase()

    return initials.ifBlank { words.first().take(8).uppercase() }
}
