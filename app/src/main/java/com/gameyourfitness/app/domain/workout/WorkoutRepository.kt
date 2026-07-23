package com.gameyourfitness.app.domain.workout

/**
 * Ergebnis des serverseitigen Erfassens eines Krafttrainings.
 *
 * [Success] trägt die vom Server vergebenen EP (der Client rechnet sie nicht selbst).
 * [NetworkError] hält die Eingaben im UI erhalten (kein Netz ≠ ungültige Eingabe);
 * [Failed] deckt HTTP-/Serverfehler ab (inkl. serverseitiger Ablehnung unplausibler
 * Eingaben, die die Client-Validierung nicht abgefangen hat).
 */
sealed interface LogWorkoutResult {
    /**
     * @param awardedXp vom Server vergebene EP.
     * @param levelBefore serverseitig berechnetes Level vor der Vergabe.
     * @param levelAfter serverseitig berechnetes Level nach der Vergabe;
     *   `levelAfter > levelBefore` bedeutet Level-Up (#5).
     */
    data class Success(val awardedXp: Long, val levelBefore: Int, val levelAfter: Int) : LogWorkoutResult

    data object NetworkError : LogWorkoutResult

    data object Failed : LogWorkoutResult
}

/**
 * Einziger Zugang der UI zum Erfassen eines Krafttrainings (CLAUDE.md Abschnitt 5:
 * Backend-Zugriff ausschließlich über Repository-Interfaces im domain-Layer).
 */
interface WorkoutRepository {
    suspend fun logStrengthWorkout(workout: StrengthWorkout): LogWorkoutResult
}
