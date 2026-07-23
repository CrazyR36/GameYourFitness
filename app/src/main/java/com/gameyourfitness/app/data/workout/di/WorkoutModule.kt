package com.gameyourfitness.app.data.workout.di

import com.gameyourfitness.app.data.workout.PostgrestWorkoutRepository
import com.gameyourfitness.app.domain.workout.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutModule {
    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: PostgrestWorkoutRepository): WorkoutRepository
}
