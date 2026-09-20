package com.bodzey.proaudioplayer.ui.mixer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.api.AudioLevelState
import com.bodzey.proaudioplayer.core.api.MixerTarget
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.SectionLabel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors
import java.util.Locale

@Composable
fun LogicalMixerCard(
    state: MixerUiState,
    blocked: Boolean,
    onRefresh: () -> Unit,
    onLevelChange: (MixerTarget, Double) -> Unit,
    onMuteChange: (MixerTarget, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalProAudioColors.current

    ProAudioPanel(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(text = stringResource(R.string.mixer_eyebrow))
            Text(
                text = stringResource(R.string.mixer_title),
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.mixer_description),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )

            if (blocked) {
                Text(
                    text = stringResource(R.string.mixer_blocked),
                    color = colors.warning,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            when {
                state.loading && state.mixer == null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent,
                        )
                        Text(
                            text = stringResource(R.string.mixer_loading),
                            color = colors.textMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                state.mixer != null -> {
                    MixerChannel(
                        label = stringResource(R.string.mixer_music),
                        target = MixerTarget.Music,
                        level = state.mixer.music,
                        enabled = !blocked && state.pendingTarget == null,
                        pending = state.pendingTarget == MixerTarget.Music,
                        onLevelChange = onLevelChange,
                        onMuteChange = onMuteChange,
                    )
                    MixerChannel(
                        label = stringResource(R.string.mixer_alert),
                        target = MixerTarget.Alert,
                        level = state.mixer.alert,
                        enabled = !blocked && state.pendingTarget == null,
                        pending = state.pendingTarget == MixerTarget.Alert,
                        onLevelChange = onLevelChange,
                        onMuteChange = onMuteChange,
                    )
                }
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = colors.danger,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedButton(
                onClick = onRefresh,
                enabled = !state.loading && state.pendingTarget == null,
            ) {
                Text(stringResource(R.string.mixer_refresh))
            }
        }
    }
}

@Composable
private fun MixerChannel(
    label: String,
    target: MixerTarget,
    level: AudioLevelState,
    enabled: Boolean,
    pending: Boolean,
    onLevelChange: (MixerTarget, Double) -> Unit,
    onMuteChange: (MixerTarget, Boolean) -> Unit,
) {
    val colors = LocalProAudioColors.current
    val serverDb = (level.db ?: -60.0).coerceIn(-60.0, 0.0).toFloat()
    var draftDb by remember(target, serverDb) {
        mutableFloatStateOf(serverDb)
    }
    val description = stringResource(
        R.string.mixer_level_description,
        label,
        draftDb,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = colors.textSoft,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = if (level.muted) {
                    stringResource(R.string.player_muted)
                } else {
                    String.format(Locale.ROOT, "%.1f dB", draftDb)
                },
                color = if (level.muted) colors.danger else colors.text,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
            )
        }

        Slider(
            value = draftDb,
            onValueChange = { value ->
                draftDb = value
            },
            onValueChangeFinished = {
                onLevelChange(target, draftDb.toDouble())
            },
            enabled = enabled && !pending,
            valueRange = -60f..0f,
            colors = SliderDefaults.colors(
                thumbColor = colors.text,
                activeTrackColor = colors.blueAccent,
                inactiveTrackColor = colors.surfaceInset,
            ),
        )

        OutlinedButton(
            onClick = {
                onMuteChange(target, !level.muted)
            },
            enabled = enabled && !pending && level.db != null,
        ) {
            if (pending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.accent,
                )
            } else {
                Text(
                    stringResource(
                        if (level.muted) {
                            R.string.mixer_unmute
                        } else {
                            R.string.mixer_mute
                        },
                    ),
                )
            }
        }
    }
}
