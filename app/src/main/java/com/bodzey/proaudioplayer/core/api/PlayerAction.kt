package com.bodzey.proaudioplayer.core.api

enum class PlayerAction(
    val wireValue: String,
) {
    Play("play"),
    Pause("pause"),
    Stop("stop"),
    Next("next"),
    Previous("prev"),
}
