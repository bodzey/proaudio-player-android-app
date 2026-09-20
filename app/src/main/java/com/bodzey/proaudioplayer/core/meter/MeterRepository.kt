package com.bodzey.proaudioplayer.core.meter

import com.bodzey.proaudioplayer.core.api.PlayerApiClient
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive

@OptIn(ExperimentalCoroutinesApi::class)
class MeterRepository(
    private val sessionRepository: PlayerSessionRepository,
    private val apiClient: PlayerApiClient,
) {
    fun states(): Flow<MeterState> =
        sessionRepository.state.flatMapLatest { session ->
            val connected = session as? PlayerSessionState.Connected
                ?: return@flatMapLatest flowOf(MeterState.Inactive)
            if ("meters" !in connected.capabilities.features) {
                return@flatMapLatest flowOf(MeterState.Inactive)
            }
            connectedMeterStates(connected)
        }

    private fun connectedMeterStates(
        connected: PlayerSessionState.Connected,
    ): Flow<MeterState> = flow {
        while (currentCoroutineContext().isActive) {
            emit(MeterState.Connecting)
            try {
                apiClient.meterEvents(connected.endpoint).collect { frame ->
                    emit(MeterState.Active(frame))
                }
                throw IllegalStateException("Meter event stream closed")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emit(
                    MeterState.Failed(
                        message = error.message ?: "Meter stream unavailable",
                    ),
                )
                delay(RECONNECT_DELAY_MILLIS)
            }
        }
    }

    private companion object {
        const val RECONNECT_DELAY_MILLIS = 1_000L
    }
}
