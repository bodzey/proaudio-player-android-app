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
)

data class AudioLevelState(
    val volumePercent: Double,
    val muted: Boolean,
    val db: Double? = null,
)

data class PriorityState(
    val active: Boolean,
    val blocking: Boolean,
)

data class MpdState(
    val isStream: Boolean,
    val streamUrl: String?,
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
    val master: AudioLevelState,
    val music: AudioLevelState,
    val priority: PriorityState,
    val mpd: MpdState,
    val player: PlayerState,
)
