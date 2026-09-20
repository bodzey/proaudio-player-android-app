package com.bodzey.proaudioplayer.ui.radio

import com.bodzey.proaudioplayer.core.api.RadioStation
import com.bodzey.proaudioplayer.core.model.DeviceId

data class RadioFeedback(
    val message: String,
    val isError: Boolean,
)

data class RadioUiState(
    val deviceId: DeviceId? = null,
    val stations: List<RadioStation> = emptyList(),
    val loading: Boolean = false,
    val loadError: String? = null,
    val pendingUrl: String? = null,
    val customUrl: String = "",
    val feedback: RadioFeedback? = null,
)
