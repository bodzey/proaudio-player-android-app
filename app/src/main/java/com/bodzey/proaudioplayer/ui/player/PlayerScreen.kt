package com.bodzey.proaudioplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.session.PlayerSessionState

@Composable
fun PlayerScreen(
    state: PlayerSessionState,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.back))
                }
            }

            when (state) {
                PlayerSessionState.NoSelection -> Unit

                is PlayerSessionState.Connecting -> ConnectingState(state)

                is PlayerSessionState.Connected -> ConnectedState(state)

                is PlayerSessionState.Offline -> MessageState(
                    title = stringResource(R.string.player_offline),
                    message = stringResource(R.string.player_offline_support),
                )

                is PlayerSessionState.Failed -> MessageState(
                    title = state.displayName,
                    message = state.message,
                )
            }
        }
    }
}

@Composable
private fun ConnectingState(
    state: PlayerSessionState.Connecting,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = state.displayName,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.player_connecting),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectedState(
    state: PlayerSessionState.Connected,
) {
    val player = state.status.player

    Text(
        text = state.displayName,
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.player_connected),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )

    Spacer(modifier = Modifier.height(20.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = player.title.ifBlank { player.source },
                style = MaterialTheme.typography.titleLarge,
            )
            if (player.artist.isNotBlank()) {
                Text(
                    text = player.artist,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (player.album.isNotBlank()) {
                Text(
                    text = player.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            StatusRow(
                label = stringResource(R.string.player_source),
                value = player.source,
            )
            StatusRow(
                label = stringResource(R.string.player_state),
                value = player.state,
            )
            StatusRow(
                label = stringResource(R.string.player_volume),
                value = if (state.status.master.muted) {
                    stringResource(R.string.player_muted)
                } else {
                    formatVolume(state.status.master.volumePercent)
                },
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusRow(
                label = stringResource(R.string.player_endpoint),
                value = state.endpoint.displayValue(),
            )
            StatusRow(
                label = stringResource(R.string.player_api),
                value = "v" + state.capabilities.apiMajorVersion,
            )
            state.capabilities.eventTransport?.let { transport ->
                StatusRow(
                    label = stringResource(R.string.player_events),
                    value = transport.uppercase(),
                )
            }
        }
    }
}

@Composable
private fun MessageState(
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(end = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

private fun DeviceEndpoint.displayValue(): String {
    val formattedHost = if (':' in host) "[" + host + "]" else host
    return formattedHost + ":" + port
}

private fun formatVolume(percent: Double): String =
    String.format(Locale.ROOT, "%.1f%%", percent)
