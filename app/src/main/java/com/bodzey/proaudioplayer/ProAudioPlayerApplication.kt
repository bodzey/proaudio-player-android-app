package com.bodzey.proaudioplayer

import android.app.Application
import android.content.Context
import com.bodzey.proaudioplayer.core.device.DeviceRegistry
import com.bodzey.proaudioplayer.core.device.DeviceRepository
import com.bodzey.proaudioplayer.core.discovery.CombinedDeviceDiscoverySource
import com.bodzey.proaudioplayer.core.discovery.DeviceDiscoverySource
import com.bodzey.proaudioplayer.core.discovery.demo.DemoDiscoveryController
import com.bodzey.proaudioplayer.core.discovery.nsd.AndroidNsdDiscoverySource
import com.bodzey.proaudioplayer.core.session.EndpointResolver
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import com.bodzey.proaudioplayer.data.api.OkHttpPlayerApiClient
import com.bodzey.proaudioplayer.debug.createDevelopmentDiscoverySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default,
    )

    val demoDiscoveryController = DemoDiscoveryController()

    private val discoverySources: Array<DeviceDiscoverySource> = listOfNotNull(
        AndroidNsdDiscoverySource(context),
        createDevelopmentDiscoverySource(demoDiscoveryController),
    ).toTypedArray()

    private val deviceRegistry = DeviceRegistry(
        CombinedDeviceDiscoverySource(*discoverySources),
    )

    val deviceRepository = DeviceRepository(
        deviceRegistry = deviceRegistry,
        scope = applicationScope,
    )

    private val apiClient = OkHttpPlayerApiClient()
    private val endpointResolver = EndpointResolver(apiClient)

    val playerSessionRepository = PlayerSessionRepository(
        deviceRepository = deviceRepository,
        endpointResolver = endpointResolver,
        apiClient = apiClient,
        scope = applicationScope,
    )
}
