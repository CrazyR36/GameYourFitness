package com.gameyourfitness.app.ui.workout

import com.gameyourfitness.app.R
import com.gameyourfitness.app.domain.workout.LogWorkoutResult
import com.gameyourfitness.app.domain.workout.StrengthWorkout
import com.gameyourfitness.app.domain.workout.WorkoutRepository
import com.gameyourfitness.app.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class WorkoutLogViewModelTest {
    private val repository = mockk<WorkoutRepository>()

    private fun viewModel() = WorkoutLogViewModel(repository)

    private val workout = StrengthWorkout(exercise = "Kniebeuge", sets = 5, reps = 5, weightKg = 60)

    @Test
    fun `Startzustand ist Idle`() = runTest {
        assertEquals(WorkoutLogUiState.Idle, viewModel().uiState.value)
    }

    @Test
    fun `submit zeigt zuerst Submitting und danach Success`() = runTest {
        val gate = CompletableDeferred<LogWorkoutResult>()
        coEvery { repository.logStrengthWorkout(workout) } coAnswers { gate.await() }

        val vm = viewModel()
        vm.submit(workout)
        assertEquals(WorkoutLogUiState.Submitting, vm.uiState.value)

        gate.complete(LogWorkoutResult.Success(awardedXp = 40))
        assertEquals(WorkoutLogUiState.Success, vm.uiState.value)
    }

    @Test
    fun `Netzwerkfehler liefert die Netz-Meldung`() = runTest {
        coEvery { repository.logStrengthWorkout(workout) } returns LogWorkoutResult.NetworkError

        val vm = viewModel()
        vm.submit(workout)

        val error = vm.uiState.value as WorkoutLogUiState.Error
        assertEquals(R.string.workout_submit_error_network, error.messageRes)
    }

    @Test
    fun `sonstiger Fehler liefert die generische Meldung`() = runTest {
        coEvery { repository.logStrengthWorkout(workout) } returns LogWorkoutResult.Failed

        val vm = viewModel()
        vm.submit(workout)

        val error = vm.uiState.value as WorkoutLogUiState.Error
        assertEquals(R.string.workout_submit_error_generic, error.messageRes)
    }

    @Test
    fun `ein zweiter submit waehrend des Sendens wird ignoriert`() = runTest {
        val gate = CompletableDeferred<LogWorkoutResult>()
        coEvery { repository.logStrengthWorkout(workout) } coAnswers { gate.await() }

        val vm = viewModel()
        vm.submit(workout)
        vm.submit(workout)

        gate.complete(LogWorkoutResult.Success(awardedXp = 40))
        coVerify(exactly = 1) { repository.logStrengthWorkout(workout) }
    }

    @Test
    fun `reset kehrt nach einem Fehler zu Idle zurueck`() = runTest {
        coEvery { repository.logStrengthWorkout(workout) } returns LogWorkoutResult.Failed
        val vm = viewModel()
        vm.submit(workout)
        assertEquals(WorkoutLogUiState.Error::class, vm.uiState.value::class)

        vm.reset()
        assertEquals(WorkoutLogUiState.Idle, vm.uiState.value)
    }

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }
}
