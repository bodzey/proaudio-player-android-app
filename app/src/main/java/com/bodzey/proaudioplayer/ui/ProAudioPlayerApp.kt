package com.bodzey.proaudioplayer.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bodzey.proaudioplayer.ui.devices.DevicesScreen
import com.bodzey.proaudioplayer.ui.devices.DevicesViewModel
import com.bodzey.proaudioplayer.ui.player.PlayerScreen
import com.bodzey.proaudioplayer.ui.player.PlayerViewModel

@Composable
fun ProAudioPlayerApp(
    devicesViewModel: DevicesViewModel,
    playerViewModel: PlayerViewModel,
    showDemoControls: Boolean,
) {
    val selectedDeviceId = playerViewModel.selectedDeviceId.collectAsStateWithLifecycle()

    if (selectedDeviceId.value == null) {
        val devices = devicesViewModel.devices.collectAsStateWithLifecycle()
        val demoEnabled = devicesViewModel.demoEnabled.collectAsStateWithLifecycle()

        DevicesScreen(
            devices = devices.value,
            demoEnabled = demoEnabled.value,
            showDemoControls = showDemoControls,
            onDemoEnabledChange = devicesViewModel::setDemoEnabled,
            onDeviceSelected = devicesViewModel::selectDevice,
        )
    } else {
        val sessionState = playerViewModel.state.collectAsStateWithLifecycle()
        val pendingAction = playerViewModel.pendingAction.collectAsStateWithLifecycle()
        val actionError = playerViewModel.actionError.collectAsStateWithLifecycle()
        val section = playerViewModel.section.collectAsStateWithLifecycle()
        val masterMuteBusy = playerViewModel.masterMuteBusy.collectAsStateWithLifecycle()
        val masterVolumeOverride =
            playerViewModel.masterVolumeOverride.collectAsStateWithLifecycle()
        PlayerScreen(
            state = sessionState.value,
            section = section.value,
            pendingAction = pendingAction.value,
            masterMuteBusy = masterMuteBusy.value,
            masterVolumeOverride = masterVolumeOverride.value,
            actionError = actionError.value,
            onSectionSelected = playerViewModel::selectSection,
            onAction = playerViewModel::performAction,
            onMasterVolumeChange = playerViewModel::setMasterVolume,
            onMasterMuteChange = playerViewModel::setMasterMuted,
            onBack = playerViewModel::close,
        )
    }
}
