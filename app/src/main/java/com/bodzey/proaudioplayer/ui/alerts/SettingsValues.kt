package com.bodzey.proaudioplayer.ui.alerts

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun settingsNumber(value: String): Double? =
    value.trim().replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)

internal fun settingsTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value) }.getOrNull()

internal fun settingsTimeLabel(value: String): String {
    val time = settingsTime(value) ?: return value
    val pattern = if (time.second == 0) "HH:mm" else "HH:mm:ss"
    return time.format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT))
}

internal fun selectedSettingsTime(
    previous: String,
    hour: Int,
    minute: Int,
    second: Int = settingsTime(previous)?.second ?: 0,
): String =
    LocalTime.of(hour, minute, second)
        .format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT))

internal fun settingsSliderValue(value: Float): String =
    String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
