package com.bodzey.proaudioplayer.core.session

import com.bodzey.proaudioplayer.core.api.ApiHealth
import com.bodzey.proaudioplayer.core.api.PlayerApiClient
import com.bodzey.proaudioplayer.core.device.AvailableDevice
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class ResolvedEndpoint(
    val endpoint: DeviceEndpoint,
    val health: ApiHealth,
)

class EndpointResolver(
    private val apiClient: PlayerApiClient,
) {
    suspend fun resolve(device: AvailableDevice): ResolvedEndpoint =
        supervisorScope {
            val endpoints = device.endpoints.sortedWith(endpointPreference())
            val results = Channel<Result<ResolvedEndpoint>>(capacity = endpoints.size)

            val jobs = endpoints.map { endpoint ->
                launch {
                    val result = try {
                        val health = apiClient.health(endpoint)
                        requireCompatibleHealth(
                            health = health,
                            expectedApiMajorVersion = device.apiMajorVersion,
                        )
                        Result.success(ResolvedEndpoint(endpoint, health))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                    results.send(result)
                }
            }

            val failures = mutableListOf<Throwable>()
            repeat(endpoints.size) {
                val result = results.receive()
                result.getOrNull()?.let { resolved ->
                    jobs.forEach { job -> job.cancel() }
                    return@supervisorScope resolved
                }
                result.exceptionOrNull()?.let(failures::add)
            }

            throw EndpointResolutionException(
                deviceName = device.displayName,
                failures = failures,
            )
        }

    private fun requireCompatibleHealth(
        health: ApiHealth,
        expectedApiMajorVersion: Int,
    ) {
        if (health.status != "ok") {
            throw ApiCompatibilityException("Player health status is '" + health.status + "'")
        }
        if (health.apiMajorVersion != expectedApiMajorVersion) {
            throw ApiCompatibilityException(
                "Discovered API v" + expectedApiMajorVersion +
                    " but endpoint reports v" + health.apiMajorVersion,
            )
        }
    }

    private fun endpointPreference(): Comparator<DeviceEndpoint> =
        compareBy<DeviceEndpoint>(
            { endpoint -> if (endpoint.transport == DeviceEndpoint.Transport.HTTPS) 0 else 1 },
            { endpoint -> if (':' in endpoint.host) 1 else 0 },
            { endpoint -> endpoint.host },
            { endpoint -> endpoint.port },
        )
}

class ApiCompatibilityException(
    message: String,
) : IllegalStateException(message)

class EndpointResolutionException(
    deviceName: String,
    val failures: List<Throwable>,
) : IllegalStateException(
    buildString {
        append("No compatible endpoint is reachable for ")
        append(deviceName)
        failures.firstOrNull()?.message?.takeIf { it.isNotBlank() }?.let { detail ->
            append(": ")
            append(detail)
        }
    },
)
