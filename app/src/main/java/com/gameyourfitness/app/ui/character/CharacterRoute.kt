package com.gameyourfitness.app.ui.character

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.gameyourfitness.app.ui.workout.WorkoutLogDialog
import com.gameyourfitness.app.ui.workout.WorkoutLogUiState
import com.gameyourfitness.app.ui.workout.WorkoutLogViewModel

/**
 * Stateful-Hülle: hostet [CharacterViewModel] und [WorkoutLogViewModel] und reicht State +
 * Callbacks an den stateless [CharacterScreen] weiter. Das Abmelden liegt weiterhin beim
 * Auth-Zustand (Callback aus [com.gameyourfitness.app.ui.AppRoot]).
 *
 * Das Krafttraining wird als Popup über dem Charakterbildschirm erfasst; nach erfolgreichem
 * Erfassen lädt dieselbe [CharacterViewModel]-Instanz den Charakter neu → EP-Balken und STR
 * aktualisieren sich sichtbar.
 */
@Composable
fun CharacterRoute(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CharacterViewModel = hiltViewModel(),
    workoutViewModel: WorkoutLogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val workoutState by workoutViewModel.uiState.collectAsState()
    var workoutDialogOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(workoutState) {
        if (workoutState is WorkoutLogUiState.Success) {
            viewModel.reload()
            workoutDialogOpen = false
            workoutViewModel.reset()
        }
    }

    CharacterScreen(
        state = uiState,
        onRetry = viewModel::onRetry,
        onSignOut = onSignOut,
        onLogWorkout = { workoutDialogOpen = true },
        modifier = modifier
    )

    if (workoutDialogOpen) {
        WorkoutLogDialog(
            submitState = workoutState,
            onSubmit = workoutViewModel::submit,
            onDismiss = {
                workoutDialogOpen = false
                workoutViewModel.reset()
            }
        )
    }
}
