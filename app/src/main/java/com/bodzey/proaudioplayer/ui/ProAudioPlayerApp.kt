package com.bodzey.proaudioplayer.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bodzey.proaudioplayer.ui.devices.DevicesScreen
import com.bodzey.proaudioplayer.ui.devices.DevicesViewModel

@Composable
fun ProAudioPlayerApp(
    viewModel: DevicesViewModel,
    showDemoControls: Boolean,
) {
    val devices = viewModel.devices.collectAsStateWithLifecycle()
    val demoEnabled = viewModel.demoEnabled.collectAsStateWithLifecycle()

    DevicesScreen(
        devices = devices.value,
        demoEnabled = demoEnabled.value,
        showDemoControls = showDemoControls,
        onDemoEnabledChange = viewModel::setDemoEnabled,
    )
}
