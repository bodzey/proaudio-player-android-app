package com.bodzey.proaudioplayer.core.meter

import com.bodzey.proaudioplayer.core.api.MeterFrame

sealed interface MeterState {
    data object Inactive : MeterState
    data object Connecting : MeterState
    data class Active(
        val frame: MeterFrame,
    ) : MeterState
    data class Failed(
        val message: String,
    ) : MeterState
}
