package com.gameyourfitness.app.data.auth.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.gameyourfitness.app.BuildConfig
import com.gameyourfitness.app.data.auth.DataStoreSessionStore
import com.gameyourfitness.app.data.auth.GoTrueAuthRepository
import com.gameyourfitness.app.data.auth.SessionStore
import com.gameyourfitness.app.data.auth.gotrue.SupabaseConfig
import com.gameyourfitness.app.domain.auth.AuthRepository
import com.gameyourfitness.app.domain.time.EpochClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

// Prozessweiter Singleton per Property-Delegate: verhindert "multiple DataStores
// active for the same file", wenn Hilt-Testkomponenten mehrfach aufgebaut werden.
private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_session")

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: GoTrueAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSessionStore(impl: DataStoreSessionStore): SessionStore

    companion object {
        @Provides
        @Singleton
        fun provideAuthDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.authDataStore

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        @Provides
        @Singleton
        fun provideJson(): Json = Json { ignoreUnknownKeys = true }

        @Provides
        @Singleton
        fun provideSupabaseConfig(): SupabaseConfig = SupabaseConfig(
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY
        )

        @Provides
        @Singleton
        fun provideEpochClock(): EpochClock = EpochClock { System.currentTimeMillis() / MILLIS_PER_SECOND }

        private const val HTTP_TIMEOUT_SECONDS = 15L
        private const val MILLIS_PER_SECOND = 1000L
    }
}
