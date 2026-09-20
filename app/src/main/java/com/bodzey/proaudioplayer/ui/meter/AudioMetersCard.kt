package com.bodzey.proaudioplayer.ui.meter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.api.StereoMeterLevel
import com.bodzey.proaudioplayer.core.meter.MeterState
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.SectionLabel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AudioMetersCard(
    state: StateFlow<MeterState>,
    modifier: Modifier = Modifier,
) {
    val meterState by state.collectAsStateWithLifecycle()
    val colors = LocalProAudioColors.current

    ProAudioPanel(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(text = stringResource(R.string.meters_eyebrow))
            Text(
                text = stringResource(R.string.meters_title),
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
            )

            when (val current = meterState) {
                MeterState.Inactive -> {
                    Text(
                        text = stringResource(R.string.meters_unavailable),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                MeterState.Connecting -> {
                    MeterMessage(
                        text = stringResource(R.string.meters_connecting),
                    )
                }

                is MeterState.Failed -> {
                    MeterMessage(
                        text = stringResource(R.string.meters_stream_failed),
                    )
                }

                is MeterState.Active -> {
                    MeterBus(
                        label = stringResource(R.string.meters_master),
                        level = current.frame.master,
                    )
                    MeterBus(
                        label = stringResource(R.string.meters_music),
                        level = current.frame.music,
                    )
                    MeterBus(
                        label = stringResource(R.string.meters_alert),
                        level = current.frame.alert,
                    )

                    Text(
                        text = String.format(
                            Locale.ROOT,
                            "%d Hz · %d ms",
                            current.frame.sampleRate,
                            current.frame.intervalMillis,
                        ),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeterMessage(
    text: String,
) {
    val colors = LocalProAudioColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = colors.accent,
        )
        Text(
            text = text,
            color = colors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MeterBus(
    label: String,
    level: StereoMeterLevel,
) {
    val colors = LocalProAudioColors.current

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = colors.textSoft,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
        )
        if (!level.available) {
            SegmentedMeterRow(
                busLabel = label,
                channel = "L",
                peakDb = MIN_DB,
                rmsDb = MIN_DB,
                clip = false,
                available = false,
            )
            SegmentedMeterRow(
                busLabel = label,
                channel = "R",
                peakDb = MIN_DB,
                rmsDb = MIN_DB,
                clip = false,
                available = false,
            )
        } else {
            SegmentedMeterRow(
                busLabel = label,
                channel = "L",
                peakDb = level.peakDb.left,
                rmsDb = level.rmsDb.left,
                clip = level.clipLeft,
                available = true,
            )
            SegmentedMeterRow(
                busLabel = label,
                channel = "R",
                peakDb = level.peakDb.right,
                rmsDb = level.rmsDb.right,
                clip = level.clipRight,
                available = true,
            )
        }
    }
}

@Composable
private fun SegmentedMeterRow(
    busLabel: String,
    channel: String,
    peakDb: Double,
    rmsDb: Double,
    clip: Boolean,
    available: Boolean,
) {
    val colors = LocalProAudioColors.current
    val description = if (available) {
        stringResource(
            R.string.meters_channel_description,
            busLabel,
            channel,
            peakDb,
            rmsDb,
        )
    } else {
        "$busLabel, $channel"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = channel,
            modifier = Modifier.width(12.dp),
            color = colors.textMuted,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(16.dp),
        ) {
            val gap = 1.5.dp.toPx()
            val segmentWidth =
                (size.width - gap * (SEGMENTS - 1)) / SEGMENTS.toFloat()
            val rmsCount = levelToSegments(rmsDb)
            val peakCount = levelToSegments(peakDb)

            repeat(SEGMENTS) { index ->
                val segmentStartDb =
                    MIN_DB + (MAX_DB - MIN_DB) * index.toDouble() / SEGMENTS
                val activeColor = when {
                    segmentStartDb >= -1.0 -> colors.danger
                    segmentStartDb >= -6.0 -> colors.warning
                    else -> colors.success
                }
                val segmentColor = when {
                    !available -> colors.surfaceInset
                    index < rmsCount -> activeColor.copy(alpha = 0.45f)
                    index < peakCount -> activeColor.copy(alpha = 0.82f)
                    else -> colors.surfaceInset
                }

                val left = index * (segmentWidth + gap)
                drawRoundRect(
                    color = segmentColor,
                    topLeft = Offset(left, 0f),
                    size = Size(segmentWidth.coerceAtLeast(1f), size.height),
                    cornerRadius = CornerRadius(1.5.dp.toPx()),
                )
            }

            if (clip) {
                drawRoundRect(
                    color = colors.danger,
                    topLeft = Offset(
                        x = size.width - segmentWidth.coerceAtLeast(1f),
                        y = 0f,
                    ),
                    size = Size(segmentWidth.coerceAtLeast(1f), size.height),
                    cornerRadius = CornerRadius(1.5.dp.toPx()),
                )
            }
        }

        if (clip) {
            Text(
                text = stringResource(R.string.meters_clip),
                color = colors.danger,
                style = MaterialTheme.typography.labelMedium,
            )
        } else {
            Spacer(modifier = Modifier.width(28.dp))
        }
    }
}

private fun levelToSegments(db: Double): Int {
    if (!db.isFinite()) return 0
    val normalized = ((db.coerceIn(MIN_DB, MAX_DB) - MIN_DB) /
        (MAX_DB - MIN_DB))
    return (normalized * SEGMENTS)
        .toInt()
        .coerceIn(0, SEGMENTS)
}

private const val SEGMENTS = 32
private const val MIN_DB = -60.0
private const val MAX_DB = 0.0
