package com.bodzey.proaudioplayer.ui.output

import com.bodzey.proaudioplayer.core.api.AudioOutputDescriptor
import com.bodzey.proaudioplayer.core.model.DeviceId

data class OutputUiState(
    val deviceId: DeviceId? = null,
    val loading: Boolean = false,
    val outputs: List<AudioOutputDescriptor> = emptyList(),
    val pendingOutputId: String? = null,
    val error: String? = null,
)
