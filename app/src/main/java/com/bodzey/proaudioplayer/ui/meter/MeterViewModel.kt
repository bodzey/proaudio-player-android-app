package com.bodzey.proaudioplayer.ui.meter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.meter.MeterRepository
import com.bodzey.proaudioplayer.core.meter.MeterState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MeterViewModel(
    meterRepository: MeterRepository,
) : ViewModel() {
    val state: StateFlow<MeterState> = meterRepository
        .states()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 0,
                replayExpirationMillis = 0,
            ),
            initialValue = MeterState.Inactive,
        )

    companion object {
        fun factory(
            meterRepository: MeterRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MeterViewModel(meterRepository)
                }
            }
    }
}
