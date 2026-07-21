package com.gameyourfitness.app.domain.auth

/**
 * Angemeldete GoTrue-Sitzung, wie sie die App persistiert.
 */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String,
    val email: String?
) {
    /**
     * Abgelaufen gilt eine Session schon [EXPIRY_SAFETY_WINDOW_SECONDS] vor dem
     * eigentlichen Ablauf, damit ein Access-Token nie "auf den letzten Drücker"
     * verwendet wird und unterwegs abläuft.
     */
    fun isExpired(nowEpochSeconds: Long): Boolean =
        nowEpochSeconds >= expiresAtEpochSeconds - EXPIRY_SAFETY_WINDOW_SECONDS

    companion object {
        const val EXPIRY_SAFETY_WINDOW_SECONDS = 60L
    }
}
