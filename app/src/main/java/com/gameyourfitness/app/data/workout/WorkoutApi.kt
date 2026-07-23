package com.gameyourfitness.app.data.workout

import com.gameyourfitness.app.domain.workout.StrengthWorkout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Schmaler PostgREST-Ausschnitt für Slice #4: ein RPC-Aufruf der serverseitigen
 * Funktion `log_strength_workout`. Eigenes Interface, damit E2E-Tests genau diesen
 * Zugriff ersetzen können (Offline-Fall) — analog zu `CharacterApi`/`GoTrueApi`.
 */
interface WorkoutApi {
    /**
     * Ruft `POST /rest/v1/rpc/log_strength_workout` mit dem [accessToken] auf. Der
     * Server berechnet die EP, schreibt den Audit-Trail und erhöht `total_xp`/`strength`.
     *
     * @throws com.gameyourfitness.app.data.character.PostgrestHttpException bei HTTP-Fehlerstatus.
     * @throws java.io.IOException bei Netzwerkfehlern.
     */
    suspend fun logStrengthWorkout(accessToken: String, request: LogWorkoutRequest): LogWorkoutResponse
}

/** RPC-Argumente (die Namen entsprechen den `p_*`-Parametern der SQL-Funktion). */
@Serializable
data class LogWorkoutRequest(
    @SerialName("p_exercise") val exercise: String,
    @SerialName("p_sets") val sets: Int,
    @SerialName("p_reps") val reps: Int,
    @SerialName("p_weight_kg") val weightKg: Int
)

/** Rückgabe der SQL-Funktion (vom Server vergebene EP + neuer EP-Stand). */
@Serializable
data class LogWorkoutResponse(
    @SerialName("xp_awarded") val xpAwarded: Long,
    @SerialName("total_xp") val totalXp: Long
)

fun StrengthWorkout.toRequest(): LogWorkoutRequest = LogWorkoutRequest(
    exercise = exercise,
    sets = sets,
    reps = reps,
    weightKg = weightKg
)
