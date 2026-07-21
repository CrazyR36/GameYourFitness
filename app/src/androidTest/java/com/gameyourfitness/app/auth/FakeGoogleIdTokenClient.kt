package com.gameyourfitness.app.auth

import com.gameyourfitness.app.data.auth.google.GoogleIdTokenClient
import com.gameyourfitness.app.data.auth.google.GoogleIdTokenResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ersetzt in E2E-Tests die Credential-Manager-Anbindung: liefert ein steuerbares
 * Ergebnis (Erfolg/Abbruch/Fehler), ohne einen echten Google-Dialog zu oeffnen.
 */
@Singleton
class FakeGoogleIdTokenClient @Inject constructor() : GoogleIdTokenClient {
    override suspend fun fetchIdToken(): GoogleIdTokenResult = nextResult

    companion object {
        private val defaultResult = GoogleIdTokenResult.Success("fake-google-id-token")

        var nextResult: GoogleIdTokenResult = defaultResult

        fun reset() {
            nextResult = defaultResult
        }
    }
}
