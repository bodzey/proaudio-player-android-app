package com.bodzey.proaudioplayer.ui.alerts

import com.bodzey.proaudioplayer.core.api.AlertAudioSettings
import com.bodzey.proaudioplayer.core.api.AlertMediaCatalog
import com.bodzey.proaudioplayer.core.api.AlertProviderSettings
import com.bodzey.proaudioplayer.core.model.DeviceId

data class AlertProviderForm(
    val endpoint: String,
    val locationUid: String,
    val locationType: String,
    val token: String = "",
    val pollIntervalSeconds: String,
    val requestTimeoutSeconds: String,
    val rateLimitBackoffSeconds: String,
    val clearConfirmations: String,
)

data class AlertAudioForm(
    val airRaidAlertsEnabled: Boolean,
    val minuteSilenceEnabled: Boolean,
    val duckDb: String,
    val duckFadeSeconds: String,
    val restoreFadeSeconds: String,
    val alertVolumePercent: String,
    val defaultRestoreVolumePercent: String,
    val minuteSilenceVolumePercent: String,
    val minuteSilenceStartTime: String,
    val minuteSilenceTimezone: String,
    val minuteSilenceCatchUpSeconds: String,
    val minuteSilenceMusicFadeSeconds: String,
    val alertRepeatIntervalMinutes: String,
    val duckOnlyDuringAnnouncement: Boolean,
)

data class AlertsMessage(
    val text: String,
    val isError: Boolean,
)

sealed interface AlertsBusyAction {
    data object ProviderSave : AlertsBusyAction
    data object ProviderTest : AlertsBusyAction
    data object AudioSave : AlertsBusyAction
    data class MediaUpload(val kind: String) : AlertsBusyAction
    data class MediaReset(val kind: String) : AlertsBusyAction
    data object MediaResetAll : AlertsBusyAction
}

data class AlertsUiState(
    val deviceId: DeviceId? = null,
    val loading: Boolean = false,
    val provider: AlertProviderSettings? = null,
    val providerForm: AlertProviderForm? = null,
    val providerDirty: Boolean = false,
    val providerMessage: AlertsMessage? = null,
    val audio: AlertAudioSettings? = null,
    val audioForm: AlertAudioForm? = null,
    val audioDirty: Boolean = false,
    val audioMessage: AlertsMessage? = null,
    val media: AlertMediaCatalog? = null,
    val mediaMessage: AlertsMessage? = null,
    val busyAction: AlertsBusyAction? = null,
    val loadError: String? = null,
)
