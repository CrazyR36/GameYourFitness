package com.gameyourfitness.app.workout

import com.gameyourfitness.app.data.workout.LogWorkoutRequest
import com.gameyourfitness.app.data.workout.LogWorkoutResponse
import com.gameyourfitness.app.data.workout.WorkoutApi
import com.gameyourfitness.app.data.workout.WorkoutHttpApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * E2E-Doppel für das Erfassen: delegiert standardmäßig an den ECHTEN PostgREST-RPC-Client
 * (`WorkoutHttpApi`) — der Happy-Path läuft also gegen das echte Backend (Server berechnet
 * die EP). Nur der Offline-Fall wird über [forceNetworkError] erzwungen (OkHttp wirft bei
 * fehlendem Netz ebenfalls eine [IOException]).
 */
@Singleton
class TestWorkoutApi @Inject constructor(private val real: WorkoutHttpApi) : WorkoutApi {
    override suspend fun logStrengthWorkout(accessToken: String, request: LogWorkoutRequest): LogWorkoutResponse {
        if (forceNetworkError) throw IOException("E2E: simulierter Netzfehler")
        return real.logStrengthWorkout(accessToken, request)
    }

    companion object {
        @Volatile
        var forceNetworkError: Boolean = false

        fun reset() {
            forceNetworkError = false
        }
    }
}
