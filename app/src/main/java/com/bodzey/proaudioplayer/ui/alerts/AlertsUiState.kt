package com.bodzey.proaudioplayer.ui.alerts

import com.bodzey.proaudioplayer.core.api.AlertAudioSettings
import com.bodzey.proaudioplayer.core.api.AlertMediaCatalog
import com.bodzey.proaudioplayer.core.api.AlertProviderSettings
import com.bodzey.proaudioplayer.core.model.DeviceId

data class AlertsUiState(
    val deviceId: DeviceId? = null,
    val loading: Boolean = false,
    val provider: AlertProviderSettings? = null,
    val audio: AlertAudioSettings? = null,
    val media: AlertMediaCatalog? = null,
    val loadError: String? = null,
)
