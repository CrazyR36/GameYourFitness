package com.gameyourfitness.app.character

import com.gameyourfitness.app.data.character.CharacterApi
import com.gameyourfitness.app.data.character.CharacterHttpApi
import com.gameyourfitness.app.data.character.CharacterRow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * E2E-Doppel für den Charakter-Abruf: delegiert standardmäßig an den ECHTEN
 * PostgREST-Client (`CharacterHttpApi`) — die Happy-Path-Tests laufen also gegen
 * das echte Backend. Nur der Offline-Fall wird über [forceNetworkError] erzwungen
 * (OkHttp wirft bei fehlendem Netz ebenfalls eine [IOException]).
 */
@Singleton
class TestCharacterApi @Inject constructor(private val real: CharacterHttpApi) : CharacterApi {
    override suspend fun fetchCharacterRow(accessToken: String): CharacterRow? {
        if (forceNetworkError) throw IOException("E2E: simulierter Netzfehler")
        return real.fetchCharacterRow(accessToken)
    }

    companion object {
        @Volatile
        var forceNetworkError: Boolean = false

        fun reset() {
            forceNetworkError = false
        }
    }
}
