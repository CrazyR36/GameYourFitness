package com.gameyourfitness.app.data.auth.google

/**
 * Beschafft das Google-ID-Token (Credential Manager). In E2E-Tests durch einen
 * Fake ersetzt — GoTrue kann lokal keine gefaelschten Google-Signaturen pruefen.
 */
interface GoogleIdTokenClient {
    suspend fun fetchIdToken(): GoogleIdTokenResult
}

sealed interface GoogleIdTokenResult {
    data class Success(val idToken: String) : GoogleIdTokenResult

    /** Nutzer hat den Dialog abgebrochen. */
    data object Cancelled : GoogleIdTokenResult

    data class Failure(val message: String?) : GoogleIdTokenResult
}
