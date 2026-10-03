package com.bodzey.proaudioplayer.ui.alerts

import com.bodzey.proaudioplayer.core.api.AlertAudioSettings
import com.bodzey.proaudioplayer.core.api.AlertAudioUpdate
import com.bodzey.proaudioplayer.core.api.AlertProviderSettings
import com.bodzey.proaudioplayer.core.api.AlertProviderUpdate
import com.bodzey.proaudioplayer.core.api.AlertSettingException
import com.bodzey.proaudioplayer.core.api.AlertSettingsValidator

internal fun AlertProviderSettings.toForm(): AlertProviderForm =
    AlertProviderForm(
        endpoint = endpoint,
        locationUid = locationUid.toString(),
        locationType = locationType,
        pollIntervalSeconds = pollIntervalSeconds.cleanNumber(),
        requestTimeoutSeconds = requestTimeoutSeconds.cleanNumber(),
        rateLimitBackoffSeconds = rateLimitBackoffSeconds.cleanNumber(),
        clearConfirmations = clearConfirmations.toString(),
    )

internal fun AlertAudioSettings.toForm(): AlertAudioForm =
    AlertAudioForm(
        airRaidAlertsEnabled = airRaidAlertsEnabled,
        minuteSilenceEnabled = minuteSilenceEnabled,
        duckDb = duckDb.cleanNumber(),
        duckFadeSeconds = duckFadeSeconds.cleanNumber(),
        restoreFadeSeconds = restoreFadeSeconds.cleanNumber(),
        alertVolumePercent = alertVolumePercent.cleanNumber(),
        defaultRestoreVolumePercent = defaultRestoreVolumePercent.cleanNumber(),
        minuteSilenceVolumePercent = minuteSilenceVolumePercent.cleanNumber(),
        minuteSilenceStartTime = minuteSilenceStartTime,
        minuteSilenceTimezone = minuteSilenceTimezone,
        minuteSilenceCatchUpSeconds = minuteSilenceCatchUpSeconds.toString(),
        minuteSilenceMusicFadeSeconds = minuteSilenceMusicFadeSeconds.cleanNumber(),
        alertRepeatIntervalMinutes = alertRepeatIntervalMinutes.toString(),
        duckOnlyDuringAnnouncement = duckOnlyDuringAnnouncement,
    )

internal fun AlertProviderForm.toUpdate(): AlertProviderUpdate {
    val update = AlertProviderUpdate(
        endpoint = endpoint.trim(),
        locationUid = locationUid.requiredLong(
            "UID локації",
            "locationUid",
        ),
        locationType = locationType.trim(),
        pollIntervalSeconds = pollIntervalSeconds.requiredDouble(
            "Інтервал опитування",
            "pollIntervalSeconds",
        ),
        requestTimeoutSeconds = requestTimeoutSeconds.requiredDouble(
            "Очікування відповіді",
            "requestTimeoutSeconds",
        ),
        rateLimitBackoffSeconds =
            rateLimitBackoffSeconds.requiredDouble(
            "Пауза після HTTP 429",
            "rateLimitBackoffSeconds",
        ),
        clearConfirmations = clearConfirmations.requiredInt(
            "Підтвердження відбою",
            "clearConfirmations",
        ),
        token = token.trim().takeIf { it.isNotEmpty() },
    )
    AlertSettingsValidator.validateProvider(update)
    return update
}

internal fun AlertAudioForm.toUpdate(): AlertAudioUpdate {
    val update = AlertAudioUpdate(
        airRaidAlertsEnabled = airRaidAlertsEnabled,
        duckDb = duckDb.requiredDouble(
            "Стишення музики",
            "duckDb",
        ),
        duckFadeSeconds = duckFadeSeconds.requiredDouble(
            "Плавне стишення",
            "duckFadeSeconds",
        ),
        restoreFadeSeconds = restoreFadeSeconds.requiredDouble(
            "Час відновлення",
            "restoreFadeSeconds",
        ),
        alertVolumePercent = alertVolumePercent.requiredDouble(
            "Гучність ALERT",
            "alertVolumePercent",
        ),
        defaultRestoreVolumePercent =
            defaultRestoreVolumePercent.requiredDouble(
            "Рівень відновлення",
            "defaultRestoreVolumePercent",
        ),
        minuteSilenceVolumePercent =
            minuteSilenceVolumePercent.requiredDouble(
            "Гучність хвилини мовчання",
            "minuteSilenceVolumePercent",
        ),
        minuteSilenceEnabled = minuteSilenceEnabled,
        minuteSilenceStartTime = minuteSilenceStartTime.trim(),
        minuteSilenceTimezone = minuteSilenceTimezone.trim(),
        minuteSilenceCatchUpSeconds =
            minuteSilenceCatchUpSeconds.requiredLong(
            "Допустиме запізнення",
            "minuteSilenceCatchUpSeconds",
        ),
        minuteSilenceMusicFadeSeconds =
            minuteSilenceMusicFadeSeconds.requiredDouble(
            "Стишення перед хвилиною мовчання",
            "minuteSilenceMusicFadeSeconds",
        ),
        alertRepeatIntervalMinutes =
            alertRepeatIntervalMinutes.requiredLong(
            "Повторення тривоги",
            "alertRepeatIntervalMinutes",
        ),
        duckOnlyDuringAnnouncement = duckOnlyDuringAnnouncement,
    )
    AlertSettingsValidator.validateAudio(update)
    return update
}

private fun String.requiredDouble(label: String, field: String): Double =
    trim().replace(',', '.').toDoubleOrNull()
        ?: throw AlertSettingException(field, "$label має містити число")

private fun String.requiredLong(label: String, field: String): Long =
    trim().toLongOrNull()
        ?: throw AlertSettingException(field, "$label має містити ціле число")

private fun String.requiredInt(label: String, field: String): Int =
    trim().toIntOrNull()
        ?: throw AlertSettingException(field, "$label має містити ціле число")

private fun Double.cleanNumber(): String =
    if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        toString()
    }
