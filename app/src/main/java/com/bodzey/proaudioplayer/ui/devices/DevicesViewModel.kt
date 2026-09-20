package com.bodzey.proaudioplayer.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.device.AvailableDevice
import com.bodzey.proaudioplayer.core.device.DeviceRepository
import com.bodzey.proaudioplayer.core.discovery.demo.DemoDiscoveryController
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import kotlinx.coroutines.flow.StateFlow

class DevicesViewModel(
    deviceRepository: DeviceRepository,
    private val demoDiscoveryController: DemoDiscoveryController,
    private val sessionRepository: PlayerSessionRepository,
) : ViewModel() {

    val devices: StateFlow<List<AvailableDevice>> = deviceRepository.devices
    val demoEnabled: StateFlow<Boolean> = demoDiscoveryController.enabled

    fun setDemoEnabled(enabled: Boolean) {
        demoDiscoveryController.setEnabled(enabled)
    }

    fun selectDevice(deviceId: DeviceId) {
        sessionRepository.select(deviceId)
    }

    companion object {
        fun factory(
            deviceRepository: DeviceRepository,
            demoDiscoveryController: DemoDiscoveryController,
            sessionRepository: PlayerSessionRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DevicesViewModel(
                        deviceRepository = deviceRepository,
                        demoDiscoveryController = demoDiscoveryController,
                        sessionRepository = sessionRepository,
                    )
                }
            }
    }
}
