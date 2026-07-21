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
        val userId = prefs[KEY_USER_ID]
        // Ohne Tokens ODER user_id gibt es keine nutzbare Session: ein leerer
        // userId wuerde sonst als SignedIn("") ins UI und in spaetere user_id-
        // Abfragen fliessen. Fehlt nur das Ablaufdatum, genuegt Default 0L
        // (= abgelaufen → Refresh).
        if (accessToken == null || refreshToken == null || userId == null) return null
        return AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = prefs[KEY_EXPIRES_AT] ?: 0L,
            userId = userId,
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
