package com.bodzey.proaudioplayer.ui.mixer

import com.bodzey.proaudioplayer.core.api.MixerState
import com.bodzey.proaudioplayer.core.api.MixerTarget
import com.bodzey.proaudioplayer.core.model.DeviceId

data class MixerUiState(
    val deviceId: DeviceId? = null,
    val loading: Boolean = false,
    val mixer: MixerState? = null,
    val pendingTarget: MixerTarget? = null,
    val error: String? = null,
)
