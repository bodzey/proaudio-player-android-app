package com.bodzey.proaudioplayer.core.api

enum class MixerTarget(
    val wireValue: String,
) {
    Music("music"),
    Alert("alert"),
}

data class MixerState(
    val music: AudioLevelState,
    val alert: AudioLevelState,
    val master: AudioLevelState,
)
