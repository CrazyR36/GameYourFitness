package com.gameyourfitness.app.data.workout

import com.gameyourfitness.app.data.auth.SessionStore
import com.gameyourfitness.app.data.character.PostgrestHttpException
import com.gameyourfitness.app.domain.workout.LogWorkoutResult
import com.gameyourfitness.app.domain.workout.StrengthWorkout
import com.gameyourfitness.app.domain.workout.WorkoutRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erfasst ein Krafttraining echt über die serverseitige RPC. Den Access-Token holt
 * das Repository aus dem [SessionStore] (data→data, unterhalb der UI) — die Domäne
 * kennt keine Tokens. Netzfehler bleiben als [LogWorkoutResult.NetworkError] erhalten
 * (Eingaben werden im UI nicht verworfen); HTTP-/Serverfehler ergeben [Failed].
 */
@Singleton
class PostgrestWorkoutRepository @Inject constructor(
    private val workoutApi: WorkoutApi,
    private val sessionStore: SessionStore
) : WorkoutRepository {
    override suspend fun logStrengthWorkout(workout: StrengthWorkout): LogWorkoutResult {
        val session = sessionStore.load() ?: return LogWorkoutResult.Failed
        return try {
            val response = workoutApi.logStrengthWorkout(session.accessToken, workout.toRequest())
            LogWorkoutResult.Success(response.xpAwarded)
        } catch (_: IOException) {
            LogWorkoutResult.NetworkError
        } catch (_: PostgrestHttpException) {
            LogWorkoutResult.Failed
        }
    }
}
