package com.bodzey.proaudioplayer.ui.alerts

import com.bodzey.proaudioplayer.core.api.AlertSettingException
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsValuesTest {
    @Test
    fun nativeTimeSelectionPreservesSecondsInTheApiPayload() {
        assertEquals("10:25:50", selectedSettingsTime("08:59:50", 10, 25))
        assertEquals("10:25:00", selectedSettingsTime("08:59:50", 10, 25, 0))
        assertEquals("00:05:00", selectedSettingsTime("09:00:00", 0, 5))
        assertEquals("23:59:00", selectedSettingsTime("invalid", 23, 59))
        assertEquals("08:59:50", settingsTimeLabel("08:59:50"))
        assertEquals("09:00", settingsTimeLabel("09:00:00"))
    }

    @Test
    fun preciseNumberEntryAcceptsTheDecimalCommaAndRejectsNonFiniteValues() {
        assertEquals(-12.5, settingsNumber(" -12,5 ")!!, 0.0)
        assertNull(settingsNumber("NaN"))
        assertNull(settingsNumber("Infinity"))
        assertNull(settingsNumber("1,2,3"))
        assertEquals(-12.5, form().copy(duckDb = "-12,5").toUpdate().duckDb, 0.0)
        assertEquals(1.5, form().copy(duckFadeSeconds = "1,5").toUpdate().duckFadeSeconds, 0.0)
    }

    @Test
    fun sliderValuesRemainValidForTheApiWithADecimalCommaLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("uk-UA"))
            assertEquals("-12.5", settingsSliderValue(-12.5f))
            assertEquals("100", settingsSliderValue(100f))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun invalidFieldsIdentifyTheCategoryToRevealBeforeSaving() {
        val parseError = assertThrows(AlertSettingException::class.java) {
            form().copy(minuteSilenceCatchUpSeconds = "oops").toUpdate()
        }
        assertEquals("minuteSilenceCatchUpSeconds", parseError.field)
        assertEquals(SettingsPage.Schedule, settingsPageForAudioField(parseError.field))
        val rangeError = assertThrows(AlertSettingException::class.java) {
            form().copy(defaultRestoreVolumePercent = "101").toUpdate()
        }
        assertEquals("defaultRestoreVolumePercent", rangeError.field)
        assertEquals(SettingsPage.Announcements, settingsPageForAudioField(rangeError.field))
        val timezoneError = assertThrows(AlertSettingException::class.java) {
            form().copy(minuteSilenceTimezone = "Nowhere/Invalid").toUpdate()
        }
        assertEquals(SettingsPage.Schedule, settingsPageForAudioField(timezoneError.field))
    }

    private fun form() = AlertAudioForm(
        airRaidAlertsEnabled = true,
        minuteSilenceEnabled = true,
        duckDb = "-12",
        duckFadeSeconds = "1",
        restoreFadeSeconds = "1",
        alertVolumePercent = "90",
        defaultRestoreVolumePercent = "50",
        minuteSilenceVolumePercent = "80",
        minuteSilenceStartTime = "08:59:50",
        minuteSilenceTimezone = "Europe/Kyiv",
        minuteSilenceCatchUpSeconds = "120",
        minuteSilenceMusicFadeSeconds = "1",
        alertRepeatIntervalMinutes = "0",
        duckOnlyDuringAnnouncement = false,
    )
}
