package com.bodzey.proaudioplayer.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

@Composable
internal fun AudioSettingsCard(
    page: SettingsPage,
    form: AlertAudioForm,
    message: AlertsMessage?,
    busyAction: AlertsBusyAction?,
    onFormChange: (AlertAudioForm) -> Unit,
) {
    val colors = LocalProAudioColors.current
    val busy = busyAction != null
    var advanced by rememberSaveable(page) { mutableStateOf(false) }

    LaunchedEffect(message, page) {
        if (message?.isError == true) advanced = true
    }

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page == SettingsPage.Announcements) {
                Text(
                    stringResource(R.string.settings_announcements_description),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                AlertSwitchRow(
                    checked = form.airRaidAlertsEnabled,
                    onCheckedChange = { onFormChange(form.copy(airRaidAlertsEnabled = it)) },
                    title = stringResource(R.string.alerts_enable_air_raid),
                    description = stringResource(R.string.alerts_enable_air_raid_hint),
                    enabled = !busy,
                )
                SettingsGroup(stringResource(R.string.settings_announcement_sound)) {
                    SettingsSlider(
                        value = form.alertVolumePercent,
                        onValueChange = { onFormChange(form.copy(alertVolumePercent = it)) },
                        label = stringResource(R.string.settings_announcement_volume),
                        range = 0f..100f,
                        unit = "%",
                        enabled = !busy,
                    )
                    AlertTextField(
                        isError = message?.field == "alertRepeatIntervalMinutes",
                        value = form.alertRepeatIntervalMinutes,
                        onValueChange = { onFormChange(form.copy(alertRepeatIntervalMinutes = it)) },
                        label = stringResource(R.string.alerts_repeat_interval),
                        enabled = !busy,
                        keyboardType = KeyboardType.Number,
                        supportingText = stringResource(R.string.settings_repeat_hint),
                    )
                }
                SettingsGroup(stringResource(R.string.settings_music_behavior)) {
                    SettingsSlider(
                        value = form.duckDb,
                        onValueChange = { onFormChange(form.copy(duckDb = it)) },
                        label = stringResource(R.string.alerts_duck_db),
                        range = -60f..0f,
                        unit = "dB",
                        enabled = !busy,
                        supportingText = stringResource(R.string.settings_duck_hint),
                    )
                    AlertSwitchRow(
                        checked = form.duckOnlyDuringAnnouncement,
                        onCheckedChange = { onFormChange(form.copy(duckOnlyDuringAnnouncement = it)) },
                        title = stringResource(R.string.alerts_talkover),
                        description = stringResource(R.string.alerts_talkover_hint),
                        enabled = !busy,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.settings_schedule_description),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                AlertSwitchRow(
                    checked = form.minuteSilenceEnabled,
                    onCheckedChange = { onFormChange(form.copy(minuteSilenceEnabled = it)) },
                    title = stringResource(R.string.alerts_enable_minute),
                    description = stringResource(R.string.alerts_enable_minute_hint),
                    enabled = !busy,
                )
                SettingsGroup(stringResource(R.string.settings_schedule)) {
                    SettingsTime(
                        value = form.minuteSilenceStartTime,
                        onValueChange = { onFormChange(form.copy(minuteSilenceStartTime = it)) },
                        enabled = !busy,
                    )
                    AlertTextField(
                        isError = message?.field == "minuteSilenceTimezone",
                        value = form.minuteSilenceTimezone,
                        onValueChange = { onFormChange(form.copy(minuteSilenceTimezone = it)) },
                        label = stringResource(R.string.alerts_timezone),
                        enabled = !busy,
                        supportingText = stringResource(R.string.settings_timezone_hint),
                    )
                    SettingsSlider(
                        value = form.minuteSilenceVolumePercent,
                        onValueChange = { onFormChange(form.copy(minuteSilenceVolumePercent = it)) },
                        label = stringResource(R.string.settings_minute_volume),
                        range = 0f..100f,
                        unit = "%",
                        enabled = !busy,
                        supportingText = stringResource(R.string.settings_minute_volume_hint),
                    )
                }
            }

            TextButton(onClick = { advanced = !advanced }, enabled = !busy) {
                Text(stringResource(if (advanced) R.string.alerts_advanced_hide else R.string.alerts_advanced_show))
            }
            if (advanced) {
                SettingsGroup(stringResource(R.string.settings_advanced)) {
                    if (page == SettingsPage.Announcements) {
                        AlertTextField(
                            isError = message?.field == "duckFadeSeconds",
                            value = form.duckFadeSeconds,
                            onValueChange = { onFormChange(form.copy(duckFadeSeconds = it)) },
                            label = stringResource(R.string.alerts_duck_fade),
                            enabled = !busy,
                            keyboardType = KeyboardType.Decimal,
                        )
                        AlertTextField(
                            isError = message?.field == "restoreFadeSeconds",
                            value = form.restoreFadeSeconds,
                            onValueChange = { onFormChange(form.copy(restoreFadeSeconds = it)) },
                            label = stringResource(R.string.alerts_restore_fade),
                            enabled = !busy,
                            keyboardType = KeyboardType.Decimal,
                        )
                        SettingsSlider(
                            value = form.defaultRestoreVolumePercent,
                            onValueChange = { onFormChange(form.copy(defaultRestoreVolumePercent = it)) },
                            label = stringResource(R.string.settings_fallback_volume),
                            range = 0f..100f,
                            unit = "%",
                            enabled = !busy,
                            supportingText = stringResource(R.string.settings_fallback_volume_hint),
                        )
                    } else {
                        AlertTextField(
                            isError = message?.field == "minuteSilenceCatchUpSeconds",
                            value = form.minuteSilenceCatchUpSeconds,
                            onValueChange = { onFormChange(form.copy(minuteSilenceCatchUpSeconds = it)) },
                            label = stringResource(R.string.alerts_catch_up),
                            enabled = !busy,
                            keyboardType = KeyboardType.Number,
                        )
                        AlertTextField(
                            isError = message?.field == "minuteSilenceMusicFadeSeconds",
                            value = form.minuteSilenceMusicFadeSeconds,
                            onValueChange = { onFormChange(form.copy(minuteSilenceMusicFadeSeconds = it)) },
                            label = stringResource(R.string.alerts_minute_fade),
                            enabled = !busy,
                            keyboardType = KeyboardType.Decimal,
                        )
                    }
                }
            }
        }
    }
}
