package com.bodzey.proaudioplayer.ui.alerts

enum class SettingsPage {
    Overview,
    Announcements,
    Schedule,
    Provider,
    Media,
}

internal fun settingsPageForAudioField(field: String): SettingsPage =
    if (field.startsWith("minuteSilence")) SettingsPage.Schedule else SettingsPage.Announcements
