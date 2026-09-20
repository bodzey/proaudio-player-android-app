package com.bodzey.proaudioplayer.ui.alerts

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.core.api.AlertAudioSettings
import com.bodzey.proaudioplayer.core.api.AlertMediaFile
import com.bodzey.proaudioplayer.core.api.AlertProviderSettings
import com.bodzey.proaudioplayer.core.api.PriorityState
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.SectionLabel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun LazyListScope.alertsSection(
    state: AlertsUiState,
    connected: PlayerSessionState.Connected,
    onRefresh: () -> Unit,
) {
    item(key = "alerts-status") {
        AlertStatusCard(
            priority = connected.status.priority,
            audio = state.audio,
            loading = state.loading,
            onRefresh = onRefresh,
        )
    }

    state.loadError?.let { error ->
        item(key = "alerts-load-error") {
            AlertNotice(
                text = error,
                error = true,
            )
        }
    }

    if (state.loading &&
        state.provider == null &&
        state.audio == null &&
        state.media == null
    ) {
        item(key = "alerts-loading") {
            AlertsLoadingCard()
        }
    }

    state.provider?.let { provider ->
        item(key = "alerts-provider") {
            ProviderSummaryCard(provider)
        }
    }

    state.audio?.let { audio ->
        item(key = "alerts-audio") {
            AudioPolicyCard(audio)
        }
    }

    state.media?.let { media ->
        item(key = "alerts-media-heading") {
            Column(
                modifier = Modifier.padding(top = 2.dp),
            ) {
                SectionLabel(text = "ФАЙЛИ СПОВІЩЕНЬ")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Аудіофайли системи",
                    color = LocalProAudioColors.current.text,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "Максимальний розмір файла: " +
                        formatBytes(media.maxSizeBytes),
                    color = LocalProAudioColors.current.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        media.items.forEach { item ->
            item(key = "alerts-media-" + item.kind) {
                AlertMediaCard(item)
            }
        }
    }
}

@Composable
private fun AlertStatusCard(
    priority: PriorityState,
    audio: AlertAudioSettings?,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = LocalProAudioColors.current
    val systemEnabled = audio?.airRaidAlertsEnabled != false
    val title = when {
        priority.minuteSilenceActive -> "Хвилина мовчання"
        priority.active -> "Тривога активна"
        !systemEnabled -> "Система вимкнена"
        else -> "Черговий режим"
    }
    val subtitle = when {
        priority.minuteSilenceActive ->
            "Виконується щоденний сценарій хвилини мовчання"
        priority.active ->
            "Система виконує сценарій активної тривоги"
        !systemEnabled ->
            "Опитування alerts.in.ua вимкнено"
        else ->
            "Система працює у штатному режимі"
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
                    SectionLabel(text = "СТАН СИСТЕМИ")
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Система тривог",
                        color = colors.text,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Поточний runtime-стан оповіщень і останні події.",
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(
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
                        Column {
                            Text(
                                text = title,
                                color = statusColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
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
                StatusRow("Режим", priority.mode.ifBlank { "—" })
                StatusRow("Остання відповідь API", formatTimestamp(priority.lastSuccessAt))
                StatusRow("Остання зміна", formatTimestamp(priority.lastChangeAt))
                StatusRow(
                    "UID у тривозі",
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

            Surface(
                onClick = onRefresh,
                enabled = !loading,
                shape = RoundedCornerShape(9.dp),
                color = colors.surfaceRaised,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
            ) {
                Text(
                    text = if (loading) "Оновлення…" else "Оновити дані",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    color = colors.textSoft,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ProviderSummaryCard(
    settings: AlertProviderSettings,
) {
    val colors = LocalProAudioColors.current

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    SectionLabel(text = "API НАЛАШТУВАННЯ")
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "API повітряних тривог",
                        color = colors.text,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                val tokenColor =
                    if (settings.tokenConfigured) colors.success else colors.warning
                Text(
                    text = if (settings.tokenConfigured) {
                        "ТОКЕН НАЛАШТОВАНО"
                    } else {
                        "ТОКЕН ВІДСУТНІЙ"
                    },
                    color = tokenColor,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            SummaryRow("UID локації", settings.locationUid.toString())
            SummaryRow("Тип локації", locationTypeName(settings.locationType))
            SummaryRow(
                "Опитування",
                formatNumber(settings.pollIntervalSeconds) + " с",
            )
            SummaryRow(
                "Timeout",
                formatNumber(settings.requestTimeoutSeconds) + " с",
            )
            SummaryRow(
                "HTTP 429",
                formatNumber(settings.rateLimitBackoffSeconds) + " с",
            )
            SummaryRow(
                "Підтверджень відбою",
                settings.clearConfirmations.toString(),
            )

            Text(
                text = settings.endpoint,
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AudioPolicyCard(
    settings: AlertAudioSettings,
) {
    val colors = LocalProAudioColors.current

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            SectionLabel(text = "НАЛАШТУВАННЯ АУДІО")
            Text(
                text = "Поведінка звуку під час тривоги",
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
            )

            StateLine(
                label = "Система оповіщень",
                enabled = settings.airRaidAlertsEnabled,
            )
            StateLine(
                label = "Хвилина мовчання",
                enabled = settings.minuteSilenceEnabled,
            )

            SummaryRow("Ducking", formatNumber(settings.duckDb) + " dB")
            SummaryRow(
                "Плавне стишення",
                formatNumber(settings.duckFadeSeconds) + " с",
            )
            SummaryRow(
                "Відновлення",
                formatNumber(settings.restoreFadeSeconds) + " с",
            )
            SummaryRow(
                "Гучність ALERT",
                formatPercent(settings.alertVolumePercent),
            )
            SummaryRow(
                "Хвилина мовчання",
                settings.minuteSilenceStartTime + " · " +
                    settings.minuteSilenceTimezone,
            )
            SummaryRow(
                "Гучність хвилини",
                formatPercent(settings.minuteSilenceVolumePercent),
            )
            SummaryRow(
                "Повтор тривоги",
                if (settings.alertRepeatIntervalMinutes == 0L) {
                    "не повторювати"
                } else {
                    settings.alertRepeatIntervalMinutes.toString() + " хв"
                },
            )
            SummaryRow(
                "Talkover",
                if (settings.duckOnlyDuringAnnouncement) {
                    "лише під час оголошення"
                } else {
                    "на час активної тривоги"
                },
            )
            SummaryRow(
                "Обробка",
                settings.sampleRateMode.uppercase() + " · " +
                    settings.sampleRate.toString() + " Hz",
            )
        }
    }
}

@Composable
private fun AlertMediaCard(
    item: AlertMediaFile,
) {
    val colors = LocalProAudioColors.current
    val accent = when (item.kind) {
        "alarm_start" -> colors.danger
        "alarm_end" -> colors.success
        "minute_silence" -> colors.warning
        else -> colors.blueAccent
    }

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.09f), RoundedCornerShape(12.dp)),
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
                    text = formatBytes(item.sizeBytes) + " · " +
                        formatUnixTimestamp(item.modifiedUnixSeconds),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Text(
                text = if (item.configured) "ГОТОВО" else "ВІДСУТНІЙ",
                color = if (item.configured) colors.success else colors.danger,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
) {
    SummaryRow(label, value)
}

@Composable
private fun SummaryRow(
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

@Composable
private fun StateLine(
    label: String,
    enabled: Boolean,
) {
    val colors = LocalProAudioColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.textSoft,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = if (enabled) "УВІМКНЕНО" else "ВИМКНЕНО",
            color = if (enabled) colors.success else colors.textMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AlertNotice(
    text: String,
    error: Boolean,
) {
    val colors = LocalProAudioColors.current
    val accent = if (error) colors.danger else colors.success

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(
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
private fun AlertsLoadingCard() {
    val colors = LocalProAudioColors.current
    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
            Text(
                text = "Завантаження налаштувань оповіщень…",
                color = colors.textSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun locationTypeName(value: String): String =
    when (value) {
        "hromada" -> "Територіальна громада"
        "city" -> "Місто"
        "raion" -> "Район"
        "oblast" -> "Область"
        "standalone" -> "Окрема територія"
        else -> value
    }

private fun formatTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    return runCatching {
        OffsetDateTime
            .parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(timestampFormatter)
    }.getOrDefault(value)
}

private fun formatUnixTimestamp(value: Long?): String {
    if (value == null) return "—"
    return runCatching {
        java.time.Instant
            .ofEpochSecond(value)
            .atZone(ZoneId.systemDefault())
            .format(timestampFormatter)
    }.getOrDefault("—")
}

private fun formatBytes(value: Long?): String {
    if (value == null) return "—"
    return if (value < 1024L * 1024L) {
        String.format(Locale.ROOT, "%.0f KiB", value / 1024.0)
    } else {
        String.format(Locale.ROOT, "%.1f MiB", value / (1024.0 * 1024.0))
    }
}

private fun formatNumber(value: Double): String =
    String.format(Locale.ROOT, "%.1f", value)

private fun formatPercent(value: Double): String =
    String.format(Locale.ROOT, "%.1f%%", value)

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale("uk", "UA"))
