package com.gameyourfitness.app.ui.workout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gameyourfitness.app.domain.workout.StrengthWorkout
import com.gameyourfitness.app.domain.workout.StrengthWorkoutValidator
import com.gameyourfitness.app.domain.workout.WorkoutField
import com.gameyourfitness.app.domain.workout.WorkoutFieldErrors
import com.gameyourfitness.app.domain.workout.WorkoutValidationResult
import com.gameyourfitness.app.ui.theme.Dimens

/**
 * Popup zum Erfassen eines Krafttrainings. Hält die Feldwerte als lokalen Compose-State
 * (bleiben bei Netzfehler erhalten, weil das Popup geöffnet bleibt) und validiert vor dem
 * Absenden clientseitig über die reine Domänenfunktion [StrengthWorkoutValidator]. Nur bei
 * gültiger Eingabe geht der Aufruf an [onSubmit]; der Absende-Status kommt von außen.
 */
@Composable
fun WorkoutLogDialog(submitState: WorkoutLogUiState, onSubmit: (StrengthWorkout) -> Unit, onDismiss: () -> Unit) {
    var exercise by rememberSaveable { mutableStateOf("") }
    var sets by rememberSaveable { mutableStateOf("") }
    var reps by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var errors by remember { mutableStateOf(WorkoutFieldErrors()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        WorkoutLogForm(
            exercise = exercise,
            onExerciseChange = {
                exercise = it
                errors = errors.clear(WorkoutField.EXERCISE)
            },
            sets = sets,
            onSetsChange = {
                sets = it
                errors = errors.clear(WorkoutField.SETS)
            },
            reps = reps,
            onRepsChange = {
                reps = it
                errors = errors.clear(WorkoutField.REPS)
            },
            weight = weight,
            onWeightChange = {
                weight = it
                errors = errors.clear(WorkoutField.WEIGHT)
            },
            fieldErrors = errors,
            submitting = submitState is WorkoutLogUiState.Submitting,
            submitErrorRes = (submitState as? WorkoutLogUiState.Error)?.messageRes,
            onSubmit = {
                when (val result = StrengthWorkoutValidator.validate(exercise, sets, reps, weight)) {
                    is WorkoutValidationResult.Valid -> onSubmit(result.workout)
                    is WorkoutValidationResult.Invalid -> errors = result.errors
                }
            },
            onCancel = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.screenPadding)
        )
    }
}
