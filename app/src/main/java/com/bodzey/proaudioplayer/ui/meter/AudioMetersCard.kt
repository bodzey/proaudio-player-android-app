package com.bodzey.proaudioplayer.ui.meter

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.api.StereoMeterLevel
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.SectionLabel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun AudioMetersCard(
    source: MeterRenderSource,
    modifier: Modifier = Modifier,
) {
    val streamStatus by source.status.collectAsStateWithLifecycle()
    val colors = LocalProAudioColors.current
    val frameClock = remember { MeterFrameClock() }

    MeterFrameDriver(
        source = source,
        active = streamStatus == MeterStreamStatus.Active,
        frameClock = frameClock,
    )

    ProAudioPanel(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    SectionLabel(text = stringResource(R.string.meters_eyebrow))
                    Text(
                        text = stringResource(R.string.meters_title),
                        color = colors.text,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                if (streamStatus == MeterStreamStatus.Active) {
                    MeterOnlineBadge()
                }
            }

            when (streamStatus) {
                MeterStreamStatus.Inactive -> MeterMessage(
                    text = stringResource(R.string.meters_unavailable),
                    loading = false,
                )

                MeterStreamStatus.Connecting -> MeterMessage(
                    text = stringResource(R.string.meters_connecting),
                    loading = true,
                )

                MeterStreamStatus.Failed -> MeterMessage(
                    text = stringResource(R.string.meters_stream_failed),
                    loading = false,
                )

                MeterStreamStatus.Active -> MeterBridge(
                    buffer = source.buffer,
                    frameClock = frameClock,
                )
            }
        }
    }
}

@Composable
private fun MeterFrameDriver(
    source: MeterRenderSource,
    active: Boolean,
    frameClock: MeterFrameClock,
) {
    LaunchedEffect(source, active) {
        if (!active) {
            frameClock.frameTimeNanos = 0L
            return@LaunchedEffect
        }

        coroutineScope {
            val wake = Channel<Unit>(Channel.CONFLATED)
            val renderUntilNanos = AtomicLong(0L)

            val pulseCollector = launch {
                source.renderPulse.collect {
                    renderUntilNanos.set(
                        SystemClock.elapsedRealtimeNanos() + RENDER_TAIL_NANOS,
                    )
                    wake.trySend(Unit)
                }
            }

            try {
                while (isActive) {
                    wake.receive()

                    var previousFrameNanos = 0L
                    var accumulatedNanos = TARGET_FRAME_INTERVAL_NANOS

                    while (
                        isActive &&
                        SystemClock.elapsedRealtimeNanos() < renderUntilNanos.get()
                    ) {
                        withFrameNanos { frameTimeNanos ->
                            if (previousFrameNanos == 0L) {
                                previousFrameNanos = frameTimeNanos
                            } else {
                                accumulatedNanos +=
                                    (frameTimeNanos - previousFrameNanos)
                                        .coerceIn(0L, MAX_VSYNC_STEP_NANOS)
                                previousFrameNanos = frameTimeNanos
                            }

                            if (accumulatedNanos >= TARGET_FRAME_INTERVAL_NANOS) {
                                frameClock.frameTimeNanos = frameTimeNanos
                                accumulatedNanos %= TARGET_FRAME_INTERVAL_NANOS
                            }
                        }
                    }
                }
            } finally {
                pulseCollector.cancel()
                wake.close()
            }
        }
    }
}

