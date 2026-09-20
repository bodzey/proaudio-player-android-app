package com.bodzey.proaudioplayer.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.device.DeviceListEntry
import com.bodzey.proaudioplayer.core.device.DeviceRepository
import com.bodzey.proaudioplayer.core.discovery.demo.DemoDiscoveryController
import com.bodzey.proaudioplayer.core.model.DeviceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DevicesViewModel(
    private val deviceRepository: DeviceRepository,
    private val demoDiscoveryController: DemoDiscoveryController,
) : ViewModel() {

    val devices: StateFlow<List<DeviceListEntry>> = deviceRepository.listEntries
    val demoEnabled: StateFlow<Boolean> = demoDiscoveryController.enabled

    private val _forgettingDeviceId = MutableStateFlow<DeviceId?>(null)
    val forgettingDeviceId: StateFlow<DeviceId?> = _forgettingDeviceId.asStateFlow()

    fun setDemoEnabled(enabled: Boolean) {
        demoDiscoveryController.setEnabled(enabled)
    }

    fun forgetDevice(deviceId: DeviceId) {
        if (_forgettingDeviceId.value != null) {
            return
        }

        viewModelScope.launch {
            _forgettingDeviceId.value = deviceId
            try {
                deviceRepository.forgetKnownDevice(deviceId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the row visible so the user can retry.
            } finally {
                if (_forgettingDeviceId.value == deviceId) {
                    _forgettingDeviceId.value = null
                }
            }
        }
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
