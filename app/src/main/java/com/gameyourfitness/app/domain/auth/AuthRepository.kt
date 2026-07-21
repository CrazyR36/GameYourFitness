package com.gameyourfitness.app.domain.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Einziger Zugang der UI zum Anmeldezustand (CLAUDE.md Abschnitt 5:
 * Backend-Zugriff ausschliesslich ueber Repository-Interfaces im domain-Layer).
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>

    /**
     * Stellt beim App-Start die persistierte Session wieder her; erneuert
     * abgelaufene Access-Tokens per Refresh-Token. Bei reinen Netzwerkfehlern
     * bleibt der Nutzer angemeldet — abgemeldet wird nur, wenn GoTrue den
     * Refresh-Token tatsaechlich ablehnt.
     */
    suspend fun restoreSession()

    suspend fun signInWithGoogle(): SignInResult

    /** Meldet lokal in jedem Fall ab, auch wenn der Server nicht erreichbar ist. */
    suspend fun signOut()
}
