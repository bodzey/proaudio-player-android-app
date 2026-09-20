package com.bodzey.proaudioplayer.ui.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.api.AlertAudioSettings
import com.bodzey.proaudioplayer.core.api.PriorityState
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.SectionLabel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

@Composable
internal fun AlertStatusCard(
    priority: PriorityState,
    audio: AlertAudioSettings?,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = LocalProAudioColors.current
    val systemEnabled = audio?.airRaidAlertsEnabled != false
    val title = when {
        priority.minuteSilenceActive -> stringResource(R.string.alerts_minute_silence_active)
        priority.active -> stringResource(R.string.alerts_alert_active)
        !systemEnabled -> stringResource(R.string.alerts_system_disabled)
        else -> stringResource(R.string.alerts_standby)
    }
    val subtitle = when {
        priority.minuteSilenceActive ->
            stringResource(R.string.alerts_minute_silence_running)
        priority.active ->
            stringResource(R.string.alerts_active_description)
        !systemEnabled ->
            stringResource(R.string.alerts_disabled_description)
        else ->
            stringResource(R.string.alerts_standby_description)
    }
    val statusColor = when {
        priority.minuteSilenceActive -> colors.warning
        priority.active -> colors.danger
        !systemEnabled -> colors.textMuted
        else -> colors.success
    }

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    SectionLabel(text = stringResource(R.string.alerts_system_state))
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = stringResource(R.string.alerts_system_title),
                        color = colors.text,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = stringResource(R.string.alerts_system_description),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.08f),
                    border = BorderStroke(
                        1.dp,
                        statusColor.copy(alpha = 0.28f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(statusColor, CircleShape),
                        )
                        Text(
                            text = title,
                            color = statusColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Text(
                text = subtitle,
                color = colors.textSoft,
                style = MaterialTheme.typography.bodyMedium,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryRow(
                    stringResource(R.string.alerts_mode),
                    priority.mode.ifBlank { "—" },
                )
                SummaryRow(
                    stringResource(R.string.alerts_last_api_response),
                    formatTimestamp(priority.lastSuccessAt),
                )
                SummaryRow(
                    stringResource(R.string.alerts_last_change),
                    formatTimestamp(priority.lastChangeAt),
                )
                SummaryRow(
                    stringResource(R.string.alerts_matched_uids),
                    priority.matchedUids
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(", ")
                        ?: "—",
                )
            }

            priority.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                AlertNotice(
                    text = error,
                    error = true,
                )
            }

            OutlinedButton(
                onClick = onRefresh,
                enabled = !loading,
            ) {
                Text(
                    text = stringResource(
                        if (loading) {
                            R.string.alerts_refreshing
                        } else {
                            R.string.alerts_refresh
                        },
                    ),
                )
            }
        }
    }
}
