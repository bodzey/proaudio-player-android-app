package com.bodzey.proaudioplayer.core.api

data class AlertProviderSettings(
    val endpoint: String,
    val locationUid: Long,
    val locationType: String,
    val pollIntervalSeconds: Double,
    val requestTimeoutSeconds: Double,
    val rateLimitBackoffSeconds: Double,
    val clearConfirmations: Int,
    val tokenConfigured: Boolean,
)

data class AlertAudioSettings(
    val airRaidAlertsEnabled: Boolean,
    val duckDb: Double,
    val duckFadeSeconds: Double,
    val restoreFadeSeconds: Double,
    val alertVolumePercent: Double,
    val defaultRestoreVolumePercent: Double,
    val minuteSilenceVolumePercent: Double,
    val minuteSilenceEnabled: Boolean,
    val minuteSilenceStartTime: String,
    val minuteSilenceTimezone: String,
    val minuteSilenceCatchUpSeconds: Long,
    val minuteSilenceMusicFadeSeconds: Double,
    val alertRepeatIntervalMinutes: Long,
    val duckOnlyDuringAnnouncement: Boolean,
    val sampleRateMode: String,
    val sampleRate: Int,
    val allowedSampleRates: List<Int>,
)

data class AlertMediaFile(
    val kind: String,
    val label: String,
    val fileName: String,
    val configured: Boolean,
    val sizeBytes: Long?,
    val modifiedUnixSeconds: Long?,
    val maxSizeBytes: Long,
    val contentType: String,
)

data class AlertMediaCatalog(
    val items: List<AlertMediaFile>,
    val acceptedContentTypes: Set<String>,
    val maxSizeBytes: Long,
)
