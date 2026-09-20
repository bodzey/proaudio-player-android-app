package com.bodzey.proaudioplayer.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.device.AvailableDevice
import com.bodzey.proaudioplayer.core.device.DeviceRegistry
import com.bodzey.proaudioplayer.core.discovery.demo.DemoDiscoveryController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DevicesViewModel(
    deviceRegistry: DeviceRegistry,
    private val demoDiscoveryController: DemoDiscoveryController,
) : ViewModel() {

    val devices: StateFlow<List<AvailableDevice>> = deviceRegistry
        .devices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            initialValue = emptyList(),
        )

    val demoEnabled: StateFlow<Boolean> = demoDiscoveryController.enabled

    fun setDemoEnabled(enabled: Boolean) {
        demoDiscoveryController.setEnabled(enabled)
    }

    companion object {
        fun factory(
            deviceRegistry: DeviceRegistry,
            demoDiscoveryController: DemoDiscoveryController,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DevicesViewModel(
                        deviceRegistry = deviceRegistry,
                        demoDiscoveryController = demoDiscoveryController,
                    )
                }
            }
    }
}
