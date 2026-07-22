package com.gameyourfitness.app.data.character.di

import com.gameyourfitness.app.data.character.PostgrestCharacterRepository
import com.gameyourfitness.app.domain.character.CharacterRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CharacterModule {
    @Binds
    @Singleton
    abstract fun bindCharacterRepository(impl: PostgrestCharacterRepository): CharacterRepository
}
