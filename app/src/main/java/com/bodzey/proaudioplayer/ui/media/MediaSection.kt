package com.bodzey.proaudioplayer.ui.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.bodzey.proaudioplayer.core.api.QueueItem
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import com.bodzey.proaudioplayer.ui.components.ProAudioPanel
import com.bodzey.proaudioplayer.ui.components.SectionLabel
import com.bodzey.proaudioplayer.ui.theme.LocalProAudioColors

fun LazyListScope.mediaSection(
    state: MediaUiState,
    connected: PlayerSessionState.Connected,
    onRefresh: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onLibraryQueryChange: (String) -> Unit,
    onPlayLibraryPath: (String) -> Unit,
    onLoadPlaylist: (String) -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
) {
    val features = connected.capabilities.features
    val blocked = connected.status.priority.blocking
    val interactionEnabled = !blocked && state.busyAction == null

    item(key = "media-header") {
        MediaHeader(
            loading = state.loading,
            onRefresh = onRefresh,
        )
    }

    if (blocked) {
        item(key = "media-blocked") {
            MediaNotice(
                text = stringResource(R.string.media_blocked),
                error = true,
            )
        }
    }

    state.error?.let { message ->
        item(key = "media-error") {
            MediaNotice(
                text = message,
                error = true,
            )
        }
    }

    if (state.loading &&
        state.library.isEmpty() &&
        state.playlists.isEmpty() &&
        state.queue.isEmpty()
    ) {
        item(key = "media-loading") {
            MediaLoadingCard()
        }
    }

    if ("queue" in features) {
        item(key = "media-queue-header") {
            MediaSectionHeader(
                eyebrow = stringResource(R.string.media_queue_eyebrow),
                title = stringResource(R.string.media_queue_title),
                actionLabel = if (state.queue.isNotEmpty()) {
                    stringResource(R.string.media_queue_clear)
                } else {
                    null
                },
                actionEnabled = interactionEnabled && state.queue.isNotEmpty(),
                onAction = onClearQueue,
            )
        }

        if (!state.loading && state.queue.isEmpty()) {
            item(key = "media-queue-empty") {
                MediaEmptyCard(
                    text = stringResource(R.string.media_queue_empty),
                )
            }
        }

        items(
            items = state.queue,
            key = { item -> "queue-" + item.position + "-" + item.file },
            contentType = { "media-queue-item" },
        ) { item ->
            QueueItemCard(
                item = item,
                enabled = interactionEnabled,
                busyAction = state.busyAction,
                onPlay = { onPlayQueueItem(item.position) },
                onRemove = { onRemoveQueueItem(item.position) },
            )
        }
    }

    if ("playlists" in features) {
        item(key = "media-playlists-header") {
            MediaSectionHeader(
                eyebrow = stringResource(R.string.media_playlists_eyebrow),
                title = stringResource(R.string.media_playlists_title),
            )
        }

        if (!state.loading && state.playlists.isEmpty()) {
            item(key = "media-playlists-empty") {
                MediaEmptyCard(
                    text = stringResource(R.string.media_playlists_empty),
                )
            }
        }

        items(
            items = state.playlists,
            key = { name -> "playlist-" + name },
            contentType = { "media-playlist" },
        ) { name ->
            MediaActionRow(
                title = name,
                subtitle = null,
                actionLabel = stringResource(R.string.media_playlist_load),
                enabled = interactionEnabled,
                busy = state.busyAction == "playlist:$name",
                onClick = { onLoadPlaylist(name) },
                description = stringResource(R.string.media_playlist_load) + ": " + name,
            )
        }
    }

    if ("library" in features) {
        item(key = "media-library-header") {
            LibraryHeader(
                query = state.libraryQuery,
                refreshing = state.libraryRefreshing,
                onQueryChange = onLibraryQueryChange,
                onRefresh = onRefreshLibrary,
            )
        }

        val filtered = if (state.libraryQuery.isBlank()) {
            state.library
        } else {
            state.library.filter { path ->
                path.contains(state.libraryQuery.trim(), ignoreCase = true)
            }
        }
        val visible = filtered.take(MAX_VISIBLE_LIBRARY_ITEMS)

        if (!state.loading && filtered.isEmpty()) {
            item(key = "media-library-empty") {
                MediaEmptyCard(
                    text = stringResource(R.string.media_library_empty),
                )
            }
        }

        if (filtered.size > visible.size) {
            item(key = "media-library-limit") {
                MediaNotice(
                    text = stringResource(
                        R.string.media_library_limit,
                        visible.size,
                        filtered.size,
                    ),
                    error = false,
                )
            }
        }

        items(
            items = visible,
            key = { path -> "library-" + path },
            contentType = { "media-library-item" },
        ) { path ->
            MediaActionRow(
                title = path.substringAfterLast('/').ifBlank { path },
                subtitle = path,
                actionLabel = stringResource(R.string.media_library_play),
                enabled = interactionEnabled,
                busy = state.busyAction == "library:$path",
                onClick = { onPlayLibraryPath(path) },
                description = stringResource(R.string.media_library_play) + ": " + path,
            )
        }
    }
}