@Composable
private fun MeterOnlineBadge() {
    val colors = LocalProAudioColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.success),
        )
        Text(
            text = stringResource(R.string.meters_online),
            color = colors.success,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun MeterMessage(
    text: String,
    loading: Boolean,
) {
    val colors = LocalProAudioColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
        }

        Text(
            text = text,
            color = colors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MeterBridge(
    buffer: MeterRenderBuffer,
    frameClock: MeterFrameClock,
) {
    val colors = LocalProAudioColors.current
    val dynamics = remember {
        arrayOf(
            MeterDynamics(),
            MeterDynamics(),
            MeterDynamics(),
        )
    }
    val shape = RoundedCornerShape(10.dp)
    val description = stringResource(R.string.meters_bridge_description)

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        MeterBusLabels()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(218.dp)
                .clip(shape)
                .background(colors.canvasDeep)
                .semantics {
                    contentDescription = description
                }
                .drawWithCache {
                    val outerPadding = 10.dp.toPx()
                    val busGap = 9.dp.toPx()
                    val meterTop = 12.dp.toPx()
                    val meterBottom = size.height - 8.dp.toPx()
                    val meterHeight = (meterBottom - meterTop).coerceAtLeast(1f)
                    val busWidth =
                        (
                            size.width -
                                outerPadding * 2f -
                                busGap * (METER_BUS_COUNT - 1)
                            ) / METER_BUS_COUNT
                    val buses = Array(METER_BUS_COUNT) { index ->
                        meterBusGeometry(
                            index = index,
                            outerPadding = outerPadding,
                            busGap = busGap,
                            busWidth = busWidth,
                        )
                    }
                    val activeBrush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to colors.danger,
                            0.05f to colors.danger,
                            0.30f to colors.warning,
                            1.00f to colors.success,
                        ),
                        startY = meterTop,
                        endY = meterBottom,
                    )
                    val separatorPath = Path().apply {
                        repeat(METER_SEGMENT_ROWS - 1) { segment ->
                            val fraction =
                                (segment + 1).toFloat() / METER_SEGMENT_ROWS
                            val y = meterTop + meterHeight * fraction

                            buses.forEach { bus ->
                                moveTo(bus.left, y)
                                lineTo(bus.right, y)
                            }
                        }
                    }

                    onDrawBehind {
                        val frameTimeNanos = frameClock.frameTimeNanos
                        val frame = buffer.readFrame()

                        dynamics[MUSIC_INDEX].update(
                            level = frame?.music,
                            frameTimeNanos = frameTimeNanos,
                        )
                        dynamics[ALERT_INDEX].update(
                            level = frame?.alert,
                            frameTimeNanos = frameTimeNanos,
                        )
                        dynamics[MASTER_INDEX].update(
                            level = frame?.master,
                            frameTimeNanos = frameTimeNanos,
                        )

                        repeat(METER_BUS_COUNT) { index ->
                            val level = when (index) {
                                MUSIC_INDEX -> frame?.music
                                ALERT_INDEX -> frame?.alert
                                else -> frame?.master
                            }

                            drawMeterBus(
                                geometry = buses[index],
                                dynamics = dynamics[index],
                                sourceLevel = level,
                                frameTimeNanos = frameTimeNanos,
                                activeBrush = activeBrush,
                                inactive = colors.surfaceRaised,
                                peak = colors.text,
                                clip = colors.danger,
                            )
                        }

                        drawPath(
                            path = separatorPath,
                            color = colors.canvasDeep.copy(alpha = 0.92f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 0.9.dp.toPx(),
                            ),
                        )

                        for (index in 1 until METER_BUS_COUNT) {
                            val x =
                                outerPadding +
                                    index * busWidth +
                                    (index - 0.5f) * busGap
                            drawLine(
                                color = colors.border.copy(alpha = 0.65f),
                                start = Offset(x, 5.dp.toPx()),
                                end = Offset(x, size.height - 5.dp.toPx()),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                    }
                },
        )

        MeterLaneLabels()
    }
}

