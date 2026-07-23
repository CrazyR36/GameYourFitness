package com.gameyourfitness.app.workout

import com.gameyourfitness.app.data.workout.WorkoutApi
import com.gameyourfitness.app.data.workout.di.WorkoutApiModule
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Ersetzt in E2E-Tests nur den Workout-Netzzugriff durch [TestWorkoutApi]
 * (steuerbarer Offline-Fall). Repository, SessionStore, OkHttp usw. bleiben echt.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [WorkoutApiModule::class]
)
abstract class TestWorkoutModule {
    @Binds
    @Singleton
    abstract fun bindWorkoutApi(impl: TestWorkoutApi): WorkoutApi
}
