package com.gameyourfitness.app.data.character

import com.gameyourfitness.app.data.auth.SessionStore
import com.gameyourfitness.app.domain.auth.AuthSession
import com.gameyourfitness.app.domain.character.CharacterResult
import com.gameyourfitness.app.domain.progression.Rank
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostgrestCharacterRepositoryTest {
    private val api = mockk<CharacterApi>()
    private val store = InMemorySessionStore()

    private fun repository() = PostgrestCharacterRepository(api, store)

    private val session = AuthSession(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAtEpochSeconds = 1_000_000,
        userId = "user-1",
        email = "a@example.com"
    )

    private val row = CharacterRow(
        userId = "user-1",
        totalXp = 150,
        rank = "D",
        strength = 15,
        vitality = 12,
        agility = 11,
        perception = 13
    )

    @Test
    fun `erfolgreicher Abruf bildet die Zeile auf den Charakter ab und nutzt den Access-Token`() = runTest {
        store.save(session)
        coEvery { api.fetchCharacterRow("access-token") } returns row

        val result = repository().getCharacter()

        val character = (result as CharacterResult.Success).character
        assertEquals("user-1", character.userId)
        assertEquals(150L, character.totalXp)
        assertEquals(Rank.D, character.rank)
        assertEquals(15, character.stats.strength)
        assertEquals(12, character.stats.vitality)
        assertEquals(11, character.stats.agility)
        assertEquals(13, character.stats.perception)
        coVerify { api.fetchCharacterRow("access-token") }
    }

    @Test
    fun `unbekannter Rang-String faellt auf E zurueck`() = runTest {
        store.save(session)
        coEvery { api.fetchCharacterRow(any()) } returns row.copy(rank = "X")

        val result = repository().getCharacter()

        assertEquals(Rank.E, (result as CharacterResult.Success).character.rank)
    }

    @Test
    fun `keine Zeile ergibt den Leerzustand`() = runTest {
        store.save(session)
        coEvery { api.fetchCharacterRow(any()) } returns null

        assertEquals(CharacterResult.Empty, repository().getCharacter())
    }

    @Test
    fun `ohne gespeicherte Session liefert der Abruf Failed ohne Backend-Aufruf`() = runTest {
        val result = repository().getCharacter()

        assertEquals(CharacterResult.Failed, result)
        coVerify(exactly = 0) { api.fetchCharacterRow(any()) }
    }

    @Test
    fun `Netzwerkfehler ergibt NetworkError`() = runTest {
        store.save(session)
        coEvery { api.fetchCharacterRow(any()) } throws IOException("kein Netz")

        assertEquals(CharacterResult.NetworkError, repository().getCharacter())
    }

    @Test
    fun `HTTP-Fehler ergibt Failed`() = runTest {
        store.save(session)
        coEvery { api.fetchCharacterRow(any()) } throws PostgrestHttpException(401, "jwt expired")

        assertEquals(CharacterResult.Failed, repository().getCharacter())
    }

    private class InMemorySessionStore : SessionStore {
        private var session: AuthSession? = null

        override suspend fun save(session: AuthSession) {
            this.session = session
        }

        override suspend fun load(): AuthSession? = session

        override suspend fun clear() {
            session = null
        }
    }
}
