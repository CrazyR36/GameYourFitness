package com.gameyourfitness.app.data.workout

import com.gameyourfitness.app.data.auth.gotrue.SupabaseConfig
import com.gameyourfitness.app.data.character.PostgrestHttpException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Handgeschriebener PostgREST-RPC-Client (bewusst kein SDK, wie GoTrue-/Character-Client —
 * docs/DECISIONS.md #2): ein POST auf `rpc/log_strength_workout`, OkHttp + Serialization.
 */
@Singleton
class WorkoutHttpApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val config: SupabaseConfig
) : WorkoutApi {
    override suspend fun logStrengthWorkout(accessToken: String, request: LogWorkoutRequest): LogWorkoutResponse =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(LogWorkoutRequest.serializer(), request)
            val httpRequest = Request.Builder()
                .url("${config.url}/rest/v1/rpc/log_strength_workout")
                .header("apikey", config.anonKey)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", JSON_MEDIA_TYPE_VALUE)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(httpRequest).execute().use { response ->
                response.failOnHttpError()
                val payload = checkNotNull(response.body) { "Leere PostgREST-Antwort" }.string()
                json.decodeFromString(LogWorkoutResponse.serializer(), payload)
            }
        }

    private fun Response.failOnHttpError() {
        if (!isSuccessful) {
            throw PostgrestHttpException(code, body?.string().orEmpty())
        }
    }

    private companion object {
        const val JSON_MEDIA_TYPE_VALUE = "application/json"
        val JSON_MEDIA_TYPE = JSON_MEDIA_TYPE_VALUE.toMediaType()
    }
}