@Composable
private fun MediaHeader(
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = LocalProAudioColors.current

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(text = stringResource(R.string.media_eyebrow))
            Text(
                text = stringResource(R.string.media_title),
                color = colors.text,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.media_description),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = onRefresh,
                enabled = !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .heightIn(max = 18.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )
                }
                Text(stringResource(R.string.media_refresh))
            }
        }
    }
}

@Composable
private fun MediaSectionHeader(
    eyebrow: String,
    title: String,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    val colors = LocalProAudioColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionLabel(text = eyebrow)
            Text(
                text = title,
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (actionLabel != null) {
            OutlinedButton(
                onClick = onAction,
                enabled = actionEnabled,
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun QueueItemCard(
    item: QueueItem,
    enabled: Boolean,
    busyAction: String?,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalProAudioColors.current
    val playBusy = busyAction == "queue-play:" + item.position

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = item.position.toString(),
                    color = colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = item.title.ifBlank {
                            item.file.substringAfterLast('/')
                        },
                        color = colors.text,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.artist.isNotBlank()) {
                        Text(
                            text = item.artist,
                            color = colors.textSoft,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = item.file,
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPlay,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    if (playBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.heightIn(max = 18.dp),
                            color = colors.text,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.media_queue_play))
                    }
                }
                OutlinedButton(
                    onClick = onRemove,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.media_queue_remove))
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    query: String,
    refreshing: Boolean,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = LocalProAudioColors.current

    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(text = stringResource(R.string.media_library_eyebrow))
            Text(
                text = stringResource(R.string.media_library_title),
                color = colors.text,
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text(stringResource(R.string.media_library_search))
                },
                placeholder = {
                    Text(stringResource(R.string.media_library_search_hint))
                },
            )
            OutlinedButton(
                onClick = onRefresh,
                enabled = !refreshing,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.heightIn(max = 18.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )
                } else {
                    Text(stringResource(R.string.media_library_update))
                }
            }
        }
    }
}

@Composable
private fun MediaActionRow(
    title: String,
    subtitle: String?,
    actionLabel: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    description: String,
) {
    val colors = LocalProAudioColors.current

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it != title }?.let { value ->
                    Text(
                        text = value,
                        color = colors.textMuted,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.heightIn(max = 20.dp),
                    strokeWidth = 2.dp,
                    color = colors.accent,
                )
            } else {
                Text(
                    text = actionLabel,
                    color = colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun MediaLoadingCard() {
    val colors = LocalProAudioColors.current
    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.heightIn(max = 22.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
            Text(
                text = stringResource(R.string.media_loading),
                color = colors.textSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MediaEmptyCard(
    text: String,
) {
    val colors = LocalProAudioColors.current
    ProAudioPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = colors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MediaNotice(
    text: String,
    error: Boolean,
) {
    val colors = LocalProAudioColors.current
    val accent = if (error) colors.danger else colors.blueAccent

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            color = accent,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val MAX_VISIBLE_LIBRARY_ITEMS = 200
