package com.gameyourfitness.app.data.character.di

import com.gameyourfitness.app.data.character.CharacterApi
import com.gameyourfitness.app.data.character.CharacterHttpApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Eigenes Modul, damit E2E-Tests genau diesen Zugriff ersetzen können
 * (Offline-Fall, siehe Issue #3) — analog zu `GoTrueApiModule` (#2).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CharacterApiModule {
    @Binds
    @Singleton
    abstract fun bindCharacterApi(impl: CharacterHttpApi): CharacterApi
}
