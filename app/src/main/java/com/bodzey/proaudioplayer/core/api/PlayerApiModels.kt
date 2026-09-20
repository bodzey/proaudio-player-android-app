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
)

data class PlayerStatus(
    val name: String,
    val master: AudioLevelState,
    val music: AudioLevelState,
    val player: PlayerState,
)
