package com.bodzey.proaudioplayer.ui.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun AlertNotice(
    text: String,
    error: Boolean,
) {
    val colors = LocalProAudioColors.current
    val accent = if (error) colors.danger else colors.success

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.06f),
        border = BorderStroke(
            1.dp,
            accent.copy(alpha = 0.22f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            color = accent,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun AlertsMessageView(
    message: AlertsMessage?,
    dirty: Boolean = false,
) {
    val colors = LocalProAudioColors.current
    val text = message?.text ?: if (dirty) "Є незбережені зміни" else ""
    if (text.isBlank()) return

    Text(
        text = text,
        color = when {
            message?.isError == true -> colors.danger
            message != null -> colors.success
            else -> colors.warning
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
internal fun SummaryRow(
    label: String,
    value: String,
) {
    val colors = LocalProAudioColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.46f),
            color = colors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.54f),
            color = colors.textSoft,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

internal fun locationTypeName(value: String): String =
    when (value) {
        "hromada" -> "Територіальна громада"
        "city" -> "Місто"
        "raion" -> "Район"
        "oblast" -> "Область"
        "standalone" -> "Окрема територія"
        else -> value
    }

internal fun formatTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    return runCatching {
        OffsetDateTime
            .parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(timestampFormatter)
    }.getOrDefault(value)
}

internal fun formatUnixTimestamp(value: Long?): String {
    if (value == null) return "—"
    return runCatching {
        java.time.Instant
            .ofEpochSecond(value)
            .atZone(ZoneId.systemDefault())
            .format(timestampFormatter)
    }.getOrDefault("—")
}

internal fun formatBytes(value: Long?): String {
    if (value == null) return "—"
    return if (value < 1024L * 1024L) {
        String.format(Locale.ROOT, "%.0f KiB", value / 1024.0)
    } else {
        String.format(Locale.ROOT, "%.1f MiB", value / (1024.0 * 1024.0))
    }
}

internal fun formatNumber(value: Double): String =
    String.format(Locale.ROOT, "%.1f", value)

internal fun formatPercent(value: Double): String =
    String.format(Locale.ROOT, "%.1f%%", value)

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.forLanguageTag("uk-UA"))
