package com.gameyourfitness.app.data.character

import com.gameyourfitness.app.data.auth.SessionStore
import com.gameyourfitness.app.domain.character.CharacterRepository
import com.gameyourfitness.app.domain.character.CharacterResult
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liest den Charakter echt aus PostgREST. Den Zugriffstoken holt sich das Repository
 * aus dem [SessionStore] (data→data, unterhalb der UI) — die Domäne kennt keine Tokens.
 */
@Singleton
class PostgrestCharacterRepository @Inject constructor(
    private val characterApi: CharacterApi,
    private val sessionStore: SessionStore
) : CharacterRepository {
    override suspend fun getCharacter(): CharacterResult {
        val session = sessionStore.load() ?: return CharacterResult.Failed
        return try {
            when (val row = characterApi.fetchCharacterRow(session.accessToken)) {
                null -> CharacterResult.Empty
                else -> CharacterResult.Success(row.toCharacter())
            }
        } catch (_: IOException) {
            CharacterResult.NetworkError
        } catch (_: PostgrestHttpException) {
            CharacterResult.Failed
        }
    }
}
