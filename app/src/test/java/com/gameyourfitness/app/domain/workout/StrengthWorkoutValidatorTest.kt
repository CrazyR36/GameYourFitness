package com.gameyourfitness.app.domain.workout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StrengthWorkoutValidatorTest {
    private fun validate(
        exercise: String = "Kniebeuge",
        sets: String = "5",
        reps: String = "5",
        weight: String = "60"
    ) = StrengthWorkoutValidator.validate(exercise, sets, reps, weight)

    @Test
    fun `gueltige Eingabe wird geparst und getrimmt`() {
        val result = validate(exercise = "  Kniebeuge  ", sets = " 5 ", reps = "10", weight = "60")

        val workout = (result as WorkoutValidationResult.Valid).workout
        assertEquals("Kniebeuge", workout.exercise)
        assertEquals(5, workout.sets)
        assertEquals(10, workout.reps)
        assertEquals(60, workout.weightKg)
    }

    @Test
    fun `Gewicht 0 (Koerpergewicht) ist gueltig`() {
        val result = validate(weight = "0")
        assertEquals(0, (result as WorkoutValidationResult.Valid).workout.weightKg)
    }

    @Test
    fun `leere Uebung wird abgelehnt`() {
        val errors = (validate(exercise = "   ") as WorkoutValidationResult.Invalid).errors
        assertEquals(WorkoutInputError.BLANK_EXERCISE, errors.exercise)
    }

    @Test
    fun `zu langer Uebungsname wird abgelehnt`() {
        val errors = (validate(exercise = "x".repeat(61)) as WorkoutValidationResult.Invalid).errors
        assertEquals(WorkoutInputError.EXERCISE_TOO_LONG, errors.exercise)
    }

    @Test
    fun `null Wiederholungen ergeben einen Feldfehler nur an reps`() {
        val errors = (validate(reps = "0") as WorkoutValidationResult.Invalid).errors
        assertEquals(WorkoutInputError.REPS_OUT_OF_RANGE, errors.reps)
        assertNull(errors.exercise)
        assertNull(errors.sets)
        assertNull(errors.weight)
    }

    @Test
    fun `Saetze und reps teilen die Untergrenze 1 aber liefern getrennte Fehler`() {
        val errors = (validate(sets = "0", reps = "0") as WorkoutValidationResult.Invalid).errors
        assertEquals(WorkoutInputError.SETS_OUT_OF_RANGE, errors.sets)
        assertEquals(WorkoutInputError.REPS_OUT_OF_RANGE, errors.reps)
    }

    @Test
    fun `Werte oberhalb der Grenzen werden abgelehnt`() {
        val errors = (validate(sets = "21", reps = "101", weight = "501") as WorkoutValidationResult.Invalid).errors
        assertEquals(WorkoutInputError.SETS_OUT_OF_RANGE, errors.sets)
        assertEquals(WorkoutInputError.REPS_OUT_OF_RANGE, errors.reps)
        assertEquals(WorkoutInputError.WEIGHT_OUT_OF_RANGE, errors.weight)
    }

    @Test
    fun `negatives Gewicht wird abgelehnt`() {
        val errors = (validate(weight = "-1") as WorkoutValidationResult.Invalid).errors
        assertEquals(WorkoutInputError.WEIGHT_OUT_OF_RANGE, errors.weight)
    }

    @Test
    fun `nicht-numerische Zahlenfelder werden abgelehnt`() {
        val errors = (validate(sets = "abc", reps = "", weight = "1.5") as WorkoutValidationResult.Invalid).errors
        assertEquals(WorkoutInputError.NOT_A_NUMBER, errors.sets)
        assertEquals(WorkoutInputError.NOT_A_NUMBER, errors.reps)
        assertEquals(WorkoutInputError.NOT_A_NUMBER, errors.weight)
    }

    @Test
    fun `clear entfernt nur den Fehler des angegebenen Feldes`() {
        val all = WorkoutFieldErrors(
            exercise = WorkoutInputError.BLANK_EXERCISE,
            sets = WorkoutInputError.SETS_OUT_OF_RANGE,
            reps = WorkoutInputError.REPS_OUT_OF_RANGE,
            weight = WorkoutInputError.WEIGHT_OUT_OF_RANGE
        )
        val cleared = all.clear(WorkoutField.SETS)
        assertNull(cleared.sets)
        assertEquals(WorkoutInputError.BLANK_EXERCISE, cleared.exercise)
        assertEquals(WorkoutInputError.REPS_OUT_OF_RANGE, cleared.reps)
        assertEquals(WorkoutInputError.WEIGHT_OUT_OF_RANGE, cleared.weight)
    }
}
