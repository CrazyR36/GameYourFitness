package com.gameyourfitness.app.domain.workout

/** Feld eines Krafttrainings-Formulars — für die Zuordnung von Eingabefehlern. */
enum class WorkoutField {
    EXERCISE,
    SETS,
    REPS,
    WEIGHT
}

/** Semantischer Eingabefehler (framework-frei; die UI bildet ihn auf Texte ab). */
enum class WorkoutInputError {
    BLANK_EXERCISE,
    EXERCISE_TOO_LONG,
    NOT_A_NUMBER,
    SETS_OUT_OF_RANGE,
    REPS_OUT_OF_RANGE,
    WEIGHT_OUT_OF_RANGE
}

/** Feldbezogene Validierungsfehler; `null` = Feld ist in Ordnung. */
data class WorkoutFieldErrors(
    val exercise: WorkoutInputError? = null,
    val sets: WorkoutInputError? = null,
    val reps: WorkoutInputError? = null,
    val weight: WorkoutInputError? = null
) {
    val hasErrors: Boolean
        get() = exercise != null || sets != null || reps != null || weight != null

    fun clear(field: WorkoutField): WorkoutFieldErrors = when (field) {
        WorkoutField.EXERCISE -> copy(exercise = null)
        WorkoutField.SETS -> copy(sets = null)
        WorkoutField.REPS -> copy(reps = null)
        WorkoutField.WEIGHT -> copy(weight = null)
    }
}

/** Ergebnis der Client-Validierung: gültige Eingabe oder feldbezogene Fehler. */
sealed interface WorkoutValidationResult {
    data class Valid(val workout: StrengthWorkout) : WorkoutValidationResult

    data class Invalid(val errors: WorkoutFieldErrors) : WorkoutValidationResult
}

/**
 * Plausibilisiert die manuelle Krafttraining-Eingabe (Anti-Cheat, erste Version,
 * Issue #4). Reine Domänenfunktion ohne Android/`R` — die Grenzen spiegeln die
 * serverseitigen CHECK-Constraints und die SQL-Funktion `log_strength_workout`
 * (letzte Verteidigungslinie liegt am Server, CLAUDE.md Abschnitt 6/8).
 */
object StrengthWorkoutValidator {
    const val MIN_SETS = 1
    const val MAX_SETS = 20
    const val MIN_REPS = 1
    const val MAX_REPS = 100
    const val MIN_WEIGHT_KG = 0
    const val MAX_WEIGHT_KG = 500
    const val MAX_EXERCISE_LENGTH = 60

    fun validate(exercise: String, sets: String, reps: String, weight: String): WorkoutValidationResult {
        val trimmedExercise = exercise.trim()
        val errors = WorkoutFieldErrors(
            exercise = exerciseError(trimmedExercise),
            sets = intFieldError(sets, MIN_SETS, MAX_SETS, WorkoutInputError.SETS_OUT_OF_RANGE),
            reps = intFieldError(reps, MIN_REPS, MAX_REPS, WorkoutInputError.REPS_OUT_OF_RANGE),
            weight = intFieldError(weight, MIN_WEIGHT_KG, MAX_WEIGHT_KG, WorkoutInputError.WEIGHT_OUT_OF_RANGE)
        )
        if (errors.hasErrors) return WorkoutValidationResult.Invalid(errors)
        return WorkoutValidationResult.Valid(
            StrengthWorkout(
                exercise = trimmedExercise,
                sets = sets.trim().toInt(),
                reps = reps.trim().toInt(),
                weightKg = weight.trim().toInt()
            )
        )
    }

    private fun exerciseError(trimmed: String): WorkoutInputError? = when {
        trimmed.isEmpty() -> WorkoutInputError.BLANK_EXERCISE
        trimmed.length > MAX_EXERCISE_LENGTH -> WorkoutInputError.EXERCISE_TOO_LONG
        else -> null
    }

    private fun intFieldError(raw: String, min: Int, max: Int, rangeError: WorkoutInputError): WorkoutInputError? {
        val value = raw.trim().toIntOrNull() ?: return WorkoutInputError.NOT_A_NUMBER
        return if (value < min || value > max) rangeError else null
    }
}
