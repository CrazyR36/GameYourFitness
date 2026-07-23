package com.gameyourfitness.app.ui.workout

import androidx.annotation.StringRes
import com.gameyourfitness.app.R
import com.gameyourfitness.app.domain.workout.WorkoutInputError

/**
 * Bildet den framework-freien [WorkoutInputError] aus der Domäne auf eine
 * String-Ressource ab — so bleibt der Validator ohne Android-Abhängigkeit testbar.
 */
@StringRes
fun WorkoutInputError.toMessageRes(): Int = when (this) {
    WorkoutInputError.BLANK_EXERCISE -> R.string.workout_error_exercise_blank
    WorkoutInputError.EXERCISE_TOO_LONG -> R.string.workout_error_exercise_too_long
    WorkoutInputError.NOT_A_NUMBER -> R.string.workout_error_not_a_number
    WorkoutInputError.SETS_OUT_OF_RANGE -> R.string.workout_error_sets_range
    WorkoutInputError.REPS_OUT_OF_RANGE -> R.string.workout_error_reps_range
    WorkoutInputError.WEIGHT_OUT_OF_RANGE -> R.string.workout_error_weight_range
}
