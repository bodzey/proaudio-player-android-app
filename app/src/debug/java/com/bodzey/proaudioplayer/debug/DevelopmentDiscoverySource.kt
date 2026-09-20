package com.bodzey.proaudioplayer.debug

import com.bodzey.proaudioplayer.core.discovery.DeviceDiscoverySource
import com.bodzey.proaudioplayer.core.discovery.demo.DemoDeviceDiscoverySource
import com.bodzey.proaudioplayer.core.discovery.demo.DemoDiscoveryController

fun createDevelopmentDiscoverySource(
    controller: DemoDiscoveryController,
): DeviceDiscoverySource? =
    DemoDeviceDiscoverySource(controller)
