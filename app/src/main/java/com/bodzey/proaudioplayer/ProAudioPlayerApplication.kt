package com.bodzey.proaudioplayer

import android.app.Application
import android.content.Context
import com.bodzey.proaudioplayer.core.device.DeviceRegistry
import com.bodzey.proaudioplayer.core.discovery.CombinedDeviceDiscoverySource
import com.bodzey.proaudioplayer.core.discovery.DeviceDiscoverySource
import com.bodzey.proaudioplayer.core.discovery.demo.DemoDiscoveryController
import com.bodzey.proaudioplayer.core.discovery.nsd.AndroidNsdDiscoverySource
import com.bodzey.proaudioplayer.debug.createDevelopmentDiscoverySource

class ProAudioPlayerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(
    context: Context,
) {
    val demoDiscoveryController = DemoDiscoveryController()

    private val discoverySources: Array<DeviceDiscoverySource> = listOfNotNull(
        AndroidNsdDiscoverySource(context),
        createDevelopmentDiscoverySource(demoDiscoveryController),
    ).toTypedArray()

    val deviceRegistry = DeviceRegistry(
        CombinedDeviceDiscoverySource(*discoverySources),
    )
}
