package com.bodzey.proaudioplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.api.AudioLevelState
import com.bodzey.proaudioplayer.core.api.AudioOutputDescriptor
import com.bodzey.proaudioplayer.core.api.PlayerAction
import com.bodzey.proaudioplayer.core.api.PlayerControls
import com.bodzey.proaudioplayer.core.api.PlayerState
import com.bodzey.proaudioplayer.core.api.RadioStation
import com.bodzey.proaudioplayer.core.meter.MeterState
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import com.bodzey.proaudioplayer.ui.AppSection
import com.bodzey.proaudioplayer.ui.alerts.AlertAudioForm
import com.bodzey.proaudioplayer.ui.alerts.AlertProviderForm
import com.bodzey.proaudioplayer.ui.alerts.AlertsUiState
import com.bodzey.proaudioplayer.ui.alerts.alertsSection
import com.bodzey.proaudioplayer.ui.components.PrimaryNavigation
import com.bodzey.proaudioplayer.ui.components.ProAudioHeader
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.ProAudioShell
import com.bodzey.proaudioplayer.ui.components.SectionLabel
import com.bodzey.proaudioplayer.ui.components.StatusBadge
import com.bodzey.proaudioplayer.ui.components.StatusBadgeState
import com.bodzey.proaudioplayer.ui.media.MediaUiState
import com.bodzey.proaudioplayer.ui.media.mediaSection
import com.bodzey.proaudioplayer.ui.meter.AudioMetersCard
import com.bodzey.proaudioplayer.ui.output.AudioOutputCard
import com.bodzey.proaudioplayer.ui.output.OutputUiState
import com.bodzey.proaudioplayer.ui.radio.RadioUiState
import com.bodzey.proaudioplayer.ui.radio.radioSection
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PlayerScreen(
    state: PlayerSessionState,
    section: AppSection,
    pendingAction: PlayerAction?,
    masterMuteBusy: Boolean,
    masterVolumeOverride: Double?,
    actionError: String?,
    radioState: RadioUiState,
    alertsState: AlertsUiState,
    meterState: StateFlow<MeterState>,
    outputState: OutputUiState,
    mediaState: MediaUiState,
    onSectionSelected: (AppSection) -> Unit,
    onAction: (PlayerAction) -> Unit,
    onMasterVolumeChange: (Double) -> Unit,
    onMasterMuteChange: (Boolean) -> Unit,
    onOutputRefresh: () -> Unit,
    onOutputSelect: (AudioOutputDescriptor) -> Unit,
    onMediaRefresh: () -> Unit,
    onMediaRefreshLibrary: () -> Unit,
    onMediaLibraryQueryChange: (String) -> Unit,
    onMediaPlayLibraryPath: (String) -> Unit,
    onMediaLoadPlaylist: (String) -> Unit,
    onMediaPlayQueueItem: (Int) -> Unit,
    onMediaRemoveQueueItem: (Int) -> Unit,
    onMediaClearQueue: () -> Unit,
    onRadioRefresh: () -> Unit,
    onRadioStationToggle: (RadioStation) -> Unit,
    onRadioCustomUrlChange: (String) -> Unit,
    onRadioPlayCustom: () -> Unit,
    onAlertsRefresh: () -> Unit,
    onAlertProviderFormChange: (AlertProviderForm) -> Unit,
    onAlertProviderTest: () -> Unit,
    onAlertProviderSave: () -> Unit,
    onAlertAudioFormChange: (AlertAudioForm) -> Unit,
    onAlertAudioSave: () -> Unit,
    onAlertMediaSelected: (String, String) -> Unit,
    onAlertMediaReset: (String) -> Unit,
    onAlertMediaResetAll: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val colors = LocalProAudioColors.current
    var pendingAlertMediaKind by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val alertMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val kind = pendingAlertMediaKind
        pendingAlertMediaKind = null
        if (uri != null && kind != null) {
            onAlertMediaSelected(kind, uri.toString())
        }
    }

    ProAudioShell {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 2.dp,
                end = 16.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ProAudioHeader(
                    trailing = {
                        when (state) {
                            is PlayerSessionState.Connected -> StatusBadge(
                                text = stringResource(R.string.player_connected),
                                state = StatusBadgeState.Online,
                            )
                            is PlayerSessionState.Connecting -> StatusBadge(
                                text = stringResource(R.string.player_connecting_short),
                                state = StatusBadgeState.Connecting,
                            )
                            is PlayerSessionState.Offline,
                            is PlayerSessionState.Failed -> StatusBadge(
                                text = stringResource(R.string.player_offline),
                                state = StatusBadgeState.Offline,
                            )
                            PlayerSessionState.NoSelection -> Unit
                        }
                    },
                )
            }

            item {
                OutlinedButton(
                    onClick = onBack,
                ) {
                    Text(
                        text = "‹  " + stringResource(R.string.back_to_players),
                    )
                }
            }

            item {
                PrimaryNavigation(
                    selected = section,
                    onSelected = onSectionSelected,
                    alertActive = (state as? PlayerSessionState.Connected)
                        ?.status
                        ?.priority
                        ?.active == true,
                )
            }

            if (actionError != null) {
                item {
                    ActionError(message = actionError)
                }
            }

            when {
                state is PlayerSessionState.Connected && section == AppSection.Player -> {
                    item(key = "player-section") {
                        ConnectedState(
                            state = state,
                            pendingAction = pendingAction,
                            masterMuteBusy = masterMuteBusy,
                            masterVolumeOverride = masterVolumeOverride,
                            meterState = meterState,
                            outputState = outputState,
                            onAction = onAction,
                            onMasterVolumeChange = onMasterVolumeChange,
                            onMasterMuteChange = onMasterMuteChange,
                            onOutputRefresh = onOutputRefresh,
                            onOutputSelect = onOutputSelect,
                        )
                    }
                }

                state is PlayerSessionState.Connected && section == AppSection.Media -> {
                    mediaSection(
                        state = mediaState,
                        connected = state,
                        onRefresh = onMediaRefresh,
                        onRefreshLibrary = onMediaRefreshLibrary,
                        onLibraryQueryChange = onMediaLibraryQueryChange,
                        onPlayLibraryPath = onMediaPlayLibraryPath,
                        onLoadPlaylist = onMediaLoadPlaylist,
                        onPlayQueueItem = onMediaPlayQueueItem,
                        onRemoveQueueItem = onMediaRemoveQueueItem,
                        onClearQueue = onMediaClearQueue,
                    )
                }

                state is PlayerSessionState.Connected && section == AppSection.Radio -> {
                    radioSection(
                        state = radioState,
                        connected = state,
                        onRefresh = onRadioRefresh,
                        onStationToggle = onRadioStationToggle,
                        onCustomUrlChange = onRadioCustomUrlChange,
                        onPlayCustom = onRadioPlayCustom,
                    )
                }

                state is PlayerSessionState.Connected && section == AppSection.Alerts -> {
                    alertsSection(
                        state = alertsState,
                        connected = state,
                        onRefresh = onAlertsRefresh,
                        onProviderFormChange = onAlertProviderFormChange,
                        onProviderTest = onAlertProviderTest,
                        onProviderSave = onAlertProviderSave,
                        onAudioFormChange = onAlertAudioFormChange,
                        onAudioSave = onAlertAudioSave,
                        onPickMedia = { kind ->
                            pendingAlertMediaKind = kind
                            alertMediaPicker.launch(
                                arrayOf("audio/mpeg", "audio/mp3"),
                            )
                        },
                        onResetMedia = onAlertMediaReset,
                        onResetAllMedia = onAlertMediaResetAll,
                    )
                }

                state is PlayerSessionState.Connected -> {
                    item(key = "section-placeholder") {
                        SectionPlaceholder(section)
                    }
                }

                state is PlayerSessionState.Connecting -> {
                    item(key = "connecting") {
                        ConnectingState(state)
                    }
                }

                state is PlayerSessionState.Offline -> {
                    item(key = "offline") {
                        MessageState(
                            title = stringResource(R.string.player_offline),
                            message = stringResource(R.string.player_offline_support),
                        )
                    }
                }

                state is PlayerSessionState.Failed -> {
                    item(key = "failed") {
                        MessageState(
                            title = state.displayName,
                            message = state.message,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectingState(
    state: PlayerSessionState.Connecting,
) {
    val colors = LocalProAudioColors.current

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
            Text(
                text = state.displayName,
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.player_connecting),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ConnectedState(
    state: PlayerSessionState.Connected,
    pendingAction: PlayerAction?,
    masterMuteBusy: Boolean,
    masterVolumeOverride: Double?,
    meterState: StateFlow<MeterState>,
    outputState: OutputUiState,
    onAction: (PlayerAction) -> Unit,
    onMasterVolumeChange: (Double) -> Unit,
    onMasterMuteChange: (Boolean) -> Unit,
    onOutputRefresh: () -> Unit,
    onOutputSelect: (AudioOutputDescriptor) -> Unit,
) {
    val colors = LocalProAudioColors.current
    val player = state.status.player

    BoxWithConstraints {
        val wide = maxWidth >= 700.dp

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProAudioPanel(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.weight(1.08f),
                        ) {
                            PlayerArtwork(
                                source = player.source,
                            )
                        }
                        Box(
                            modifier = Modifier.weight(0.92f),
                        ) {
                            PlayerMeta(
                                player = player,
                                pendingAction = pendingAction,
                                onAction = onAction,
                            )
                        }
                    }
                } else {
                    Column {
                        PlayerArtwork(
                            source = player.source,
                        )
                        PlayerMeta(
                            player = player,
                            pendingAction = pendingAction,
                            onAction = onAction,
                        )
                    }
                }
            }

            AudioMetersCard(
                state = meterState,
            )

            MasterOutputControl(
                master = state.status.master,
                muteBusy = masterMuteBusy,
                volumeOverride = masterVolumeOverride,
                onVolumeChange = onMasterVolumeChange,
                onMuteChange = onMasterMuteChange,
            )

            if ("audio_outputs" in state.capabilities.features) {
                AudioOutputCard(
                    state = outputState,
                    onRefresh = onOutputRefresh,
                    onSelect = onOutputSelect,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = state.endpoint.host.let {
                        if (':' in it) "IPv6" else "IPv4"
                    } + " · API v" + state.capabilities.apiMajorVersion,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                )
                state.capabilities.eventTransport?.let {
                    Text(
                        text = it.uppercase(),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerArtwork(
    source: String,
) {
    val colors = LocalProAudioColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF111722),
                        Color(0xFF080B10),
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
                            colors.blueAccent.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        center = Offset(160f, 90f),
                        radius = 500f,
                    ),
                ),
        )

        Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(26.dp),
            color = Color.White.copy(alpha = 0.035f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.10f),
            ),
        ) {
            Text(
                text = "♪",
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                color = colors.textMuted,
                style = MaterialTheme.typography.headlineLarge,
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            shape = RoundedCornerShape(9.dp),
            color = Color.Black.copy(alpha = 0.48f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.10f),
            ),
        ) {
            Text(
                text = source.ifBlank { stringResource(R.string.player_no_source) },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerMeta(
    player: PlayerState,
    pendingAction: PlayerAction?,
    onAction: (PlayerAction) -> Unit,
) {
    val colors = LocalProAudioColors.current
    val progress by animateFloatAsState(
        targetValue = player.progressPercent.coerceIn(0, 100).toFloat(),
        label = "playerProgress",
    )

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        SectionLabel(
            text = stringResource(R.string.now_playing).uppercase(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = player.title.ifBlank { stringResource(R.string.no_active_stream) },
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = player.artist
                .ifBlank { player.album }
                .ifBlank { "ProAudio Player" },
            color = colors.textSoft,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(22.dp))

        PlayerProgress(
            progress = progress,
            positionSeconds = player.positionSeconds,
            durationSeconds = player.durationSeconds,
        )

        Spacer(modifier = Modifier.height(20.dp))

        TransportControls(
            state = player.state,
            controls = player.controls,
            pendingAction = pendingAction,
            onAction = onAction,
        )
    }
}

@Composable
private fun PlayerProgress(
    progress: Float,
    positionSeconds: Double?,
    durationSeconds: Double?,
) {
    val colors = LocalProAudioColors.current

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(colors.surfaceInset, CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress / 100f)
                    .height(7.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFF7B4A), colors.accentStrong),
                        ),
                        CircleShape,
                    ),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatClock(positionSeconds),
                color = colors.textMuted,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = formatClock(durationSeconds),
                color = colors.textMuted,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun TransportControls(
    state: String,
    controls: PlayerControls,
    pendingAction: PlayerAction?,
    onAction: (PlayerAction) -> Unit,
) {
    val playing = state == "playing"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(
            label = stringResource(R.string.player_previous),
            type = TransportIcon.Previous,
            action = PlayerAction.Previous,
            enabled = controls.previous,
            pending = pendingAction == PlayerAction.Previous,
            onClick = onAction,
        )
        Spacer(modifier = Modifier.size(10.dp))
        TransportButton(
            label = stringResource(R.string.player_stop),
            type = TransportIcon.Stop,
            action = PlayerAction.Stop,
            enabled = controls.stop,
            pending = pendingAction == PlayerAction.Stop,
            onClick = onAction,
        )
        Spacer(modifier = Modifier.size(10.dp))
        TransportButton(
            label = stringResource(
                if (playing) R.string.player_pause else R.string.player_play,
            ),
            type = if (playing) TransportIcon.Pause else TransportIcon.Play,
            action = if (playing) PlayerAction.Pause else PlayerAction.Play,
            enabled = if (playing) controls.pause else controls.play,
            pending = pendingAction == if (playing) PlayerAction.Pause else PlayerAction.Play,
            primary = true,
            onClick = onAction,
        )
        Spacer(modifier = Modifier.size(10.dp))
        TransportButton(
            label = stringResource(R.string.player_next),
            type = TransportIcon.Next,
            action = PlayerAction.Next,
            enabled = controls.next,
            pending = pendingAction == PlayerAction.Next,
            onClick = onAction,
        )
    }
}

@Composable
private fun TransportButton(
    label: String,
    type: TransportIcon,
    action: PlayerAction,
    enabled: Boolean,
    pending: Boolean,
    primary: Boolean = false,
    onClick: (PlayerAction) -> Unit,
) {
    val colors = LocalProAudioColors.current
    val size = if (primary) 62.dp else 48.dp
    val background = if (primary) {
        Brush.linearGradient(listOf(Color(0xFFFF7843), colors.accentStrong))
    } else {
        Brush.linearGradient(listOf(colors.surfaceRaised, colors.surfaceRaised))
    }

    Surface(
        onClick = { onClick(action) },
        enabled = enabled && !pending,
        modifier = Modifier
            .size(size)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        shape = RoundedCornerShape(if (primary) 14.dp else 12.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .background(
                    if (enabled && !pending) Color.Transparent else colors.canvas.copy(alpha = 0.48f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            TransportGlyph(
                type = type,
                color = if (primary) Color.White else colors.textSoft,
                modifier = Modifier.size(if (primary) 28.dp else 21.dp),
            )
        }
    }
}

private enum class TransportIcon {
    Previous,
    Stop,
    Play,
    Pause,
    Next,
}

@Composable
private fun TransportGlyph(
    type: TransportIcon,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        when (type) {
            TransportIcon.Play -> {
                val path = Path().apply {
                    moveTo(size.width * 0.34f, size.height * 0.22f)
                    lineTo(size.width * 0.76f, size.height * 0.50f)
                    lineTo(size.width * 0.34f, size.height * 0.78f)
                    close()
                }
                drawPath(path, color)
            }
            TransportIcon.Pause -> {
                drawRect(
                    color,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.22f),
                    size = Size(size.width * 0.14f, size.height * 0.56f),
                )
                drawRect(
                    color,
                    topLeft = Offset(size.width * 0.58f, size.height * 0.22f),
                    size = Size(size.width * 0.14f, size.height * 0.56f),
                )
            }
            TransportIcon.Stop -> {
                drawRect(
                    color,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.28f),
                    size = Size(size.width * 0.44f, size.height * 0.44f),
                    style = Stroke(stroke),
                )
            }
            TransportIcon.Previous,
            TransportIcon.Next -> {
                val reverse = type == TransportIcon.Previous
                val barX = if (reverse) size.width * 0.25f else size.width * 0.75f
                drawLine(
                    color,
                    start = Offset(barX, size.height * 0.22f),
                    end = Offset(barX, size.height * 0.78f),
                    strokeWidth = stroke,
                )
                val path = Path()
                if (reverse) {
                    path.moveTo(size.width * 0.70f, size.height * 0.25f)
                    path.lineTo(size.width * 0.36f, size.height * 0.50f)
                    path.lineTo(size.width * 0.70f, size.height * 0.75f)
                } else {
                    path.moveTo(size.width * 0.30f, size.height * 0.25f)
                    path.lineTo(size.width * 0.64f, size.height * 0.50f)
                    path.lineTo(size.width * 0.30f, size.height * 0.75f)
                }
                path.close()
                drawPath(path, color)
            }
        }
    }
}

