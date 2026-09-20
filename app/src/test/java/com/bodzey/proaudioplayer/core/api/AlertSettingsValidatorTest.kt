package com.bodzey.proaudioplayer.core.api

import org.junit.Assert.assertThrows
import org.junit.Test

class AlertSettingsValidatorTest {

    @Test
    fun providerAcceptsNativeBoundaryValues() {
        AlertSettingsValidator.validateProvider(
            provider(
                poll = 8.0,
                timeout = 0.1,
                backoff = 60.0,
                confirmations = 1,
            ),
        )
        AlertSettingsValidator.validateProvider(
            provider(
                poll = 3_600.0,
                timeout = 120.0,
                backoff = 86_400.0,
                confirmations = 100,
            ),
        )
    }

    @Test
    fun providerRejectsInvalidTemplateAndRanges() {
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateProvider(
                provider(endpoint = "https://api.example/status"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateProvider(
                provider(poll = 7.99),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateProvider(
                provider(timeout = 120.1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateProvider(
                provider(backoff = 59.0),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateProvider(
                provider(confirmations = 101),
            )
        }
    }

    @Test
    fun providerRejectsCredentialsAndUnknownLocationType() {
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateProvider(
                provider(endpoint = "https://user:pass@api.example/{uid}"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateProvider(
                provider(locationType = "village"),
            )
        }
    }

    @Test
    fun audioAcceptsNativeBoundaryValues() {
        AlertSettingsValidator.validateAudio(
            audio(
                duckDb = -60.0,
                fade = 0.0,
                volume = 0.0,
                catchUp = 0,
                repeat = 0,
            ),
        )
        AlertSettingsValidator.validateAudio(
            audio(
                duckDb = 0.0,
                fade = 60.0,
                volume = 100.0,
                catchUp = 86_400,
                repeat = 1_440,
            ),
        )
    }

    @Test
    fun audioRejectsInvalidTimeTimezoneAndRanges() {
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateAudio(
                audio(startTime = "24:00:00"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateAudio(
                audio(timezone = "Nowhere/Invalid"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateAudio(
                audio(duckDb = -60.1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateAudio(
                audio(volume = 100.1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateAudio(
                audio(fade = 60.1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateAudio(
                audio(catchUp = 86_401),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertSettingsValidator.validateAudio(
                audio(repeat = 1_441),
            )
        }
    }

    private fun provider(
        endpoint: String = "https://api.example/{uid}",
        locationType: String = "hromada",
        poll: Double = 8.0,
        timeout: Double = 7.0,
        backoff: Double = 60.0,
        confirmations: Int = 2,
    ) = AlertProviderUpdate(
        endpoint = endpoint,
        locationUid = 1,
        locationType = locationType,
        pollIntervalSeconds = poll,
        requestTimeoutSeconds = timeout,
        rateLimitBackoffSeconds = backoff,
        clearConfirmations = confirmations,
    )

    private fun audio(
        duckDb: Double = -12.0,
        fade: Double = 1.0,
        volume: Double = 89.0,
        catchUp: Long = 120,
        repeat: Long = 0,
        startTime: String = "08:59:50",
        timezone: String = "Europe/Kyiv",
    ) = AlertAudioUpdate(
        airRaidAlertsEnabled = true,
        duckDb = duckDb,
        duckFadeSeconds = fade,
        restoreFadeSeconds = fade,
        alertVolumePercent = volume,
        defaultRestoreVolumePercent = volume,
        minuteSilenceVolumePercent = volume,
        minuteSilenceEnabled = true,
        minuteSilenceStartTime = startTime,
        minuteSilenceTimezone = timezone,
        minuteSilenceCatchUpSeconds = catchUp,
        minuteSilenceMusicFadeSeconds = fade,
        alertRepeatIntervalMinutes = repeat,
        duckOnlyDuringAnnouncement = false,
    )
}
