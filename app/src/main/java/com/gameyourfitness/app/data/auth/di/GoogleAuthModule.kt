package com.gameyourfitness.app.data.auth.di

import com.gameyourfitness.app.data.auth.google.CredentialManagerGoogleIdTokenClient
import com.gameyourfitness.app.data.auth.google.GoogleIdTokenClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Eigenes Modul, damit E2E-Tests genau dieses Binding ersetzen koennen
 * (Mock-Grenze fuer Google-SSO, siehe Issue #2).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleAuthModule {
    @Binds
    @Singleton
    abstract fun bindGoogleIdTokenClient(impl: CredentialManagerGoogleIdTokenClient): GoogleIdTokenClient
}
