package com.bodzey.proaudioplayer.core.api

data class AudioOutputCapabilities(
    val sampleFormat: String?,
    val sampleRate: Int?,
    val channels: Int?,
    val channelMap: List<String>,
    val alsaDevice: Int?,
    val deviceApi: String?,
    val deviceBus: String?,
)

data class AudioOutputDescriptor(
    val id: String,
    val name: String,
    val state: String,
    val deviceClass: String,
    val alsaCard: Int?,
    val capabilities: AudioOutputCapabilities,
    val selected: Boolean,
    val available: Boolean,
)

data class QueueItem(
    val position: Int,
    val file: String,
    val title: String,
    val artist: String,
    val album: String,
)
