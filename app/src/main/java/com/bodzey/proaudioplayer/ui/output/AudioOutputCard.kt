package com.bodzey.proaudioplayer.ui.output

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.api.AudioOutputDescriptor
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.SectionLabel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

@Composable
fun AudioOutputCard(
    state: OutputUiState,
    onRefresh: () -> Unit,
    onSelect: (AudioOutputDescriptor) -> Unit,
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
            SectionLabel(text = stringResource(R.string.outputs_eyebrow))
            Text(
                text = stringResource(R.string.outputs_title),
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.outputs_description),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )

            when {
                state.loading && state.outputs.isEmpty() -> {
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
                            text = stringResource(R.string.outputs_loading),
                            color = colors.textMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                state.outputs.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.outputs_empty),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    state.outputs.forEach { output ->
                        OutputRow(
                            output = output,
                            pending = state.pendingOutputId == output.id,
                            interactionsEnabled = state.pendingOutputId == null,
                            onSelect = onSelect,
                        )
                    }
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
                enabled = !state.loading && state.pendingOutputId == null,
            ) {
                Text(stringResource(R.string.outputs_refresh))
            }
        }
    }
}

@Composable
private fun OutputRow(
    output: AudioOutputDescriptor,
    pending: Boolean,
    interactionsEnabled: Boolean,
    onSelect: (AudioOutputDescriptor) -> Unit,
) {
    val colors = LocalProAudioColors.current
    val borderColor = when {
        output.selected -> colors.accent.copy(alpha = 0.65f)
        !output.available -> colors.border.copy(alpha = 0.55f)
        else -> colors.border
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceRaised,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = output.name,
                    color = if (output.available) colors.text else colors.textMuted,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                val details = outputDetails(output)
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = output.state.ifBlank { output.deviceClass },
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            when {
                output.selected -> {
                    Text(
                        text = stringResource(R.string.outputs_selected),
                        color = colors.accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                !output.available -> {
                    Text(
                        text = stringResource(R.string.outputs_unavailable),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                pending -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent,
                        )
                        Text(
                            text = stringResource(R.string.outputs_switching),
                            color = colors.textSoft,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                else -> {
                    OutlinedButton(
                        onClick = { onSelect(output) },
                        enabled = interactionsEnabled,
                    ) {
                        Text(stringResource(R.string.outputs_select))
                    }
                }
            }
        }
    }
}

@Composable
private fun outputDetails(output: AudioOutputDescriptor): List<String> =
    buildList {
        output.capabilities.deviceBus
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
            ?.let(::add)
        output.capabilities.deviceApi
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
            ?.let(::add)
        output.capabilities.sampleRate
            ?.let { rate ->
                add(stringResource(R.string.outputs_details_rate, rate))
            }
        output.capabilities.channels
            ?.let { channels ->
                add(stringResource(R.string.outputs_details_channels, channels))
            }
    }
