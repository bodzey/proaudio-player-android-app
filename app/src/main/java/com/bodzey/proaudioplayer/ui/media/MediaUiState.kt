package com.bodzey.proaudioplayer.ui.media

import com.bodzey.proaudioplayer.core.api.QueueItem
import com.bodzey.proaudioplayer.core.model.DeviceId

data class MediaUiState(
    val deviceId: DeviceId? = null,
    val loading: Boolean = false,
    val libraryRefreshing: Boolean = false,
    val library: List<String> = emptyList(),
    val playlists: List<String> = emptyList(),
    val queue: List<QueueItem> = emptyList(),
    val libraryQuery: String = "",
    val busyAction: String? = null,
    val error: String? = null,
)
