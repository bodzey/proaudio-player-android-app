package com.bodzey.proaudioplayer.ui.meter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bodzey.proaudioplayer.core.meter.MeterRepository
import com.bodzey.proaudioplayer.core.meter.MeterState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn

class MeterViewModel(
    meterRepository: MeterRepository,
) : ViewModel() {

    private val buffer = MeterRenderBuffer()
    private val renderPulse = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val status: StateFlow<MeterStreamStatus> = flow {
        var previous = MeterStreamStatus.Inactive

        meterRepository.states().collect { state ->
            val next = when (state) {
                MeterState.Inactive -> {
                    buffer.reset()
                    MeterStreamStatus.Inactive
                }
                MeterState.Connecting -> {
                    buffer.reset()
                    MeterStreamStatus.Connecting
                }
                is MeterState.Failed -> {
                    buffer.reset()
                    MeterStreamStatus.Failed
                }
                is MeterState.Active -> {
                    if (buffer.write(state.frame)) {
                        renderPulse.tryEmit(Unit)
                    }
                    MeterStreamStatus.Active
                }
            }

            if (next != previous) {
                previous = next
                emit(next)
            }
        }
    }
        .onCompletion {
            buffer.reset()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 0,
                replayExpirationMillis = 0,
            ),
            initialValue = MeterStreamStatus.Inactive,
        )

    val renderSource = MeterRenderSource(
        status = status,
        buffer = buffer,
        renderPulse = renderPulse.asSharedFlow(),
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
