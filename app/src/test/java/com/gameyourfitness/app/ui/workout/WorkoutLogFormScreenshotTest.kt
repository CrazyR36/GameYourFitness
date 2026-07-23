package com.gameyourfitness.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.gameyourfitness.app.R
import com.gameyourfitness.app.domain.workout.WorkoutFieldErrors
import com.gameyourfitness.app.domain.workout.WorkoutInputError
import com.gameyourfitness.app.ui.theme.Dimens
import com.gameyourfitness.app.ui.theme.GameYourFitnessTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot-Tests des Krafttraining-Formulars (Light + Dark) sowie der Fehlervariante
 * (Feldfehler + Absende-Fehler). Goldens: app/src/test/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WorkoutLogFormScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent {
            GameYourFitnessTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(Dimens.screenPadding),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun workoutForm_dark() = capture("workoutForm_dark", darkTheme = true) {
        WorkoutLogForm(
            exercise = "Kniebeuge",
            onExerciseChange = {},
            sets = "5",
            onSetsChange = {},
            reps = "5",
            onRepsChange = {},
            weight = "60",
            onWeightChange = {},
            fieldErrors = WorkoutFieldErrors(),
            submitting = false,
            submitErrorRes = null,
            onSubmit = {},
            onCancel = {}
        )
    }

    @Test
    fun workoutForm_light() = capture("workoutForm_light", darkTheme = false) {
        WorkoutLogForm(
            exercise = "Kniebeuge",
            onExerciseChange = {},
            sets = "5",
            onSetsChange = {},
            reps = "5",
            onRepsChange = {},
            weight = "60",
            onWeightChange = {},
            fieldErrors = WorkoutFieldErrors(),
            submitting = false,
            submitErrorRes = null,
            onSubmit = {},
            onCancel = {}
        )
    }

    @Test
    fun workoutForm_errors_dark() = capture("workoutForm_errors_dark", darkTheme = true) {
        WorkoutLogForm(
            exercise = "",
            onExerciseChange = {},
            sets = "0",
            onSetsChange = {},
            reps = "0",
            onRepsChange = {},
            weight = "999",
            onWeightChange = {},
            fieldErrors = WorkoutFieldErrors(
                exercise = WorkoutInputError.BLANK_EXERCISE,
                sets = WorkoutInputError.SETS_OUT_OF_RANGE,
                reps = WorkoutInputError.REPS_OUT_OF_RANGE,
                weight = WorkoutInputError.WEIGHT_OUT_OF_RANGE
            ),
            submitting = false,
            submitErrorRes = R.string.workout_submit_error_network,
            onSubmit = {},
            onCancel = {}
        )
    }
}
