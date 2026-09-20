package com.bodzey.proaudioplayer.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bodzey.proaudioplayer.audio.AudioRelayService
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
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
    val context = LocalContext.current
    val relayActive = AudioRelayService.active.collectAsStateWithLifecycle()
    var pendingRelayEndpoint by remember { mutableStateOf<DeviceEndpoint?>(null) }
    var relayError by remember { mutableStateOf<String?>(null) }

    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val endpoint = pendingRelayEndpoint
        pendingRelayEndpoint = null
        val data = result.data
        if (endpoint != null && result.resultCode == Activity.RESULT_OK && data != null) {
            runCatching {
                AudioRelayService.start(
                    context = context,
                    resultCode = result.resultCode,
                    resultData = data,
                    endpoint = endpoint,
                )
            }.onFailure { error ->
                relayError = error.message ?: "Не вдалося запустити передачу аудіо"
            }
        } else if (endpoint != null) {
            relayError = "Дозвіл на захоплення аудіо не надано"
        }
    }

    fun launchProjection(endpoint: DeviceEndpoint) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            relayError = "Передавання системного аудіо потребує Android 10 або новішої версії"
            return
        }
        relayError = null
        pendingRelayEndpoint = endpoint
        val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val endpoint = pendingRelayEndpoint
        if (granted && endpoint != null) {
            launchProjection(endpoint)
        } else {
            pendingRelayEndpoint = null
            relayError = "Доступ до запису аудіо не надано"
        }
    }

    fun startAudioRelay(endpoint: DeviceEndpoint) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            relayError = "Передавання системного аудіо потребує Android 10 або новішої версії"
            return
        }
        pendingRelayEndpoint = endpoint
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchProjection(endpoint)
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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
            audioRelayActive = relayActive.value,
            audioRelayError = relayError,
            pendingAction = pendingAction.value,
            masterMuteBusy = masterMuteBusy.value,
            masterVolumeOverride = masterVolumeOverride.value,
            actionError = actionError.value,
            radioState = radioState.value,
            alertsState = alertsState.value,
            meterSource = meterViewModel.renderSource,
            playerTimeline = playerViewModel.timeline,
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
            onAudioRelayStart = ::startAudioRelay,
            onAudioRelayStop = {
                relayError = null
                AudioRelayService.stop(context)
            },
            onBack = playerViewModel::close,
        )
    }
}
