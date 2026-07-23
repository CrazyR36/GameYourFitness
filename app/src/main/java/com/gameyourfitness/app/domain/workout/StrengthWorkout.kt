package com.gameyourfitness.app.domain.workout

/**
 * Eine erfasste Krafttrainings-Einheit, wie sie der Nutzer eingibt. Die daraus
 * vergebenen EP berechnet ausschließlich der Server (CLAUDE.md Abschnitt 6/8) —
 * dieses Modell trägt nur die Eingabe.
 */
data class StrengthWorkout(
    val exercise: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Int
)
