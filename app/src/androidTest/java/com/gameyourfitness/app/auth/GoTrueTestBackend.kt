package com.gameyourfitness.app.auth

import com.gameyourfitness.app.data.auth.gotrue.SessionDto
import com.gameyourfitness.app.data.auth.gotrue.SupabaseConfig
import com.gameyourfitness.app.data.auth.gotrue.toAuthSession
import com.gameyourfitness.app.domain.auth.AuthSession
import com.gameyourfitness.app.domain.time.EpochClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Besorgt E2E-Tests eine ECHTE GoTrue-Session ueber den E-Mail/Passwort-Signup
 * (Autoconfirm, nur Testumgebung) — der einzige Weg an gueltige JWTs ohne ein
 * von Google signiertes ID-Token.
 */
@Singleton
class GoTrueTestBackend @Inject constructor(
    private val config: SupabaseConfig,
    private val client: OkHttpClient,
    private val json: Json,
    private val clock: EpochClock
) {
    suspend fun signUpNewUser(): SessionDto = withContext(Dispatchers.IO) {
        val email = "e2e-${UUID.randomUUID()}@example.com"
        val body = """{"email":"$email","password":"$TEST_PASSWORD"}"""
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${config.url}/auth/v1/signup")
            .header("apikey", config.anonKey)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                "GoTrue-Signup fehlgeschlagen (${response.code}): $payload"
            }
            json.decodeFromString(SessionDto.serializer(), payload)
        }
    }

    suspend fun obtainRealSession(): AuthSession = signUpNewUser().toAuthSession(clock.now())

    companion object {
        const val TEST_PASSWORD = "e2e-password-123!"
    }
}
