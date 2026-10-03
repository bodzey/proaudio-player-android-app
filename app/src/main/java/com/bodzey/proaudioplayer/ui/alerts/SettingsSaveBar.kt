package com.bodzey.proaudioplayer.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

@Composable
fun SettingsSaveBar(
    page: SettingsPage,
    state: AlertsUiState,
    onProviderTest: () -> Unit,
    onProviderSave: () -> Unit,
    onAudioSave: () -> Unit,
) {
    val colors = LocalProAudioColors.current
    val provider = page == SettingsPage.Provider
    val dirty = if (provider) state.providerDirty else state.audioDirty
    val message = if (provider) state.providerMessage else state.audioMessage
    val loaded = if (provider) state.providerForm != null else state.audioForm != null
    val busy = state.busyAction != null
    val saving = if (provider) state.busyAction is AlertsBusyAction.ProviderSave else
        state.busyAction is AlertsBusyAction.AudioSave
    Surface(color = colors.surface, tonalElevation = 3.dp) {
        Column {
            HorizontalDivider(color = colors.border)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AlertsMessageView(message = message, dirty = dirty)
                if (!provider) {
                    Text(
                        text = stringResource(R.string.settings_shared_audio_save),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (provider) {
                        OutlinedButton(
                            onClick = onProviderTest,
                            modifier = Modifier.weight(1f),
                            enabled = loaded && !busy,
                        ) {
                            if (state.busyAction is AlertsBusyAction.ProviderTest) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                            Text(
                                stringResource(
                                    if (state.busyAction is AlertsBusyAction.ProviderTest) {
                                        R.string.alerts_testing_api
                                    } else {
                                        R.string.alerts_test_api
                                    },
                                ),
                            )
                        }
                    }
                    Button(
                        onClick = if (provider) onProviderSave else onAudioSave,
                        modifier = Modifier.weight(1f),
                        enabled = loaded && !busy && dirty,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (saving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(stringResource(if (saving) R.string.alerts_saving else R.string.alerts_save))
                        }
                    }
                }
            }
        }
    }
}
