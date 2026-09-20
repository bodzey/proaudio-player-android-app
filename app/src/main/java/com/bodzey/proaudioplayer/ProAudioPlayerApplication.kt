package com.bodzey.proaudioplayer

import android.app.Application
import android.content.Context
import com.bodzey.proaudioplayer.core.device.DeviceRegistry
import com.bodzey.proaudioplayer.core.discovery.CombinedDeviceDiscoverySource
import com.bodzey.proaudioplayer.core.discovery.demo.DemoDeviceDiscoverySource
import com.bodzey.proaudioplayer.core.discovery.demo.DemoDiscoveryController
import com.bodzey.proaudioplayer.core.discovery.nsd.AndroidNsdDiscoverySource

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

    private val discoverySource = CombinedDeviceDiscoverySource(
        AndroidNsdDiscoverySource(context),
        DemoDeviceDiscoverySource(demoDiscoveryController),
    )

    val deviceRegistry = DeviceRegistry(discoverySource)
}
