package com.gameyourfitness.app.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gameyourfitness.app.domain.auth.AuthSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Persistiert die GoTrue-Session (CLAUDE.md-Stack: DataStore Preferences).
 * Interface, damit das Repository ohne Android-DataStore unit-testbar bleibt.
 */
interface SessionStore {
    suspend fun save(session: AuthSession)

    suspend fun load(): AuthSession?

    suspend fun clear()
}

@Singleton
class DataStoreSessionStore @Inject constructor(private val dataStore: DataStore<Preferences>) : SessionStore {
    override suspend fun save(session: AuthSession) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = session.accessToken
            prefs[KEY_REFRESH_TOKEN] = session.refreshToken
            prefs[KEY_EXPIRES_AT] = session.expiresAtEpochSeconds
            prefs[KEY_USER_ID] = session.userId
            session.email?.let { prefs[KEY_EMAIL] = it } ?: prefs.remove(KEY_EMAIL)
        }
    }

    override suspend fun load(): AuthSession? {
        val prefs = dataStore.data.first()
        val accessToken = prefs[KEY_ACCESS_TOKEN]
        val refreshToken = prefs[KEY_REFRESH_TOKEN]
        // Ohne Tokens gibt es keine nutzbare Session.
        if (accessToken == null || refreshToken == null) return null
        return AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            // expiresAt/userId werden immer gemeinsam mit den Tokens geschrieben;
            // fehlen sie, ist der Store inkonsistent → Defaults statt Absturz.
            expiresAtEpochSeconds = prefs[KEY_EXPIRES_AT] ?: 0L,
            userId = prefs[KEY_USER_ID].orEmpty(),
            email = prefs[KEY_EMAIL]
        )
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_EXPIRES_AT)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_EMAIL)
        }
    }

    private companion object {
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_EXPIRES_AT = longPreferencesKey("expires_at_epoch_seconds")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_EMAIL = stringPreferencesKey("email")
    }
}
