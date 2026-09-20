package com.bodzey.proaudioplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bodzey.proaudioplayer.ui.alerts.AlertsViewModel
import com.bodzey.proaudioplayer.ui.devices.DevicesScreen
import com.bodzey.proaudioplayer.ui.devices.DevicesViewModel
import com.bodzey.proaudioplayer.ui.media.MediaViewModel
import com.bodzey.proaudioplayer.ui.meter.MeterViewModel
import com.bodzey.proaudioplayer.ui.mixer.MixerViewModel
import com.bodzey.proaudioplayer.ui.output.OutputViewModel
import com.bodzey.proaudioplayer.ui.player.PlayerScreen
import com.bodzey.proaudioplayer.ui.player.PlayerViewModel
import com.bodzey.proaudioplayer.ui.radio.RadioViewModel

@Composable
fun ProAudioPlayerApp(
    devicesViewModel: DevicesViewModel,
    playerViewModel: PlayerViewModel,
    meterViewModel: MeterViewModel,
    mixerViewModel: MixerViewModel,
    mediaViewModel: MediaViewModel,
    outputViewModel: OutputViewModel,
    radioViewModel: RadioViewModel,
    alertsViewModel: AlertsViewModel,
    showDemoControls: Boolean,
) {
    val selectedDeviceId = playerViewModel.selectedDeviceId.collectAsStateWithLifecycle()

    BackHandler(enabled = selectedDeviceId.value != null) {
        playerViewModel.close()
    }

    if (selectedDeviceId.value == null) {
        val devices = devicesViewModel.devices.collectAsStateWithLifecycle()
        val demoEnabled = devicesViewModel.demoEnabled.collectAsStateWithLifecycle()
        val forgettingDeviceId =
            devicesViewModel.forgettingDeviceId.collectAsStateWithLifecycle()

        DevicesScreen(
            devices = devices.value,
            demoEnabled = demoEnabled.value,
            showDemoControls = showDemoControls,
            forgettingDeviceId = forgettingDeviceId.value,
            onDemoEnabledChange = devicesViewModel::setDemoEnabled,
            onDeviceSelected = playerViewModel::rememberSelectedDevice,
            onDeviceForget = devicesViewModel::forgetDevice,
        )
    } else {
        val sessionState = playerViewModel.state.collectAsStateWithLifecycle()
        val pendingAction = playerViewModel.pendingAction.collectAsStateWithLifecycle()
        val actionError = playerViewModel.actionError.collectAsStateWithLifecycle()
        val section = playerViewModel.section.collectAsStateWithLifecycle()
        val masterMuteBusy = playerViewModel.masterMuteBusy.collectAsStateWithLifecycle()
        val masterVolumeOverride =
            playerViewModel.masterVolumeOverride.collectAsStateWithLifecycle()
        val mixerState = mixerViewModel.uiState.collectAsStateWithLifecycle()
        val mediaState = mediaViewModel.uiState.collectAsStateWithLifecycle()
        val outputState = outputViewModel.uiState.collectAsStateWithLifecycle()
        val radioState = radioViewModel.uiState.collectAsStateWithLifecycle()
        val alertsState = alertsViewModel.uiState.collectAsStateWithLifecycle()
        val connectedState = sessionState.value as?
            com.bodzey.proaudioplayer.core.session.PlayerSessionState.Connected
        val connected = connectedState != null
        val audioTopologyRevision = connectedState
            ?.status
            ?.audioTopologyRevision

        LaunchedEffect(
            section.value,
            selectedDeviceId.value,
            connected,
            audioTopologyRevision,
        ) {
            when {
                section.value == AppSection.Player && connectedState != null -> {
                    if ("audio_outputs" in connectedState.capabilities.features) {
                        outputViewModel.ensureLoaded(
                            force = audioTopologyRevision != null,
                        )
                    }
                    if ("audio_mixer" in connectedState.capabilities.features) {
                        mixerViewModel.ensureLoaded()
                    }
                }
                section.value == AppSection.Media && connected ->
                    mediaViewModel.ensureLoaded()
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
            meterSource = meterViewModel.renderSource,
            mixerState = mixerState.value,
            outputState = outputState.value,
            mediaState = mediaState.value,
            onSectionSelected = playerViewModel::selectSection,
            onAction = playerViewModel::performAction,
            onMasterVolumeChange = playerViewModel::setMasterVolume,
            onMasterMuteChange = playerViewModel::setMasterMuted,
            onOutputRefresh = outputViewModel::refresh,
            onOutputSelect = outputViewModel::select,
            onMixerRefresh = mixerViewModel::refresh,
            onMixerLevelChange = mixerViewModel::setLevel,
            onMixerMuteChange = mixerViewModel::setMuted,
            onMediaRefresh = mediaViewModel::refresh,
            onMediaRefreshLibrary = mediaViewModel::refreshLibrary,
            onMediaLibraryQueryChange = mediaViewModel::setLibraryQuery,
            onMediaPlayLibraryPath = mediaViewModel::playLibraryPath,
            onMediaLoadPlaylist = mediaViewModel::loadPlaylist,
            onMediaPlayQueueItem = mediaViewModel::playQueueItem,
            onMediaRemoveQueueItem = mediaViewModel::removeQueueItem,
            onMediaClearQueue = mediaViewModel::clearQueue,
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
