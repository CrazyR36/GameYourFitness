package com.gameyourfitness.app.data.auth.gotrue

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Handgeschriebener GoTrue-Client (bewusst kein supabase-kt-SDK, siehe
 * docs/DECISIONS.md): drei Endpunkte, OkHttp + kotlinx.serialization.
 */
@Singleton
class GoTrueHttpApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val config: SupabaseConfig
) : GoTrueApi {
    override suspend fun signInWithIdToken(idToken: String): SessionDto = postForSession(
        url = "${config.url}/auth/v1/token?grant_type=id_token",
        body = json.encodeToString(
            IdTokenGrantRequest.serializer(),
            IdTokenGrantRequest(provider = "google", idToken = idToken)
        )
    )

    override suspend fun refresh(refreshToken: String): SessionDto = postForSession(
        url = "${config.url}/auth/v1/token?grant_type=refresh_token",
        body = json.encodeToString(
            RefreshGrantRequest.serializer(),
            RefreshGrantRequest(refreshToken = refreshToken)
        )
    )

    override suspend fun signOut(accessToken: String) {
        withContext(Dispatchers.IO) {
            val request = requestBuilder("${config.url}/auth/v1/logout")
                .header("Authorization", "Bearer $accessToken")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            client.newCall(request).execute().use { response ->
                response.failOnHttpError()
            }
        }
    }

    private suspend fun postForSession(url: String, body: String): SessionDto = withContext(Dispatchers.IO) {
        val request = requestBuilder(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            response.failOnHttpError()
            json.decodeFromString(
                SessionDto.serializer(),
                checkNotNull(response.body) { "Leere GoTrue-Antwort" }.string()
            )
        }
    }

    private fun requestBuilder(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("apikey", config.anonKey)

    private fun Response.failOnHttpError() {
        if (!isSuccessful) {
            throw GoTrueHttpException(code, body?.string().orEmpty())
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

@Serializable
private data class IdTokenGrantRequest(val provider: String, @SerialName("id_token") val idToken: String)

@Serializable
private data class RefreshGrantRequest(@SerialName("refresh_token") val refreshToken: String)
