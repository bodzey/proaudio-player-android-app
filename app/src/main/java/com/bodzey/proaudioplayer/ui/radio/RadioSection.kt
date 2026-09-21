package com.bodzey.proaudioplayer.ui.radio

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.api.PlayerFeature
import com.bodzey.proaudioplayer.core.api.RadioStation
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.SectionLabel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

fun LazyListScope.radioSection(
    state: RadioUiState,
    connected: PlayerSessionState.Connected,
    onRefresh: () -> Unit,
    onStationToggle: (RadioStation) -> Unit,
    onCustomUrlChange: (String) -> Unit,
    onPlayCustom: () -> Unit,
) {
    val activeUrl = activeRadioStreamUrl(connected.status)
    val blocked = connected.status.priority.blocking
    val streamsSupported = PlayerFeature.NETWORK_STREAMS in connected.capabilities.features
    val interactionEnabled = streamsSupported && !blocked && state.pendingUrl == null

    item(key = "radio-header") {
        RadioHeader(
            active = activeUrl != null,
            stationCount = state.stations.size,
            loading = state.loading,
            onRefresh = onRefresh,
        )
    }

    if (blocked) {
        item(key = "radio-blocked") {
            RadioNotice(
                text = stringResource(R.string.radio_blocked),
                error = true,
            )
        }
    } else if (!streamsSupported) {
        item(key = "radio-unsupported") {
            RadioNotice(
                text = stringResource(R.string.radio_unsupported),
                error = true,
            )
        }
    }

    state.loadError?.let { error ->
        item(key = "radio-load-error") {
            LoadError(
                message = error,
                refreshing = state.loading,
                onRefresh = onRefresh,
            )
        }
    }

    if (state.loading && state.stations.isEmpty()) {
        item(key = "radio-loading") {
            LoadingRadioCatalog()
        }
    }

    if (state.stations.isNotEmpty()) {
        item(key = "radio-catalog-label") {
            Column(
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            ) {
                SectionLabel(text = stringResource(R.string.radio_catalog_eyebrow))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.radio_ukrainian_stations),
                    color = LocalProAudioColors.current.text,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        items(
            items = state.stations,
            key = { station -> station.id },
            contentType = { "radio-station" },
        ) { station ->
            RadioStationCard(
                station = station,
                active = isSameRadioStream(activeUrl, station.url),
                pending = state.pendingUrl == station.url,
                enabled = interactionEnabled,
                onClick = { onStationToggle(station) },
            )
        }
    }

    item(key = "radio-custom-stream") {
        CustomStreamPanel(
            url = state.customUrl,
            pending = state.pendingUrl == state.customUrl.trim() &&
                state.customUrl.isNotBlank(),
            enabled = interactionEnabled,
            onUrlChange = onCustomUrlChange,
            onPlay = onPlayCustom,
        )
    }

    state.feedback?.let { feedback ->
        item(key = "radio-feedback") {
            RadioNotice(
                text = feedback.message,
                error = feedback.isError,
            )
        }
    }
}

@Composable
private fun RadioHeader(
    active: Boolean,
    stationCount: Int,
    loading: Boolean,
    onRefresh: () -> Unit,
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
                    SectionLabel(text = stringResource(R.string.radio_eyebrow))
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = stringResource(R.string.radio_title),
                        color = colors.text,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(
                        text = stringResource(R.string.radio_description),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (active) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = colors.success.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            colors.success.copy(alpha = 0.32f),
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.radio_stream_active),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            color = colors.success,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (stationCount > 0) {
                        pluralStringResource(
                            R.plurals.radio_station_count,
                            stationCount,
                            stationCount,
                        )
                    } else {
                        stringResource(R.string.radio_catalog)
                    },
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                )

                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !loading,
                ) {
                    Text(
                        text = stringResource(
                            if (loading) R.string.radio_updating else R.string.radio_update,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioStationCard(
    station: RadioStation,
    active: Boolean,
    pending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalProAudioColors.current
    val accent = stationAccent(station.id)
    val controlDescription = when {
        pending -> stringResource(
            R.string.radio_connecting_station,
            station.name,
        )
        active -> stringResource(
            R.string.radio_stop_station,
            station.name,
        )
        else -> stringResource(
            R.string.radio_listen_station,
            station.name,
        )
    }

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.92f),
                                accent.copy(alpha = 0.46f),
                                colors.surfaceInset,
                            ),
                        ),
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = radioStationShortName(station.name),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = station.name,
                        modifier = Modifier.weight(1f, fill = false),
                        color = colors.text,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (active) {
                        Text(
                            text = stringResource(R.string.radio_on_air),
                            color = colors.success,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                Text(
                    text = radioStationGenre(station.tags),
                    color = colors.textSoft,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = radioStationQuality(station.codec, station.bitrate),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Surface(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = controlDescription
                    },
                shape = RoundedCornerShape(13.dp),
                color = if (active) {
                    colors.accent.copy(alpha = 0.14f)
                } else {
                    colors.surfaceRaised
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (active) colors.accent.copy(alpha = 0.55f) else colors.border,
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (pending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent,
                        )
                    } else {
                        RadioControlGlyph(
                            stop = active,
                            color = if (active) colors.accent else colors.textSoft,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioControlGlyph(
    stop: Boolean,
    color: Color,
) {
    Canvas(modifier = Modifier.size(20.dp)) {
        if (stop) {
            drawRect(
                color = color,
                topLeft = Offset(size.width * 0.28f, size.height * 0.28f),
                size = androidx.compose.ui.geometry.Size(
                    size.width * 0.44f,
                    size.height * 0.44f,
                ),
                style = Stroke(1.8.dp.toPx()),
            )
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.30f, size.height * 0.20f)
                lineTo(size.width * 0.78f, size.height * 0.50f)
                lineTo(size.width * 0.30f, size.height * 0.80f)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
private fun CustomStreamPanel(
    url: String,
    pending: Boolean,
    enabled: Boolean,
    onUrlChange: (String) -> Unit,
    onPlay: () -> Unit,
) {
    val colors = LocalProAudioColors.current
    val focusManager = LocalFocusManager.current

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(text = stringResource(R.string.radio_custom_eyebrow))
            Text(
                text = stringResource(R.string.radio_custom_title),
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.radio_custom_placeholder),
                        color = colors.textMuted,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (enabled && url.isNotBlank()) onPlay()
                    },
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = colors.text,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.blueAccent.copy(alpha = 0.65f),
                    unfocusedBorderColor = colors.borderStrong,
                    focusedContainerColor = colors.surfaceInset,
                    unfocusedContainerColor = colors.surfaceInset,
                    cursorColor = colors.blueAccent,
                ),
            )

            Button(
                onClick = {
                    focusManager.clearFocus()
                    onPlay()
                },
                enabled = enabled && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.radio_play),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Text(
                text = stringResource(R.string.radio_supported_formats),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LoadError(
    message: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = LocalProAudioColors.current
    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = colors.danger,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = onRefresh,
                enabled = !refreshing,
            ) {
                Text(text = stringResource(R.string.radio_retry))
            }
        }
    }
}

@Composable
private fun LoadingRadioCatalog() {
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
                text = stringResource(R.string.radio_loading_catalog),
                color = colors.textSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RadioNotice(
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

private fun stationAccent(id: String): Color {
    val hash = id.fold(0) { value, char -> value * 31 + char.code }
    val hue = ((hash.toLong() and 0x7fffffffL) % 360L).toFloat()
    return Color.hsv(
        hue = hue,
        saturation = 0.64f,
        value = 0.72f,
    )
}
