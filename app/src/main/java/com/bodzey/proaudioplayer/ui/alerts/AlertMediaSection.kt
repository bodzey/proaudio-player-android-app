package com.bodzey.proaudioplayer.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.api.AlertMediaCatalog
import com.bodzey.proaudioplayer.core.api.AlertMediaFile
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.SectionLabel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

@Composable
internal fun AlertMediaSection(
    media: AlertMediaCatalog,
    busyAction: AlertsBusyAction?,
    message: AlertsMessage?,
    onPickMedia: (String) -> Unit,
    onResetMedia: (String) -> Unit,
    onResetAll: () -> Unit,
) {
    val colors = LocalProAudioColors.current
    val anyBusy = busyAction != null

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel(text = stringResource(R.string.alerts_media_eyebrow))
                Text(
                    text = stringResource(R.string.alerts_media_title),
                    color = colors.text,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.alerts_media_description),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.alerts_media_max_size,
                        formatBytes(media.maxSizeBytes),
                    ),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = onResetAll,
                    enabled = !anyBusy,
                ) {
                    if (busyAction is AlertsBusyAction.MediaResetAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.alerts_media_restore_all))
                    }
                }
                AlertsMessageView(message = message)
            }
        }

        media.items.forEach { item ->
            AlertMediaCard(
                item = item,
                busyAction = busyAction,
                onPick = { onPickMedia(item.kind) },
                onReset = { onResetMedia(item.kind) },
            )
        }
    }
}

@Composable
private fun AlertMediaCard(
    item: AlertMediaFile,
    busyAction: AlertsBusyAction?,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = LocalProAudioColors.current
    val accent = when (item.kind) {
        "alarm_start" -> colors.danger
        "alarm_end" -> colors.success
        "minute_silence" -> colors.warning
        else -> colors.blueAccent
    }
    val uploading =
        busyAction is AlertsBusyAction.MediaUpload && busyAction.kind == item.kind
    val resetting =
        busyAction is AlertsBusyAction.MediaReset && busyAction.kind == item.kind
    val anyBusy = busyAction != null

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            accent.copy(alpha = 0.09f),
                            RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(accent, CircleShape),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.label,
                        color = colors.text,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = item.fileName,
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.alerts_media_size_updated,
                            formatBytes(item.sizeBytes),
                            formatUnixTimestamp(item.modifiedUnixSeconds),
                        ),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Text(
                    text = stringResource(
                        if (item.configured) {
                            R.string.alerts_media_ready
                        } else {
                            R.string.alerts_media_missing
                        },
                    ),
                    color = if (item.configured) colors.success else colors.danger,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onPick,
                    modifier = Modifier.weight(1f),
                    enabled = !anyBusy,
                ) {
                    if (uploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.alerts_media_choose))
                    }
                }
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                    enabled = !anyBusy,
                ) {
                    if (resetting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.alerts_media_restore))
                    }
                }
            }
        }
    }
}
