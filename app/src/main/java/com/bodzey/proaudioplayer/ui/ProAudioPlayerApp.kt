package com.bodzey.proaudioplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bodzey.proaudioplayer.ui.alerts.AlertsViewModel
import com.bodzey.proaudioplayer.ui.devices.DevicesScreen
import com.bodzey.proaudioplayer.ui.devices.DevicesViewModel
import com.bodzey.proaudioplayer.ui.meter.MeterViewModel
import com.bodzey.proaudioplayer.ui.player.PlayerScreen
import com.bodzey.proaudioplayer.ui.player.PlayerViewModel
import com.bodzey.proaudioplayer.ui.radio.RadioViewModel

@Composable
fun ProAudioPlayerApp(
    devicesViewModel: DevicesViewModel,
    playerViewModel: PlayerViewModel,
    meterViewModel: MeterViewModel,
    radioViewModel: RadioViewModel,
    alertsViewModel: AlertsViewModel,
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
            onDeviceSelected = playerViewModel::rememberSelectedDevice,
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
        val alertsState = alertsViewModel.uiState.collectAsStateWithLifecycle()
        val connected = sessionState.value is com.bodzey.proaudioplayer.core.session.PlayerSessionState.Connected

        LaunchedEffect(
            section.value,
            selectedDeviceId.value,
            connected,
        ) {
            when {
                section.value == AppSection.Radio && connected ->
                    radioViewModel.ensureLoaded()
                section.value == AppSection.Alerts && connected ->
                    alertsViewModel.ensureLoaded()
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
            alertsState = alertsState.value,
            meterState = meterViewModel.state,
            onSectionSelected = playerViewModel::selectSection,
            onAction = playerViewModel::performAction,
            onMasterVolumeChange = playerViewModel::setMasterVolume,
            onMasterMuteChange = playerViewModel::setMasterMuted,
            onRadioRefresh = radioViewModel::refresh,
            onRadioStationToggle = radioViewModel::toggleStation,
            onRadioCustomUrlChange = radioViewModel::setCustomUrl,
            onRadioPlayCustom = radioViewModel::playCustomStream,
            onAlertsRefresh = alertsViewModel::refresh,
            onAlertProviderFormChange = alertsViewModel::updateProviderForm,
            onAlertProviderTest = alertsViewModel::testProvider,
            onAlertProviderSave = alertsViewModel::saveProvider,
            onAlertAudioFormChange = alertsViewModel::updateAudioForm,
            onAlertAudioSave = alertsViewModel::saveAudio,
            onAlertMediaSelected = alertsViewModel::uploadMedia,
            onAlertMediaReset = alertsViewModel::resetMedia,
            onAlertMediaResetAll = alertsViewModel::resetAllMedia,
            onBack = playerViewModel::close,
        )
    }
}