@Composable
private fun MasterOutputControl(
    master: AudioLevelState,
    muteBusy: Boolean,
    volumeOverride: Double?,
    onVolumeChange: (Double) -> Unit,
    onMuteChange: (Boolean) -> Unit,
) {
    val colors = LocalProAudioColors.current
    val displayedPercent = (volumeOverride ?: master.volumePercent)
        .coerceIn(0.0, 100.0)

    ProAudioPanel(
        modifier = Modifier.fillMaxWidth(),
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
                Column {
                    SectionLabel(text = stringResource(R.string.master_output))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.player_volume),
                        color = colors.text,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Text(
                    text = if (master.muted) {
                        stringResource(R.string.player_muted)
                    } else {
                        formatVolume(displayedPercent)
                    },
                    color = if (master.muted) colors.danger else colors.text,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Slider(
                value = displayedPercent.toFloat(),
                onValueChange = { value ->
                    onVolumeChange(value.toDouble())
                },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = colors.text,
                    activeTrackColor = colors.blueAccent,
                    inactiveTrackColor = colors.surfaceInset,
                ),
            )

            OutlinedButton(
                onClick = { onMuteChange(!master.muted) },
                enabled = !muteBusy && volumeOverride == null && master.db != null,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (master.muted) {
                        colors.danger.copy(alpha = 0.55f)
                    } else {
                        colors.borderStrong
                    },
                ),
            ) {
                Text(
                    text = if (master.muted) {
                        stringResource(R.string.master_unmute)
                    } else {
                        stringResource(R.string.master_mute)
                    },
                    color = if (master.muted) colors.danger else colors.textSoft,
                )
            }
        }
    }
}

