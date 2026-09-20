package com.bodzey.proaudioplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bodzey.proaudioplayer.ui.devices.DevicesScreen
import com.bodzey.proaudioplayer.ui.devices.DevicesViewModel
import com.bodzey.proaudioplayer.ui.player.PlayerScreen
import com.bodzey.proaudioplayer.ui.player.PlayerViewModel
import com.bodzey.proaudioplayer.ui.radio.RadioViewModel

@Composable
fun ProAudioPlayerApp(
    devicesViewModel: DevicesViewModel,
    playerViewModel: PlayerViewModel,
    radioViewModel: RadioViewModel,
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
        val radioState = radioViewModel.uiState.collectAsStateWithLifecycle()
        val connected = sessionState.value is com.bodzey.proaudioplayer.core.session.PlayerSessionState.Connected

        LaunchedEffect(
            section.value,
            selectedDeviceId.value,
            connected,
        ) {
            if (section.value == AppSection.Radio && connected) {
                radioViewModel.ensureLoaded()
            }
        }

        PlayerScreen(
            state = sessionState.value,
            section = section.value,
            pendingAction = pendingAction.value,
            masterMuteBusy = masterMuteBusy.value,
            masterVolumeOverride = masterVolumeOverride.value,
            actionError = actionError.value,
            radioState = radioState.value,
            onSectionSelected = playerViewModel::selectSection,
            onAction = playerViewModel::performAction,
            onMasterVolumeChange = playerViewModel::setMasterVolume,
            onMasterMuteChange = playerViewModel::setMasterMuted,
            onRadioRefresh = radioViewModel::refresh,
            onRadioStationToggle = radioViewModel::toggleStation,
            onRadioCustomUrlChange = radioViewModel::setCustomUrl,
            onRadioPlayCustom = radioViewModel::playCustomStream,
            onBack = playerViewModel::close,
        )
    }
}
