package com.gameyourfitness.app.data.auth.di

import com.gameyourfitness.app.data.auth.gotrue.GoTrueApi
import com.gameyourfitness.app.data.auth.gotrue.GoTrueHttpApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Eigenes Modul, damit E2E-Tests genau dieses Binding ersetzen koennen
 * (ID-Token-Tausch, siehe Issue #2).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GoTrueApiModule {
    @Binds
    @Singleton
    abstract fun bindGoTrueApi(impl: GoTrueHttpApi): GoTrueApi
}
