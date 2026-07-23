package com.gameyourfitness.app.ui.workout

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gameyourfitness.app.R
import com.gameyourfitness.app.domain.workout.LogWorkoutResult
import com.gameyourfitness.app.domain.workout.StrengthWorkout
import com.gameyourfitness.app.domain.workout.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Absende-Status des Krafttraining-Popups — genau ein StateFlow (CLAUDE.md Abschnitt 5).
 * Die Feldwerte selbst sind lokaler Compose-State im Formular (bleiben so bei Netzfehler
 * erhalten); dieses ViewModel steuert nur Absenden, Fehler und Erfolg.
 *
 * [Success] wird vom aufrufenden `CharacterRoute` konsumiert (Charakter neu laden, Popup
 * schließen, danach [reset]) — deshalb kein separater Event-Kanal nötig.
 */
sealed interface WorkoutLogUiState {
    data object Idle : WorkoutLogUiState

    data object Submitting : WorkoutLogUiState

    data class Error(@StringRes val messageRes: Int) : WorkoutLogUiState

    /**
     * Erfassen erfolgreich. [leveledUp] gibt an, ob der Server einen Level-Aufstieg
     * erkannt hat; [newLevel] ist das (neue) Level nach der Vergabe (#5).
     */
    data class Success(val leveledUp: Boolean, val newLevel: Int) : WorkoutLogUiState
}

@HiltViewModel
class WorkoutLogViewModel @Inject constructor(private val workoutRepository: WorkoutRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<WorkoutLogUiState>(WorkoutLogUiState.Idle)
    val uiState: StateFlow<WorkoutLogUiState> = _uiState.asStateFlow()

    fun submit(workout: StrengthWorkout) {
        if (_uiState.value is WorkoutLogUiState.Submitting) return
        _uiState.value = WorkoutLogUiState.Submitting
        viewModelScope.launch {
            _uiState.value = when (val result = workoutRepository.logStrengthWorkout(workout)) {
                is LogWorkoutResult.Success -> WorkoutLogUiState.Success(
                    leveledUp = result.levelAfter > result.levelBefore,
                    newLevel = result.levelAfter
                )

                LogWorkoutResult.NetworkError -> WorkoutLogUiState.Error(R.string.workout_submit_error_network)
                LogWorkoutResult.Failed -> WorkoutLogUiState.Error(R.string.workout_submit_error_generic)
            }
        }
    }

    fun reset() {
        _uiState.value = WorkoutLogUiState.Idle
    }
}
