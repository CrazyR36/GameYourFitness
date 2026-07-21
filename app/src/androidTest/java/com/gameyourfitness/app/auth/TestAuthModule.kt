package com.gameyourfitness.app.auth

import com.gameyourfitness.app.data.auth.di.GoTrueApiModule
import com.gameyourfitness.app.data.auth.di.GoogleAuthModule
import com.gameyourfitness.app.data.auth.google.GoogleIdTokenClient
import com.gameyourfitness.app.data.auth.gotrue.GoTrueApi
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Ersetzt in E2E-Tests genau die zwei Bindings der Google-SSO-Mock-Grenze;
 * alles andere (Repository, SessionStore, OkHttp, DataStore) bleibt echt.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [GoogleAuthModule::class, GoTrueApiModule::class]
)
abstract class TestAuthModule {
    @Binds
    @Singleton
    abstract fun bindGoogleIdTokenClient(impl: FakeGoogleIdTokenClient): GoogleIdTokenClient

    @Binds
    @Singleton
    abstract fun bindGoTrueApi(impl: TestGoTrueApi): GoTrueApi
}
