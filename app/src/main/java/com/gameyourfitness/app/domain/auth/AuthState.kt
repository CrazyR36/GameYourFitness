package com.gameyourfitness.app.domain.auth

/**
 * Anmeldezustand der App aus Sicht der UI.
 */
sealed interface AuthState {
    /** Beim App-Start, bevor die gespeicherte Session geprueft wurde. */
    data object Unknown : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val userId: String, val email: String?) : AuthState
}
