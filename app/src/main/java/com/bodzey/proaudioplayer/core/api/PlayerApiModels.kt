package com.bodzey.proaudioplayer.core.api

data class ApiHealth(
    val status: String,
    val apiMajorVersion: Int,
)

data class ApiCapabilities(
    val apiMajorVersion: Int,
    val eventTransport: String?,
    val features: Set<String>,
)

data class PlayerControls(
    val play: Boolean,
    val pause: Boolean,
    val stop: Boolean,
    val next: Boolean,
    val previous: Boolean,
)

data class PlayerState(
    val source: String,
    val backend: String,
    val state: String,
    val title: String,
    val artist: String,
    val album: String,
    val positionSeconds: Double?,
    val durationSeconds: Double?,
    val progressPercent: Int,
    val controls: PlayerControls,
    val artUrl: String? = null,
)

data class AudioLevelState(
    val volumePercent: Double,
    val muted: Boolean,
    val db: Double? = null,
)

data class PriorityState(
    val mode: String,
    val active: Boolean,
    val blocking: Boolean,
    val duckOnlyDuringAnnouncement: Boolean,
    val minuteSilenceActive: Boolean,
    val matchedUids: List<Long>,
    val lastSuccessAt: String?,
    val lastChangeAt: String?,
    val lastError: String?,
)

data class MpdState(
    val isStream: Boolean,
    val streamUrl: String?,
)

data class ActiveSource(
    val key: String,
    val active: Boolean,
    val type: String,
    val application: String,
    val media: String,
)

data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
    val homepage: String?,
    val favicon: String?,
    val tags: List<String>,
    val codec: String?,
    val bitrate: Int?,
    val votes: Long,
)

data class PlayerStatus(
    val name: String,
    val audioTopologyRevision: Long?,
    val master: AudioLevelState,
    val music: AudioLevelState,
    val priority: PriorityState,
    val mpd: MpdState,
    val player: PlayerState,
    val sources: List<ActiveSource> = emptyList(),
)
