package com.gameyourfitness.app.data.auth

import com.gameyourfitness.app.data.auth.google.GoogleIdTokenClient
import com.gameyourfitness.app.data.auth.google.GoogleIdTokenResult
import com.gameyourfitness.app.data.auth.gotrue.GoTrueApi
import com.gameyourfitness.app.data.auth.gotrue.GoTrueHttpException
import com.gameyourfitness.app.data.auth.gotrue.SessionDto
import com.gameyourfitness.app.data.auth.gotrue.UserDto
import com.gameyourfitness.app.domain.auth.AuthSession
import com.gameyourfitness.app.domain.auth.AuthState
import com.gameyourfitness.app.domain.auth.SignInResult
import com.gameyourfitness.app.domain.time.EpochClock
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GoTrueAuthRepositoryTest {
    private val api = mockk<GoTrueApi>()
    private val google = mockk<GoogleIdTokenClient>()
    private val store = InMemorySessionStore()
    private val clock = EpochClock { NOW }

    private fun repository() = GoTrueAuthRepository(google, api, store, clock)

    private val storedSession = AuthSession(
        accessToken = "stored-access",
        refreshToken = "stored-refresh",
        expiresAtEpochSeconds = NOW + 3600,
        userId = "user-1",
        email = "a@example.com"
    )

    private val freshDto = SessionDto(
        accessToken = "fresh-access",
        refreshToken = "fresh-refresh",
        expiresInSeconds = 3600,
        user = UserDto(id = "user-1", email = "a@example.com")
    )

    @Nested
    inner class RestoreSession {
        @Test
        fun `ohne gespeicherte Session ist der Nutzer abgemeldet`() = runTest {
            val repo = repository()
            repo.restoreSession()
            assertEquals(AuthState.SignedOut, repo.authState.value)
        }

        @Test
        fun `gueltige gespeicherte Session meldet ohne Netzwerkzugriff an`() = runTest {
            store.save(storedSession)
            val repo = repository()

            repo.restoreSession()

            assertEquals(AuthState.SignedIn("user-1", "a@example.com"), repo.authState.value)
            verify { api wasNot Called }
        }

        @Test
        fun `abgelaufene Session wird per Refresh-Token erneuert und gespeichert`() = runTest {
            store.save(storedSession.copy(expiresAtEpochSeconds = NOW - 1))
            coEvery { api.refresh("stored-refresh") } returns freshDto
            val repo = repository()

            repo.restoreSession()

            assertEquals(AuthState.SignedIn("user-1", "a@example.com"), repo.authState.value)
            assertEquals("fresh-access", store.load()?.accessToken)
            assertEquals("fresh-refresh", store.load()?.refreshToken)
        }

        @Test
        fun `abgelehnter Refresh loescht die Session und meldet ab`() = runTest {
            store.save(storedSession.copy(expiresAtEpochSeconds = NOW - 1))
            coEvery { api.refresh(any()) } throws GoTrueHttpException(401, "invalid refresh token")
            val repo = repository()

            repo.restoreSession()

            assertEquals(AuthState.SignedOut, repo.authState.value)
            assertNull(store.load())
        }

        @Test
        fun `Netzwerkfehler beim Refresh meldet NICHT ab und behaelt die Session`() = runTest {
            val expired = storedSession.copy(expiresAtEpochSeconds = NOW - 1)
            store.save(expired)
            coEvery { api.refresh(any()) } throws IOException("kein Netz")
            val repo = repository()

            repo.restoreSession()

            assertEquals(AuthState.SignedIn("user-1", "a@example.com"), repo.authState.value)
            assertEquals(expired, store.load())
        }
    }

    @Nested
    inner class SignInWithGoogle {
        @Test
        fun `erfolgreicher Login speichert die Session und meldet an`() = runTest {
            coEvery { google.fetchIdToken() } returns GoogleIdTokenResult.Success("id-token")
            coEvery { api.signInWithIdToken("id-token") } returns freshDto
            val repo = repository()

            val result = repo.signInWithGoogle()

            assertEquals(SignInResult.Success, result)
            assertEquals(AuthState.SignedIn("user-1", "a@example.com"), repo.authState.value)
            assertEquals("fresh-access", store.load()?.accessToken)
            assertEquals(NOW + 3600, store.load()?.expiresAtEpochSeconds)
        }

        @Test
        fun `abgebrochener Google-Dialog liefert Cancelled ohne Backend-Aufruf`() = runTest {
            coEvery { google.fetchIdToken() } returns GoogleIdTokenResult.Cancelled
            val repo = repository()
            repo.restoreSession() // leerer Store → SignedOut (wie im echten Flow vor dem Klick)

            val result = repo.signInWithGoogle()

            assertEquals(SignInResult.Cancelled, result)
            // Abbruch meldet niemanden an und aendert den bestehenden Zustand nicht.
            assertEquals(AuthState.SignedOut, repo.authState.value)
            verify { api wasNot Called }
        }

        @Test
        fun `Fehler bei der Token-Beschaffung liefert Failed`() = runTest {
            coEvery { google.fetchIdToken() } returns GoogleIdTokenResult.Failure("kaputt")
            val repo = repository()

            assertEquals(SignInResult.Failed, repo.signInWithGoogle())
        }

        @Test
        fun `Netzwerkfehler beim Token-Tausch liefert NetworkError`() = runTest {
            coEvery { google.fetchIdToken() } returns GoogleIdTokenResult.Success("id-token")
            coEvery { api.signInWithIdToken(any()) } throws IOException("kein Netz")
            val repo = repository()
            repo.restoreSession() // leerer Store → SignedOut

            assertEquals(SignInResult.NetworkError, repo.signInWithGoogle())
            // Fehlgeschlagener Login meldet niemanden an: Zustand bleibt SignedOut.
            assertEquals(AuthState.SignedOut, repo.authState.value)
        }

        @Test
        fun `GoTrue-Fehler beim Token-Tausch liefert Failed`() = runTest {
            coEvery { google.fetchIdToken() } returns GoogleIdTokenResult.Success("id-token")
            coEvery { api.signInWithIdToken(any()) } throws GoTrueHttpException(400, "bad token")
            val repo = repository()

            assertEquals(SignInResult.Failed, repo.signInWithGoogle())
        }
    }

    @Nested
    inner class SignOut {
        @Test
        fun `Abmelden ruft GoTrue auf, loescht die Session und meldet ab`() = runTest {
            store.save(storedSession)
            coEvery { api.signOut("stored-access") } returns Unit
            val repo = repository()
            repo.restoreSession()

            repo.signOut()

            coVerify { api.signOut("stored-access") }
            assertNull(store.load())
            assertEquals(AuthState.SignedOut, repo.authState.value)
        }

        @Test
        fun `Abmelden loescht die Session auch bei Server-Fehler lokal`() = runTest {
            store.save(storedSession)
            coEvery { api.signOut(any()) } throws IOException("kein Netz")
            val repo = repository()

            repo.signOut()

            assertNull(store.load())
            assertEquals(AuthState.SignedOut, repo.authState.value)
        }
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

    private companion object {
        const val NOW = 1_000_000L
    }
}
