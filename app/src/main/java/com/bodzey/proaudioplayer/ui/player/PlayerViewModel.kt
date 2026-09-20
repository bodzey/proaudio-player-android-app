package com.bodzey.proaudioplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel(
    private val sessionRepository: PlayerSessionRepository,
) : ViewModel() {
    val selectedDeviceId = sessionRepository.selectedDeviceId
    val state: StateFlow<PlayerSessionState> = sessionRepository.state

    fun close() {
        sessionRepository.clearSelection()
    }

    companion object {
        fun factory(
            sessionRepository: PlayerSessionRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlayerViewModel(sessionRepository)
                }
            }
    }
}
