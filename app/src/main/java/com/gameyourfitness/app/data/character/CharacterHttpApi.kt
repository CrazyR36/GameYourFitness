package com.gameyourfitness.app.data.character

import com.gameyourfitness.app.data.auth.gotrue.SupabaseConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Handgeschriebener PostgREST-Client (bewusst kein SDK, wie beim GoTrue-Client —
 * siehe docs/DECISIONS.md #2): ein GET auf `profiles`, OkHttp + kotlinx.serialization.
 */
@Singleton
class CharacterHttpApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val config: SupabaseConfig
) : CharacterApi {
    override suspend fun fetchCharacterRow(accessToken: String): CharacterRow? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${config.url}/rest/v1/profiles?select=$SELECT_COLUMNS&limit=1")
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            response.failOnHttpError()
            val payload = checkNotNull(response.body) { "Leere PostgREST-Antwort" }.string()
            json.decodeFromString(ListSerializer(CharacterRow.serializer()), payload).firstOrNull()
        }
    }

    private fun Response.failOnHttpError() {
        if (!isSuccessful) {
            throw PostgrestHttpException(code, body?.string().orEmpty())
        }
    }

    private companion object {
        const val SELECT_COLUMNS = "user_id,total_xp,rank,strength,vitality,agility,perception"
    }
}
