package com.bodzey.proaudioplayer.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.api.AlertAudioSettings
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.SectionLabel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

@Composable
internal fun AudioSettingsCard(
    settings: AlertAudioSettings,
    form: AlertAudioForm,
    dirty: Boolean,
    message: AlertsMessage?,
    busyAction: AlertsBusyAction?,
    onFormChange: (AlertAudioForm) -> Unit,
    onSave: () -> Unit,
) {
    val colors = LocalProAudioColors.current
    val busy = busyAction is AlertsBusyAction.AudioSave

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(text = stringResource(R.string.alerts_audio_eyebrow))
            Text(
                text = stringResource(R.string.alerts_audio_title),
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.alerts_audio_description),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )

            AlertSwitchRow(
                checked = form.airRaidAlertsEnabled,
                onCheckedChange = { checked ->
                    onFormChange(form.copy(airRaidAlertsEnabled = checked))
                },
                title = stringResource(R.string.alerts_enable_air_raid),
                description = stringResource(R.string.alerts_enable_air_raid_hint),
                enabled = !busy,
            )
            AlertSwitchRow(
                checked = form.minuteSilenceEnabled,
                onCheckedChange = { checked ->
                    onFormChange(form.copy(minuteSilenceEnabled = checked))
                },
                title = stringResource(R.string.alerts_enable_minute),
                description = stringResource(R.string.alerts_enable_minute_hint),
                enabled = !busy,
            )

            AlertTextField(
                value = form.duckDb,
                onValueChange = { onFormChange(form.copy(duckDb = it)) },
                label = stringResource(R.string.alerts_duck_db),
                enabled = !busy,
                keyboardType = KeyboardType.Decimal,
            )
            AlertTextField(
                value = form.duckFadeSeconds,
                onValueChange = { onFormChange(form.copy(duckFadeSeconds = it)) },
                label = stringResource(R.string.alerts_duck_fade),
                enabled = !busy,
                keyboardType = KeyboardType.Decimal,
            )
            AlertTextField(
                value = form.restoreFadeSeconds,
                onValueChange = { onFormChange(form.copy(restoreFadeSeconds = it)) },
                label = stringResource(R.string.alerts_restore_fade),
                enabled = !busy,
                keyboardType = KeyboardType.Decimal,
            )
            AlertTextField(
                value = form.alertVolumePercent,
                onValueChange = { onFormChange(form.copy(alertVolumePercent = it)) },
                label = stringResource(R.string.alerts_alert_volume),
                enabled = !busy,
                keyboardType = KeyboardType.Decimal,
            )
            AlertTextField(
                value = form.defaultRestoreVolumePercent,
                onValueChange = {
                    onFormChange(form.copy(defaultRestoreVolumePercent = it))
                },
                label = stringResource(R.string.alerts_restore_volume),
                enabled = !busy,
                keyboardType = KeyboardType.Decimal,
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.alerts_minute_silence_active),
                color = colors.textSoft,
                style = MaterialTheme.typography.titleMedium,
            )

            AlertTextField(
                value = form.minuteSilenceStartTime,
                onValueChange = { onFormChange(form.copy(minuteSilenceStartTime = it)) },
                label = stringResource(R.string.alerts_minute_time),
                enabled = !busy,
                keyboardType = KeyboardType.Text,
            )
            AlertTextField(
                value = form.minuteSilenceTimezone,
                onValueChange = { onFormChange(form.copy(minuteSilenceTimezone = it)) },
                label = stringResource(R.string.alerts_timezone),
                enabled = !busy,
            )
            AlertTextField(
                value = form.minuteSilenceCatchUpSeconds,
                onValueChange = {
                    onFormChange(form.copy(minuteSilenceCatchUpSeconds = it))
                },
                label = stringResource(R.string.alerts_catch_up),
                enabled = !busy,
                keyboardType = KeyboardType.Number,
            )
            AlertTextField(
                value = form.minuteSilenceMusicFadeSeconds,
                onValueChange = {
                    onFormChange(form.copy(minuteSilenceMusicFadeSeconds = it))
                },
                label = stringResource(R.string.alerts_minute_fade),
                enabled = !busy,
                keyboardType = KeyboardType.Decimal,
            )
            AlertTextField(
                value = form.minuteSilenceVolumePercent,
                onValueChange = {
                    onFormChange(form.copy(minuteSilenceVolumePercent = it))
                },
                label = stringResource(R.string.alerts_minute_volume),
                enabled = !busy,
                keyboardType = KeyboardType.Decimal,
            )
            AlertTextField(
                value = form.alertRepeatIntervalMinutes,
                onValueChange = {
                    onFormChange(form.copy(alertRepeatIntervalMinutes = it))
                },
                label = stringResource(R.string.alerts_repeat_interval),
                enabled = !busy,
                keyboardType = KeyboardType.Number,
            )

            AlertSwitchRow(
                checked = form.duckOnlyDuringAnnouncement,
                onCheckedChange = { checked ->
                    onFormChange(form.copy(duckOnlyDuringAnnouncement = checked))
                },
                title = stringResource(R.string.alerts_talkover),
                description = stringResource(R.string.alerts_talkover_hint),
                enabled = !busy,
            )

            SummaryRow(
                label = stringResource(R.string.alerts_audio_processing),
                value = settings.sampleRateMode.uppercase() +
                    " · " + settings.sampleRate + " Hz",
            )

            AlertsMessageView(
                message = message,
                dirty = dirty,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onSave,
                    enabled = !busy && dirty,
                ) {
                    Text(
                        text = stringResource(
                            if (busy) {
                                R.string.alerts_saving
                            } else {
                                R.string.alerts_save_audio
                            },
                        ),
                    )
                }
            }
        }
    }
}
