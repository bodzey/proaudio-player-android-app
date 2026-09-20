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
    connected: PlayerSessionState.Connected,
    onRefresh: () -> Unit,
    onProviderFormChange: (AlertProviderForm) -> Unit,
    onProviderTest: () -> Unit,
    onProviderSave: () -> Unit,
    onAudioFormChange: (AlertAudioForm) -> Unit,
    onAudioSave: () -> Unit,
    onPickMedia: (String) -> Unit,
    onResetMedia: (String) -> Unit,
    onResetAllMedia: () -> Unit,
) {
    item(key = "alerts-status") {
        AlertStatusCard(
            priority = connected.status.priority,
            audio = state.audio,
            loading = state.loading,
            onRefresh = onRefresh,
        )
    }

    state.loadError?.let { error ->
        item(key = "alerts-load-error") {
            AlertNotice(
                text = error,
                error = true,
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

    val provider = state.provider
    val providerForm = state.providerForm
    if (provider != null && providerForm != null) {
        item(key = "alerts-provider") {
            ProviderSettingsCard(
                settings = provider,
                form = providerForm,
                dirty = state.providerDirty,
                message = state.providerMessage,
                busyAction = state.busyAction,
                onFormChange = onProviderFormChange,
                onTest = onProviderTest,
                onSave = onProviderSave,
            )
        }
    }

    val audio = state.audio
    val audioForm = state.audioForm
    if (audio != null && audioForm != null) {
        item(key = "alerts-audio") {
            AudioSettingsCard(
                settings = audio,
                form = audioForm,
                dirty = state.audioDirty,
                message = state.audioMessage,
                busyAction = state.busyAction,
                onFormChange = onAudioFormChange,
                onSave = onAudioSave,
            )
        }
    }

    state.media?.let { media ->
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
