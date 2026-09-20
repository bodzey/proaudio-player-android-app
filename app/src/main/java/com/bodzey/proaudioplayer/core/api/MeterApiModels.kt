package com.bodzey.proaudioplayer.core.api

data class StereoMeterValues(
    val left: Double,
    val right: Double,
)

data class StereoMeterLevel(
    val peakDb: StereoMeterValues,
    val rmsDb: StereoMeterValues,
    val clipLeft: Boolean,
    val clipRight: Boolean,
    val available: Boolean,
)

data class MeterFrame(
    val sequence: Long,
    val sampleRate: Int,
    val intervalMillis: Long,
    val master: StereoMeterLevel,
    val music: StereoMeterLevel,
    val alert: StereoMeterLevel,
)
