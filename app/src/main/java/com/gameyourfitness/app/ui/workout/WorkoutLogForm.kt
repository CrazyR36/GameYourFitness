package com.gameyourfitness.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.gameyourfitness.app.R
import com.gameyourfitness.app.domain.workout.WorkoutFieldErrors
import com.gameyourfitness.app.domain.workout.WorkoutInputError
import com.gameyourfitness.app.ui.theme.Dimens
import com.gameyourfitness.app.ui.theme.SystemDivider
import com.gameyourfitness.app.ui.theme.systemWindow

/**
 * „System-Fenster"-Formular zum Erfassen eines Krafttrainings. Stateless: bekommt Werte
 * + Callbacks, damit es einzeln testbar und screenshot-fähig ist (CLAUDE.md Abschnitt 5/7).
 * In der App wird es von [WorkoutLogDialog] in einen Dialog gehüllt; die Feldwerte hält
 * der Aufrufer (bleiben so bei Netzfehler erhalten).
 */
@Composable
fun WorkoutLogForm(
    exercise: String,
    onExerciseChange: (String) -> Unit,
    sets: String,
    onSetsChange: (String) -> Unit,
    reps: String,
    onRepsChange: (String) -> Unit,
    weight: String,
    onWeightChange: (String) -> Unit,
    fieldErrors: WorkoutFieldErrors,
    submitting: Boolean,
    submitErrorRes: Int?,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .systemWindow()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.systemWindowPadding)
            .testTag("workout_form"),
        verticalArrangement = Arrangement.spacedBy(Dimens.formFieldSpacing)
    ) {
        Text(
            text = stringResource(R.string.workout_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("workout_title")
        )
        SystemDivider()

        LabeledField(
            value = exercise,
            onValueChange = onExerciseChange,
            labelRes = R.string.workout_field_exercise,
            fieldTag = "workout_field_exercise",
            errorTag = "workout_error_exercise",
            error = fieldErrors.exercise,
            enabled = !submitting
        )
        LabeledField(
            value = sets,
            onValueChange = onSetsChange,
            labelRes = R.string.workout_field_sets,
            fieldTag = "workout_field_sets",
            errorTag = "workout_error_sets",
            error = fieldErrors.sets,
            enabled = !submitting,
            numeric = true
        )
        LabeledField(
            value = reps,
            onValueChange = onRepsChange,
            labelRes = R.string.workout_field_reps,
            fieldTag = "workout_field_reps",
            errorTag = "workout_error_reps",
            error = fieldErrors.reps,
            enabled = !submitting,
            numeric = true
        )
        LabeledField(
            value = weight,
            onValueChange = onWeightChange,
            labelRes = R.string.workout_field_weight,
            fieldTag = "workout_field_weight",
            errorTag = "workout_error_weight",
            error = fieldErrors.weight,
            enabled = !submitting,
            numeric = true
        )

        if (submitErrorRes != null) {
            Text(
                text = stringResource(submitErrorRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workout_submit_error")
            )
        }

        // Buttons untereinander in voller Breite: die deutschen Labels ("Abbrechen",
        // "Erfassen") passen nebeneinander (je halbe Breite) nicht einzeilig und brachen um.
        val submitLabel = stringResource(R.string.workout_submit)
        Button(
            onClick = onSubmit,
            enabled = !submitting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("workout_submit")
                .semantics { contentDescription = submitLabel }
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = Dimens.progressStroke,
                    modifier = Modifier
                        .size(Dimens.inlineProgressSize)
                        .testTag("workout_submitting")
                )
            } else {
                Text(text = submitLabel)
            }
        }
        val cancelLabel = stringResource(R.string.workout_cancel)
        OutlinedButton(
            onClick = onCancel,
            enabled = !submitting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("workout_cancel")
                .semantics { contentDescription = cancelLabel }
        ) {
            Text(text = cancelLabel)
        }
    }
}

@Composable
private fun LabeledField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    fieldTag: String,
    errorTag: String,
    error: WorkoutInputError?,
    enabled: Boolean,
    numeric: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.fieldErrorSpacing)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            isError = error != null,
            label = { Text(text = stringResource(labelRes)) },
            keyboardOptions = if (numeric) {
                KeyboardOptions(keyboardType = KeyboardType.Number)
            } else {
                KeyboardOptions.Default
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(fieldTag)
        )
        if (error != null) {
            Text(
                text = stringResource(error.toMessageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(errorTag)
            )
        }
    }
}
