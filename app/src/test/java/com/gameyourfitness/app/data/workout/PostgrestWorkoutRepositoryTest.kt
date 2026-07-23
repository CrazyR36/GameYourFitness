package com.gameyourfitness.app.data.workout

import com.gameyourfitness.app.data.auth.SessionStore
import com.gameyourfitness.app.data.character.PostgrestHttpException
import com.gameyourfitness.app.domain.auth.AuthSession
import com.gameyourfitness.app.domain.workout.LogWorkoutResult
import com.gameyourfitness.app.domain.workout.StrengthWorkout
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostgrestWorkoutRepositoryTest {
    private val api = mockk<WorkoutApi>()
    private val store = InMemorySessionStore()

    private fun repository() = PostgrestWorkoutRepository(api, store)

    private val session = AuthSession(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAtEpochSeconds = 1_000_000,
        userId = "user-1",
        email = "a@example.com"
    )

    private val workout = StrengthWorkout(exercise = "Kniebeuge", sets = 5, reps = 5, weightKg = 60)

    @Test
    fun `erfolgreiche RPC liefert die vom Server vergebenen EP und nutzt Token plus Argumente`() = runTest {
        store.save(session)
        val requestSlot = slot<LogWorkoutRequest>()
        coEvery { api.logStrengthWorkout("access-token", capture(requestSlot)) } returns
            LogWorkoutResponse(xpAwarded = 40, totalXp = 40)

        val result = repository().logStrengthWorkout(workout)

        assertEquals(40L, (result as LogWorkoutResult.Success).awardedXp)
        assertEquals("Kniebeuge", requestSlot.captured.exercise)
        assertEquals(5, requestSlot.captured.sets)
        assertEquals(5, requestSlot.captured.reps)
        assertEquals(60, requestSlot.captured.weightKg)
        coVerify { api.logStrengthWorkout("access-token", any()) }
    }

    @Test
    fun `ohne gespeicherte Session ergibt Failed ohne Backend-Aufruf`() = runTest {
        val result = repository().logStrengthWorkout(workout)

        assertEquals(LogWorkoutResult.Failed, result)
        coVerify(exactly = 0) { api.logStrengthWorkout(any(), any()) }
    }

    @Test
    fun `Netzwerkfehler ergibt NetworkError (Eingaben bleiben im UI erhalten)`() = runTest {
        store.save(session)
        coEvery { api.logStrengthWorkout(any(), any()) } throws IOException("kein Netz")

        assertEquals(LogWorkoutResult.NetworkError, repository().logStrengthWorkout(workout))
    }

    @Test
    fun `serverseitige Ablehnung (HTTP-Fehler) ergibt Failed`() = runTest {
        store.save(session)
        coEvery { api.logStrengthWorkout(any(), any()) } throws PostgrestHttpException(400, "implausibel")

        assertEquals(LogWorkoutResult.Failed, repository().logStrengthWorkout(workout))
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
