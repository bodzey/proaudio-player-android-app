package com.bodzey.proaudioplayer.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

fun LazyListScope.alertsSection(
    state: AlertsUiState,
    page: SettingsPage,
    connected: PlayerSessionState.Connected,
    onPageSelected: (SettingsPage) -> Unit,
    onRefresh: () -> Unit,
    onProviderFormChange: (AlertProviderForm) -> Unit,
    onAudioFormChange: (AlertAudioForm) -> Unit,
    onPickMedia: (String) -> Unit,
    onResetMedia: (String) -> Unit,
    onResetAllMedia: () -> Unit,
) {
    item(key = "settings-heading") {
        SettingsHeader(page = page, onBack = { onPageSelected(SettingsPage.Overview) })
    }

    state.loadError?.let { error ->
        item(key = "alerts-load-error") {
            AlertNotice(
                text = error,
                error = true,
            )
        }
    }

    if (page == SettingsPage.Overview && (state.providerDirty || state.audioDirty)) {
        item(key = "alerts-drafts") {
            AlertNotice(
                text = stringResource(R.string.alerts_drafts_retained),
                error = false,
            )
        }
    }

    if (state.loading &&
        state.provider == null &&
        state.audio == null &&
        state.media == null
    ) {
        item(key = "alerts-loading") {
            AlertsLoadingCard()
        }
    }

    if (page == SettingsPage.Overview) {
        item(key = "settings-overview") {
            SettingsOverview(state = state, onSelected = onPageSelected)
        }
        item(key = "settings-diagnostics") {
            SettingsDiagnostics(state = state, connected = connected, onRefresh = onRefresh)
        }
    }

    val provider = state.provider
    val providerForm = state.providerForm
    if (page == SettingsPage.Provider && provider != null && providerForm != null) {
        item(key = "alerts-provider") {
            ProviderSettingsCard(
                settings = provider,
                form = providerForm,
                message = state.providerMessage,
                busyAction = state.busyAction,
                onFormChange = onProviderFormChange,
            )
        }
    }

    val audioForm = state.audioForm
    if ((page == SettingsPage.Announcements || page == SettingsPage.Schedule) && audioForm != null) {
        item(key = "alerts-audio") {
            AudioSettingsCard(
                page = page,
                form = audioForm,
                message = state.audioMessage,
                busyAction = state.busyAction,
                onFormChange = onAudioFormChange,
            )
        }
    }

    state.media?.takeIf { page == SettingsPage.Media }?.let { media ->
        item(key = "alerts-media") {
            AlertMediaSection(
                media = media,
                busyAction = state.busyAction,
                message = state.mediaMessage,
                onPickMedia = onPickMedia,
                onResetMedia = onResetMedia,
                onResetAll = onResetAllMedia,
            )
        }
    }
}

@Composable
private fun SettingsDiagnostics(
    state: AlertsUiState,
    connected: PlayerSessionState.Connected,
    onRefresh: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    androidx.compose.foundation.layout.Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.settings_diagnostics_hide else R.string.settings_diagnostics))
        }
        if (expanded) {
            AlertStatusCard(
                priority = connected.status.priority,
                audio = state.audio,
                loading = state.loading,
                onRefresh = onRefresh,
            )
        } else if (state.loadError != null) {
            TextButton(onClick = onRefresh, enabled = !state.loading) {
                Text(stringResource(R.string.alerts_refresh))
            }
        }
    }
}

@Composable
private fun AlertsLoadingCard() {
    val colors = LocalProAudioColors.current
    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
            Text(
                text = stringResource(R.string.alerts_loading),
                color = colors.textSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