@Composable
private fun MeterBusLabels() {
    val colors = LocalProAudioColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        listOf(
            stringResource(R.string.meters_music),
            stringResource(R.string.meters_alert),
            stringResource(R.string.meters_master),
        ).forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = colors.textSoft,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MeterLaneLabels() {
    val colors = LocalProAudioColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        repeat(METER_BUS_COUNT) {
            Row(
                modifier = Modifier.weight(1f),
            ) {
                MeterLaneLabel(
                    text = "L",
                    modifier = Modifier.weight(1f),
                    color = colors.textMuted,
                )
                MeterLaneLabel(
                    text = "RMS",
                    modifier = Modifier.weight(2f),
                    color = colors.textMuted,
                )
                MeterLaneLabel(
                    text = "R",
                    modifier = Modifier.weight(1f),
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun MeterLaneLabel(
    text: String,
    modifier: Modifier,
    color: Color,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

private fun DrawScope.drawMeterBus(
    geometry: MeterBusGeometry,
    dynamics: MeterDynamics,
    sourceLevel: StereoMeterLevel?,
    frameTimeNanos: Long,
    activeBrush: Brush,
    inactive: Color,
    peak: Color,
    clip: Color,
) {
    val meterTop = 12.dp.toPx()
    val meterBottom = size.height - 8.dp.toPx()
    val meterHeight = (meterBottom - meterTop).coerceAtLeast(1f)
    val cornerRadius = 1.5.dp.toPx()

    for (lane in 0..2) {
        val x = geometry.laneX(lane)
        val width = geometry.laneWidth(lane)
        val levelDb = when (lane) {
            LEFT_LANE -> dynamics.displayedPeakDb[0]
            RMS_LANE -> dynamics.displayedRmsDb
            else -> dynamics.displayedPeakDb[1]
        }
        val top =
            (
                meterBottom -
                    normalizedDb(levelDb) * meterHeight
                ).toFloat().coerceIn(meterTop, meterBottom)

        drawRoundRect(
            color = inactive,
            topLeft = Offset(x, meterTop),
            size = Size(width, meterHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                cornerRadius,
                cornerRadius,
            ),
        )

        if (top < meterBottom - 0.5f) {
            drawRect(
                brush = activeBrush,
                topLeft = Offset(x, top),
                size = Size(width, meterBottom - top),
                alpha = if (lane == RMS_LANE) 0.74f else 1f,
            )
        }
    }

    for (channel in 0..1) {
        val lane = if (channel == 0) LEFT_LANE else RIGHT_LANE
        val x = geometry.laneX(lane)
        val width = geometry.laneWidth(lane)
        val clipped = dynamics.clipVisible(channel, frameTimeNanos)

        drawRoundRect(
            color = if (clipped) clip else inactive,
            topLeft = Offset(x, 3.dp.toPx()),
            size = Size(width, 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                2.dp.toPx(),
                2.dp.toPx(),
            ),
        )

        val visiblePeak = dynamics.visiblePeakDb(channel)
        if (sourceLevel?.available == true || visiblePeak > METER_MIN_DB + 0.1) {
            val y =
                (
                    meterBottom -
                        normalizedDb(visiblePeak) * meterHeight
                    ).toFloat().coerceIn(meterTop, meterBottom)

            drawRect(
                color = if (clipped) clip else peak,
                topLeft = Offset(x - 0.5.dp.toPx(), y),
                size = Size(width + 1.dp.toPx(), 1.5.dp.toPx()),
            )
        }
    }
}

private fun meterBusGeometry(
    index: Int,
    outerPadding: Float,
    busGap: Float,
    busWidth: Float,
): MeterBusGeometry {
    val left = outerPadding + index * (busWidth + busGap)
    val innerPadding = busWidth * 0.09f
    val laneGap = busWidth * 0.035f
    val available = busWidth - innerPadding * 2f - laneGap * 2f
    val peakWidth = available * 0.25f
    val rmsWidth = available - peakWidth * 2f
    val leftPeakX = left + innerPadding
    val rmsX = leftPeakX + peakWidth + laneGap
    val rightPeakX = rmsX + rmsWidth + laneGap

    return MeterBusGeometry(
        left = left,
        right = left + busWidth,
        leftPeakX = leftPeakX,
        rmsX = rmsX,
        rightPeakX = rightPeakX,
        peakWidth = peakWidth,
        rmsWidth = rmsWidth,
    )
}

private data class MeterBusGeometry(
    val left: Float,
    val right: Float,
    val leftPeakX: Float,
    val rmsX: Float,
    val rightPeakX: Float,
    val peakWidth: Float,
    val rmsWidth: Float,
) {
    fun laneX(lane: Int): Float =
        when (lane) {
            LEFT_LANE -> leftPeakX
            RMS_LANE -> rmsX
            else -> rightPeakX
        }

    fun laneWidth(lane: Int): Float =
        if (lane == RMS_LANE) rmsWidth else peakWidth
}

private fun normalizedDb(db: Double): Double =
    (db.coerceIn(METER_MIN_DB, METER_MAX_DB) - METER_MIN_DB) /
        (METER_MAX_DB - METER_MIN_DB)

@Stable
private class MeterFrameClock {
    var frameTimeNanos by mutableLongStateOf(0L)
}

private const val METER_BUS_COUNT = 3
private const val MUSIC_INDEX = 0
private const val ALERT_INDEX = 1
private const val MASTER_INDEX = 2

private const val LEFT_LANE = 0
private const val RMS_LANE = 1
private const val RIGHT_LANE = 2

private const val METER_SEGMENT_ROWS = 48

private val TARGET_FRAME_INTERVAL_NANOS =
    (1_000_000_000.0 / TARGET_RENDER_FPS).toLong()

private const val MAX_VSYNC_STEP_NANOS = 50_000_000L
private const val RENDER_TAIL_NANOS = 3_250_000_000L
