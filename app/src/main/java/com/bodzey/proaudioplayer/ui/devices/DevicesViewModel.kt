package com.bodzey.proaudioplayer.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.device.DeviceListEntry
import com.bodzey.proaudioplayer.core.device.DeviceRepository
import com.bodzey.proaudioplayer.core.discovery.demo.DemoDiscoveryController
import kotlinx.coroutines.flow.StateFlow

class DevicesViewModel(
    deviceRepository: DeviceRepository,
    private val demoDiscoveryController: DemoDiscoveryController,
) : ViewModel() {

    val devices: StateFlow<List<DeviceListEntry>> = deviceRepository.listEntries
    val demoEnabled: StateFlow<Boolean> = demoDiscoveryController.enabled

    fun setDemoEnabled(enabled: Boolean) {
        demoDiscoveryController.setEnabled(enabled)
    }

    companion object {
        fun factory(
            deviceRepository: DeviceRepository,
            demoDiscoveryController: DemoDiscoveryController,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DevicesViewModel(
                        deviceRepository = deviceRepository,
                        demoDiscoveryController = demoDiscoveryController,
                    )
                }
            }
    }
}