@Composable
private fun SectionPlaceholder(
    section: AppSection,
) {
    val colors = LocalProAudioColors.current
    val title = when (section) {
        AppSection.Player -> stringResource(R.string.nav_player)
        AppSection.Media -> stringResource(R.string.nav_media)
        AppSection.Radio -> stringResource(R.string.nav_radio)
        AppSection.Alerts -> stringResource(R.string.nav_alerts)
    }
    val message = when (section) {
        AppSection.Player,
        AppSection.Media -> ""
        AppSection.Radio -> stringResource(R.string.radio_port_pending)
        AppSection.Alerts -> stringResource(R.string.alerts_port_pending)
    }

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel(text = title.uppercase())
            Text(
                text = title,
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
            )
            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ActionError(
    message: String,
) {
    val colors = LocalProAudioColors.current
    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(colors.danger, CircleShape),
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = colors.textSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MessageState(
    title: String,
    message: String,
) {
    val colors = LocalProAudioColors.current
    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = message,
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun formatVolume(percent: Double): String =
    String.format(Locale.ROOT, "%.1f%%", percent)

private fun formatClock(seconds: Double?): String {
    if (seconds == null || !seconds.isFinite() || seconds < 0) {
        return "—:—"
    }
    val total = seconds.toInt()
    val minutes = total / 60
    val remainder = total % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, remainder)
}
