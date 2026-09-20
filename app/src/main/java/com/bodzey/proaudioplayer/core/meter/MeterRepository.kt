package com.bodzey.proaudioplayer.core.meter

import com.bodzey.proaudioplayer.core.api.PlayerApiClient
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.session.PlayerSessionRepository
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive

@OptIn(ExperimentalCoroutinesApi::class)
class MeterRepository(
    private val sessionRepository: PlayerSessionRepository,
    private val apiClient: PlayerApiClient,
) {
    fun states(): Flow<MeterState> =
        sessionRepository.state
            .map(::meterConnection)
            .distinctUntilChanged()
            .flatMapLatest { connection ->
                if (connection == null) {
                    flowOf(MeterState.Inactive)
                } else {
                    connectedMeterStates(connection.endpoint)
                }
            }

    private fun connectedMeterStates(
        endpoint: DeviceEndpoint,
    ): Flow<MeterState> = flow {
        while (currentCoroutineContext().isActive) {
            emit(MeterState.Connecting)
            try {
                apiClient.meterEvents(endpoint).collect { frame ->
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

    private fun meterConnection(
        session: PlayerSessionState,
    ): MeterConnection? {
        val connected = session as? PlayerSessionState.Connected
            ?: return null
        if ("meters" !in connected.capabilities.features) {
            return null
        }
        return MeterConnection(
            endpoint = connected.endpoint,
        )
    }

    private data class MeterConnection(
        val endpoint: DeviceEndpoint,
    )

    private companion object {
        const val RECONNECT_DELAY_MILLIS = 1_000L
    }
}
