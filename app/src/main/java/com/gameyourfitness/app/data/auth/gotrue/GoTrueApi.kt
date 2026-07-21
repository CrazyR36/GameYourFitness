package com.gameyourfitness.app.data.auth.gotrue

import com.gameyourfitness.app.domain.auth.AuthSession
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Schmaler GoTrue-Ausschnitt, den dieser Slice braucht. Implementierung ist
 * austauschbar (E2E-Tests ersetzen genau den ID-Token-Tausch, siehe Issue #2).
 */
interface GoTrueApi {
    /** POST /auth/v1/token?grant_type=id_token */
    suspend fun signInWithIdToken(idToken: String): SessionDto

    /** POST /auth/v1/token?grant_type=refresh_token */
    suspend fun refresh(refreshToken: String): SessionDto

    /** POST /auth/v1/logout */
    suspend fun signOut(accessToken: String)
}

@Serializable
data class SessionDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresInSeconds: Long,
    val user: UserDto
)

@Serializable
data class UserDto(val id: String, val email: String? = null)

fun SessionDto.toAuthSession(nowEpochSeconds: Long): AuthSession = AuthSession(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAtEpochSeconds = nowEpochSeconds + expiresInSeconds,
    userId = user.id,
    email = user.email
)

/** GoTrue hat mit einem HTTP-Fehlerstatus geantwortet (kein Netzwerkfehler). */
class GoTrueHttpException(val statusCode: Int, message: String) : Exception("GoTrue-Fehler $statusCode: $message")

/** Konfiguration des Supabase-Endpunkts (aus BuildConfig, siehe DI-Modul). */
data class SupabaseConfig(val url: String, val anonKey: String)
