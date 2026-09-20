package com.bodzey.proaudioplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

@Composable
fun ProAudioShell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalProAudioColors.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.canvas,
                        colors.canvasDeep,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        center = Offset.Zero,
                        radius = 900f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.blueAccent.copy(alpha = 0.07f),
                            Color.Transparent,
                        ),
                        center = Offset(1100f, 80f),
                        radius = 1000f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF0284C7),
                            colors.blueAccent,
                            Color(0xFF2563EB),
                        ),
                    ),
                ),
        )
        content()
    }
}

@Composable
fun ProAudioHeader(
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalProAudioColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BrandMark()
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "PRO",
                        color = colors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                    )
                    Text(
                        text = "AUDIO",
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                    )
                }
                Text(
                    text = "NETWORK STREAMING PLAYER",
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 8.sp,
                    letterSpacing = 1.2.sp,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = trailing,
        )
    }
}

@Composable
private fun BrandMark() {
    val colors = LocalProAudioColors.current

    Surface(
        modifier = Modifier.size(38.dp),
        shape = RoundedCornerShape(11.dp),
        color = colors.surfaceRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong),
    ) {
        Canvas(modifier = Modifier.padding(8.dp)) {
            val bars = listOf(0.38f, 0.72f, 1f, 0.62f, 0.84f)
            val step = size.width / bars.size
            bars.forEachIndexed { index, factor ->
                val x = step * index + step / 2f
                val barHeight = size.height * factor
                drawLine(
                    color = if (index % 2 == 0) colors.accent else colors.text,
                    start = Offset(x, (size.height - barHeight) / 2f),
                    end = Offset(x, (size.height + barHeight) / 2f),
                    strokeWidth = 3.dp.toPx(),
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    state: StatusBadgeState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalProAudioColors.current
    val statusColor = when (state) {
        StatusBadgeState.Online -> colors.success
        StatusBadgeState.Connecting -> colors.warning
        StatusBadgeState.Offline -> colors.danger
        StatusBadgeState.Neutral -> colors.textMuted
    }

    Row(
        modifier = modifier
            .border(1.dp, colors.border, RoundedCornerShape(9.dp))
            .background(colors.surfaceRaised.copy(alpha = 0.92f), RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(statusColor, CircleShape),
        )
        Text(
            text = text,
            color = colors.textSoft,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

enum class StatusBadgeState {
    Online,
    Connecting,
    Offline,
    Neutral,
}

@Composable
fun ProAudioPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalProAudioColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        content = content,
    )
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalProAudioColors.current
    Text(
        text = text,
        modifier = modifier,
        color = colors.accent,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
    )
}
