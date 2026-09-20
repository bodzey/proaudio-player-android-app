package com.bodzey.proaudioplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.api.PlayerAction
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val sessionRepository: PlayerSessionRepository,
) : ViewModel() {
    val selectedDeviceId = sessionRepository.selectedDeviceId
    val state: StateFlow<PlayerSessionState> = sessionRepository.state

    private val _pendingAction = MutableStateFlow<PlayerAction?>(null)
    val pendingAction: StateFlow<PlayerAction?> = _pendingAction.asStateFlow()

    fun performAction(action: PlayerAction) {
        if (_pendingAction.value != null) {
            return
        }
        viewModelScope.launch {
            _pendingAction.value = action
            try {
                sessionRepository.performAction(action)
            } finally {
                _pendingAction.value = null
            }
        }
    }

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
