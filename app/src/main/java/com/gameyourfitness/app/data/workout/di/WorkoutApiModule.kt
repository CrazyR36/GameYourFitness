package com.gameyourfitness.app.data.workout.di

import com.gameyourfitness.app.data.workout.WorkoutApi
import com.gameyourfitness.app.data.workout.WorkoutHttpApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Eigenes Modul, damit E2E-Tests genau diesen Zugriff ersetzen können
 * (Offline-Fall, siehe Issue #4) — analog zu `CharacterApiModule` (#3).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutApiModule {
    @Binds
    @Singleton
    abstract fun bindWorkoutApi(impl: WorkoutHttpApi): WorkoutApi
}
