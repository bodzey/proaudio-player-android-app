package com.bodzey.proaudioplayer.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

@Composable
internal fun SettingsHeader(page: SettingsPage, onBack: () -> Unit) {
    val colors = LocalProAudioColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (page != SettingsPage.Overview) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.settings_back))
            }
        }
        Text(
            text = settingsPageTitle(page),
            modifier = Modifier.semantics { heading() },
            color = colors.text,
            style = MaterialTheme.typography.headlineSmall,
        )
        if (page == SettingsPage.Overview) {
            Text(
                text = stringResource(R.string.settings_description),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun SettingsOverview(state: AlertsUiState, onSelected: (SettingsPage) -> Unit) {
    val colors = LocalProAudioColors.current
    val audio = state.audioForm
    val provider = state.providerForm
    val pending = stringResource(R.string.alerts_unsaved_changes)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsCategory(
                    page = SettingsPage.Announcements,
                    summary = if (audio == null) {
                        stringResource(R.string.settings_unavailable)
                    } else {
                        stringResource(
                            if (audio.airRaidAlertsEnabled) R.string.settings_enabled else R.string.settings_disabled,
                        )
                    },
                    draft = pending.takeIf { state.audioDirty },
                    enabled = audio != null,
                    onSelected = onSelected,
                )
                HorizontalDivider(color = colors.border)
                SettingsCategory(
                    page = SettingsPage.Schedule,
                    summary = when {
                        audio == null -> stringResource(R.string.settings_unavailable)
                        audio.minuteSilenceEnabled ->
                            "${settingsTimeLabel(audio.minuteSilenceStartTime)} · ${audio.minuteSilenceTimezone}"
                        else -> stringResource(R.string.settings_disabled)
                    },
                    draft = pending.takeIf { state.audioDirty },
                    enabled = audio != null,
                    onSelected = onSelected,
                )
                HorizontalDivider(color = colors.border)
                SettingsCategory(
                    page = SettingsPage.Provider,
                    summary = if (provider == null) {
                        stringResource(R.string.settings_unavailable)
                    } else {
                        "${locationTypeName(provider.locationType)} · ${provider.locationUid}"
                    },
                    draft = pending.takeIf { state.providerDirty },
                    enabled = provider != null,
                    onSelected = onSelected,
                )
                HorizontalDivider(color = colors.border)
                SettingsCategory(
                    page = SettingsPage.Media,
                    summary = stringResource(R.string.settings_media_summary),
                    enabled = state.media != null,
                    onSelected = onSelected,
                )
            }
        }
        state.audio?.let { audioSettings ->
            Text(
                text = stringResource(
                    R.string.settings_processing,
                    "${audioSettings.sampleRateMode} · ${audioSettings.sampleRate} Hz",
                ),
                modifier = Modifier.padding(horizontal = 4.dp),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SettingsCategory(
    page: SettingsPage,
    summary: String,
    enabled: Boolean,
    onSelected: (SettingsPage) -> Unit,
    draft: String? = null,
) {
    val colors = LocalProAudioColors.current
    Surface(onClick = { onSelected(page) }, enabled = enabled, color = colors.surface) {
        ListItem(
            headlineContent = { Text(settingsPageTitle(page)) },
            supportingContent = {
                Column {
                    Text(summary)
                    draft?.let { Text(it, color = colors.warning) }
                }
            },
            trailingContent = { Text(stringResource(R.string.settings_open)) },
            colors = ListItemDefaults.colors(
                containerColor = colors.surface,
                headlineColor = if (enabled) colors.text else colors.textMuted,
                supportingColor = colors.textMuted,
                trailingIconColor = colors.accent,
            ),
        )
    }
}

@Composable
internal fun settingsPageTitle(page: SettingsPage): String = stringResource(
    when (page) {
        SettingsPage.Overview -> R.string.settings_title
        SettingsPage.Announcements -> R.string.settings_announcements
        SettingsPage.Schedule -> R.string.settings_minute_silence
        SettingsPage.Provider -> R.string.settings_provider
        SettingsPage.Media -> R.string.settings_media
    },
)
