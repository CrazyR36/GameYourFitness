package com.gameyourfitness.app.character

import com.gameyourfitness.app.data.auth.gotrue.SupabaseConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Seedet für E2E-Tests EP/Stats einer bestehenden Profilzeile über PostgREST mit dem
 * `service_role`-Key (bypasst RLS). So bleibt der Client selbst read-only (Anti-Cheat),
 * während der „gefüllter EP-Balken"-Test echte Backend-Daten liest.
 *
 * Der Key ist der ÖFFENTLICHE Beispiel-Key des lokalen/CI-Stacks (backend/.env.example)
 * und existiert ausschließlich hier im androidTest-Quellcode (Test-APK) — NIE in der App
 * (CLAUDE.md Abschnitt 8); auf einem echten Server wird dieser Beispielwert abgelehnt (#13).
 */
@Singleton
class CharacterTestSeeder @Inject constructor(
    private val config: SupabaseConfig,
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun seed(userId: String, totalXp: Long, strength: Int, vitality: Int, agility: Int, perception: Int) =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("total_xp", totalXp)
                put("strength", strength)
                put("vitality", vitality)
                put("agility", agility)
                put("perception", perception)
            }.toString()
            val request = Request.Builder()
                .url("${config.url}/rest/v1/profiles?user_id=eq.$userId")
                .header("apikey", SERVICE_ROLE_KEY)
                .header("Authorization", "Bearer $SERVICE_ROLE_KEY")
                .header("Prefer", "return=representation")
                .patch(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                check(response.isSuccessful) { "Seed fehlgeschlagen (${response.code}): $responseBody" }
                val updated = json.parseToJsonElement(responseBody).jsonArray
                check(updated.isNotEmpty()) { "Seed hat keine Zeile getroffen (user_id=$userId): $responseBody" }
            }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        // Öffentlicher Beispiel-service_role-Key des lokalen/CI-Stacks (backend/.env.example).
        const val SERVICE_ROLE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJpc3MiOiJzdXBhYmFzZS1sb2NhbCIsInJvbGUiOiJzZXJ2aWNlX3JvbGUiLCJleHAiOjE5ODM4MTI5OTZ9." +
            "CvGPVcSrdWNqg71tF_g4YKevVDnN4F2WdoXh3ce0T7k"
    }
}
