package com.bodzey.proaudioplayer.core.api

import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.ZoneId

object AlertSettingsValidator {
    private val timePattern =
        Regex("""^(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d$""")

    fun validateProvider(update: AlertProviderUpdate) {
        require(update.locationUid in 1..4_294_967_295L) {
            "UID локації має бути в межах 1..4294967295"
        }
        require(update.locationType in LOCATION_TYPES) {
            "Невідомий тип локації"
        }
        require(update.endpoint.contains("{uid}")) {
            "Адреса API має містити шаблон {uid}"
        }
        require(update.endpoint.utf8Size() <= MAX_ENDPOINT_BYTES) {
            "Адреса API перевищує 2048 байтів"
        }
        update.token?.let { token ->
            require(token.utf8Size() <= MAX_TOKEN_BYTES) {
                "API-токен перевищує 4096 байтів"
            }
        }
        require(isValidHttpEndpoint(update.endpoint)) {
            "Адреса API має бути коректною HTTP(S)-адресою"
        }
        require(update.pollIntervalSeconds.inFiniteRange(8.0, 3_600.0)) {
            "Інтервал опитування має бути в межах 8..3600 секунд"
        }
        require(update.requestTimeoutSeconds.inFiniteRange(0.1, 120.0)) {
            "Очікування відповіді має бути в межах 0.1..120 секунд"
        }
        require(update.rateLimitBackoffSeconds.inFiniteRange(60.0, 86_400.0)) {
            "Пауза після HTTP 429 має бути в межах 60..86400 секунд"
        }
        require(update.clearConfirmations in 1..100) {
            "Підтвердження відбою мають бути в межах 1..100"
        }
    }

    fun validateAudio(update: AlertAudioUpdate) {
        require(update.duckDb.inFiniteRange(-60.0, 0.0)) {
            "Стишення музики має бути в межах -60..0 dB"
        }
        validatePercent(
            value = update.alertVolumePercent,
            label = "Гучність ALERT",
        )
        validatePercent(
            value = update.defaultRestoreVolumePercent,
            label = "Рівень відновлення",
        )
        validatePercent(
            value = update.minuteSilenceVolumePercent,
            label = "Гучність хвилини мовчання",
        )
        validateDuration(
            value = update.duckFadeSeconds,
            label = "Плавне стишення",
        )
        validateDuration(
            value = update.restoreFadeSeconds,
            label = "Час відновлення",
        )
        validateDuration(
            value = update.minuteSilenceMusicFadeSeconds,
            label = "Стишення перед хвилиною мовчання",
        )
        require(timePattern.matches(update.minuteSilenceStartTime)) {
            "Час початку має формат HH:MM:SS"
        }
        require(runCatching { ZoneId.of(update.minuteSilenceTimezone) }.isSuccess) {
            "Невідомий часовий пояс"
        }
        require(update.minuteSilenceCatchUpSeconds in 0..86_400) {
            "Допустиме запізнення має бути в межах 0..86400 секунд"
        }
        require(update.alertRepeatIntervalMinutes in 0..1_440) {
            "Повторення тривоги має бути в межах 0..1440 хвилин"
        }
    }

    private fun validatePercent(
        value: Double,
        label: String,
    ) {
        require(value.inFiniteRange(0.0, 100.0)) {
            "$label має бути в межах 0..100%"
        }
    }

    private fun validateDuration(
        value: Double,
        label: String,
    ) {
        require(value.inFiniteRange(0.0, 60.0)) {
            "$label має бути в межах 0..60 секунд"
        }
    }

    private fun isValidHttpEndpoint(endpoint: String): Boolean =
        runCatching {
            val uri = URI(endpoint.replace("{uid}", "1"))
            uri.scheme?.lowercase() in setOf("http", "https") &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        }.getOrDefault(false)

    private fun Double.inFiniteRange(
        minimum: Double,
        maximum: Double,
    ): Boolean =
        isFinite() && this in minimum..maximum

    private fun String.utf8Size(): Int =
        toByteArray(StandardCharsets.UTF_8).size

    private val LOCATION_TYPES =
        setOf("hromada", "raion", "oblast", "standalone", "city")

    private const val MAX_ENDPOINT_BYTES = 2_048
    private const val MAX_TOKEN_BYTES = 4_096
}
