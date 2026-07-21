package com.gameyourfitness.app.domain.auth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthSessionTest {
    private val session = AuthSession(
        accessToken = "access",
        refreshToken = "refresh",
        expiresAtEpochSeconds = 1_000_000,
        userId = "user-1",
        email = "a@example.com"
    )

    @Test
    fun `Session ist gueltig, solange das Ablaufdatum samt Sicherheitsfenster nicht erreicht ist`() {
        val now = session.expiresAtEpochSeconds - AuthSession.EXPIRY_SAFETY_WINDOW_SECONDS - 1
        assertFalse(session.isExpired(now))
    }

    @Test
    fun `Session gilt bereits im Sicherheitsfenster vor dem Ablauf als abgelaufen`() {
        val now = session.expiresAtEpochSeconds - AuthSession.EXPIRY_SAFETY_WINDOW_SECONDS
        assertTrue(session.isExpired(now))
    }

    @Test
    fun `Session ist nach dem Ablaufdatum abgelaufen`() {
        assertTrue(session.isExpired(session.expiresAtEpochSeconds + 1))
    }
}
