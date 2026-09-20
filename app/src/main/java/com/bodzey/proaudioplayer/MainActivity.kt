package com.bodzey.proaudioplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bodzey.proaudioplayer.ui.ProAudioPlayerApp
import com.bodzey.proaudioplayer.ui.devices.DevicesViewModel
import com.bodzey.proaudioplayer.ui.theme.ProAudioPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ProAudioPlayerApplication).container

        setContent {
            val devicesViewModel: DevicesViewModel = viewModel(
                factory = DevicesViewModel.factory(
                    deviceRegistry = container.deviceRegistry,
                    demoDiscoveryController = container.demoDiscoveryController,
                ),
            )

            ProAudioPlayerTheme {
                ProAudioPlayerApp(
                    viewModel = devicesViewModel,
                    showDemoControls = BuildConfig.DEBUG,
                )
            }
        }
    }
}
