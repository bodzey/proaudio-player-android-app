package com.bodzey.proaudioplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bodzey.proaudioplayer.ui.ProAudioPlayerApp
import com.bodzey.proaudioplayer.ui.alerts.AlertsViewModel
import com.bodzey.proaudioplayer.ui.devices.DevicesViewModel
import com.bodzey.proaudioplayer.ui.player.PlayerViewModel
import com.bodzey.proaudioplayer.ui.radio.RadioViewModel
import com.bodzey.proaudioplayer.ui.theme.ProAudioPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ProAudioPlayerApplication).container

        setContent {
            val devicesViewModel: DevicesViewModel = viewModel(
                factory = DevicesViewModel.factory(
                    deviceRepository = container.deviceRepository,
                    demoDiscoveryController = container.demoDiscoveryController,
                    sessionRepository = container.playerSessionRepository,
                ),
            )
            val playerViewModel: PlayerViewModel = viewModel(
                factory = PlayerViewModel.factory(
                    sessionRepository = container.playerSessionRepository,
                ),
            )
            val radioViewModel: RadioViewModel = viewModel(
                factory = RadioViewModel.factory(
                    sessionRepository = container.playerSessionRepository,
                ),
            )
            val alertsViewModel: AlertsViewModel = viewModel(
                factory = AlertsViewModel.factory(
                    sessionRepository = container.playerSessionRepository,
                    mediaImporter = container.alertMediaImporter,
                ),
            )

            ProAudioPlayerTheme {
                ProAudioPlayerApp(
                    devicesViewModel = devicesViewModel,
                    playerViewModel = playerViewModel,
                    radioViewModel = radioViewModel,
                    alertsViewModel = alertsViewModel,
                    showDemoControls = BuildConfig.DEBUG,
                )
            }
        }
    }
}
