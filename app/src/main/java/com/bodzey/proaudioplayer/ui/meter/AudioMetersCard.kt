package com.bodzey.proaudioplayer.ui.meter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.meter.MeterState
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.SectionLabel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect

@Composable
fun AudioMetersCard(
    state: StateFlow<MeterState>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalProAudioColors.current
    val buffer = remember { MeterRenderBuffer() }
    val frameClock = remember { MeterFrameClock() }
    var streamStatus by remember { mutableStateOf(MeterStreamStatus.Inactive) }
    var streamFormat by remember { mutableStateOf<MeterStreamFormat?>(null) }

    LaunchedEffect(state, buffer) {
        state.collect { current ->
            when (current) {
                MeterState.Inactive -> {
                    buffer.reset()
                    streamFormat = null
                    streamStatus = MeterStreamStatus.Inactive
                }
                MeterState.Connecting -> {
                    buffer.reset()
                    streamFormat = null
                    streamStatus = MeterStreamStatus.Connecting
                }
                is MeterState.Failed -> {
                    buffer.reset()
                    streamFormat = null
                    streamStatus = MeterStreamStatus.Failed
                }
                is MeterState.Active -> {
                    buffer.write(current.frame)
                    val nextFormat = MeterStreamFormat(
                        sampleRate = current.frame.sampleRate,
                        intervalMillis = current.frame.intervalMillis,
                    )
                    if (streamFormat != nextFormat) {
                        streamFormat = nextFormat
                    }
                    if (streamStatus != MeterStreamStatus.Active) {
                        streamStatus = MeterStreamStatus.Active
                    }
                }
            }
        }
    }

    LaunchedEffect(streamStatus) {
        if (streamStatus != MeterStreamStatus.Active) {
            frameClock.frameTimeNanos = 0L
            return@LaunchedEffect
        }

        while (true) {
            withFrameNanos { frameTimeNanos ->
                frameClock.frameTimeNanos = frameTimeNanos
            }
        }
    }

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

            when (streamStatus) {
                MeterStreamStatus.Inactive -> {
                    Text(
                        text = stringResource(R.string.meters_unavailable),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                MeterStreamStatus.Connecting -> {
                    MeterMessage(
                        text = stringResource(R.string.meters_connecting),
                    )
                }

                MeterStreamStatus.Failed -> {
                    MeterMessage(
                        text = stringResource(R.string.meters_stream_failed),
                    )
                }

                MeterStreamStatus.Active -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        MeterBusCanvas(
                            label = stringResource(R.string.meters_music),
                            bus = MeterBus.Music,
                            buffer = buffer,
                            frameClock = frameClock,
                            modifier = Modifier.weight(1f),
                        )
                        MeterBusCanvas(
                            label = stringResource(R.string.meters_alert),
                            bus = MeterBus.Alert,
                            buffer = buffer,
                            frameClock = frameClock,
                            modifier = Modifier.weight(1f),
                        )
                        MeterBusCanvas(
                            label = stringResource(R.string.meters_master),
                            bus = MeterBus.Master,
                            buffer = buffer,
                            frameClock = frameClock,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    streamFormat?.let { format ->
                        Text(
                            text = String.format(
                                Locale.ROOT,
                                "%d Hz · %d ms",
                                format.sampleRate,
                                format.intervalMillis,
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
private fun MeterBusCanvas(
    label: String,
    bus: MeterBus,
    buffer: MeterRenderBuffer,
    frameClock: MeterFrameClock,
    modifier: Modifier = Modifier,
) {
    val colors = LocalProAudioColors.current
    val dynamics = remember(bus) { MeterDynamics() }
    val shape = RoundedCornerShape(8.dp)
    val description = stringResource(
        R.string.meters_canvas_description,
        label,
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = label,
            color = colors.textSoft,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
        )

        Canvas(
            modifier = Modifier
                .width(72.dp)
                .height(236.dp)
                .clip(shape)
                .background(colors.canvasDeep)
                .border(
                    width = 1.dp,
                    color = colors.borderStrong,
                    shape = shape,
                )
                .semantics {
                    contentDescription = description
                },
        ) {
            val frameTimeNanos = frameClock.frameTimeNanos
            val sourceLevel = buffer.read(bus)
            dynamics.update(
                level = sourceLevel,
                frameTimeNanos = frameTimeNanos,
            )

            drawMeter(
                dynamics = dynamics,
                sourceAvailable = sourceLevel.available,
                frameTimeNanos = frameTimeNanos,
                inactive = colors.surfaceRaised,
                low = colors.success,
                mid = colors.warning,
                high = colors.danger,
                peak = colors.text,
                clip = colors.danger,
            )
        }

        Row(
            modifier = Modifier.width(72.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MeterLaneLabel(
                text = "L",
                modifier = Modifier.weight(1f),
            )
            MeterLaneLabel(
                text = "RMS",
                modifier = Modifier.weight(1.4f),
            )
            MeterLaneLabel(
                text = "R",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MeterLaneLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalProAudioColors.current
    Text(
        text = text,
        modifier = modifier,
        color = colors.textMuted,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

private fun DrawScope.drawMeter(
    dynamics: MeterDynamics,
    sourceAvailable: Boolean,
    frameTimeNanos: Long,
    inactive: Color,
    low: Color,
    mid: Color,
    high: Color,
    peak: Color,
    clip: Color,
) {
    val meterTop = 10.dp.toPx()
    val meterBottom = size.height - 5.dp.toPx()
    val meterHeight = (meterBottom - meterTop).coerceAtLeast(1f)

    val outerPadding = maxOf(3.dp.toPx(), size.width * 0.08f)
    val laneGap = maxOf(1.5.dp.toPx(), size.width * 0.04f)
    val usableWidth = (size.width - outerPadding * 2f - laneGap * 2f)
        .coerceAtLeast(12.dp.toPx())
    val peakWidth = maxOf(3.dp.toPx(), usableWidth * 0.22f)
    val rmsWidth = maxOf(5.dp.toPx(), usableWidth - peakWidth * 2f)
    val leftX = outerPadding
    val rmsX = leftX + peakWidth + laneGap
    val rightX = rmsX + rmsWidth + laneGap

    val segmentGap = 0.75.dp.toPx()
    val segmentHeight = (
        (meterHeight - segmentGap * (METER_SEGMENTS - 1)) /
            METER_SEGMENTS.toFloat()
        ).coerceAtLeast(1f)

    drawSegmentedLane(
        x = leftX,
        laneWidth = peakWidth,
        levelDb = dynamics.displayedPeakDb[0],
        meterBottom = meterBottom,
        segmentGap = segmentGap,
        segmentHeight = segmentHeight,
        inactive = inactive,
        low = low,
        mid = mid,
        high = high,
    )
    drawSegmentedLane(
        x = rmsX,
        laneWidth = rmsWidth,
        levelDb = dynamics.displayedRmsDb,
        meterBottom = meterBottom,
        segmentGap = segmentGap,
        segmentHeight = segmentHeight,
        inactive = inactive,
        low = low,
        mid = mid,
        high = high,
        activeAlpha = 0.72f,
    )
    drawSegmentedLane(
        x = rightX,
        laneWidth = peakWidth,
        levelDb = dynamics.displayedPeakDb[1],
        meterBottom = meterBottom,
        segmentGap = segmentGap,
        segmentHeight = segmentHeight,
        inactive = inactive,
        low = low,
        mid = mid,
        high = high,
    )

    drawRect(
        color = inactive,
        topLeft = Offset(rmsX - 0.5.dp.toPx(), meterTop - 0.5.dp.toPx()),
        size = Size(rmsWidth + 1.dp.toPx(), meterHeight + 1.dp.toPx()),
        style = Stroke(width = 1.dp.toPx()),
    )

    for (channel in 0..1) {
        val x = if (channel == 0) leftX else rightX
        val clipped = dynamics.clipVisible(channel, frameTimeNanos)

        drawRect(
            color = if (clipped) clip else inactive,
            topLeft = Offset(x, 2.dp.toPx()),
            size = Size(peakWidth, 4.dp.toPx()),
        )

        val visiblePeak = dynamics.visiblePeakDb(channel)
        if (sourceAvailable || visiblePeak > METER_MIN_DB + 0.1) {
            val peakY = (
                meterBottom - normalizedDb(visiblePeak) * meterHeight
                ).toFloat().coerceIn(meterTop, meterBottom)
            drawRect(
                color = if (clipped) clip else peak,
                topLeft = Offset(x - 0.5.dp.toPx(), peakY),
                size = Size(peakWidth + 1.dp.toPx(), 1.5.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawSegmentedLane(
    x: Float,
    laneWidth: Float,
    levelDb: Double,
    meterBottom: Float,
    segmentGap: Float,
    segmentHeight: Float,
    inactive: Color,
    low: Color,
    mid: Color,
    high: Color,
    activeAlpha: Float = 1f,
) {
    repeat(METER_SEGMENTS) { segment ->
        val segmentDb =
            METER_MIN_DB +
                ((segment + 1).toDouble() / METER_SEGMENTS) *
                (METER_MAX_DB - METER_MIN_DB)
        val y =
            meterBottom -
                (segment + 1) * segmentHeight -
                segment * segmentGap
        val active = segmentDb <= levelDb
        val color = if (active) {
            levelColor(
                db = segmentDb,
                low = low,
                mid = mid,
                high = high,
            ).copy(alpha = activeAlpha)
        } else {
            inactive
        }

        drawRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(laneWidth, segmentHeight),
        )
    }
}

private fun levelColor(
    db: Double,
    low: Color,
    mid: Color,
    high: Color,
): Color =
    when {
        db >= -3.0 -> high
        db >= -18.0 -> mid
        else -> low
    }

private fun normalizedDb(db: Double): Double =
    (db.coerceIn(METER_MIN_DB, METER_MAX_DB) - METER_MIN_DB) /
        (METER_MAX_DB - METER_MIN_DB)

private enum class MeterStreamStatus {
    Inactive,
    Connecting,
    Active,
    Failed,
}

private data class MeterStreamFormat(
    val sampleRate: Int,
    val intervalMillis: Long,
)

@Stable
private class MeterFrameClock {
    var frameTimeNanos by mutableLongStateOf(0L)
}
