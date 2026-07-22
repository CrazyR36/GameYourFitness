package com.gameyourfitness.app.character

import com.gameyourfitness.app.data.character.CharacterApi
import com.gameyourfitness.app.data.character.di.CharacterApiModule
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Ersetzt in E2E-Tests nur den Charakter-Netzzugriff durch [TestCharacterApi]
 * (steuerbarer Offline-Fall). Repository, SessionStore, OkHttp usw. bleiben echt.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CharacterApiModule::class]
)
abstract class TestCharacterModule {
    @Binds
    @Singleton
    abstract fun bindCharacterApi(impl: TestCharacterApi): CharacterApi
}
