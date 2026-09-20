package com.bodzey.proaudioplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bodzey.proaudioplayer.ui.ProAudioPlayerApp
import com.bodzey.proaudioplayer.ui.theme.ProAudioPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ProAudioPlayerTheme {
                ProAudioPlayerApp()
            }
        }
    }
}
