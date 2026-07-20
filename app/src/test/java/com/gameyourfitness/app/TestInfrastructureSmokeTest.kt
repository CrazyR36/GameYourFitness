package com.gameyourfitness.app

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Beweist, dass die Unit-Test-Kette (JUnit 5 + MockK + Turbine + Coroutines-Test)
 * funktioniert — Zweck von Slice #1. Faellt weg, sobald echte Domaenentests existieren.
 */
class TestInfrastructureSmokeTest {
    private fun interface Greeter {
        fun greet(): String
    }

    @Test
    fun `junit5 and mockk are wired up`() {
        val greeter = mockk<Greeter>()
        every { greeter.greet() } returns "System bereit."

        assertEquals("System bereit.", greeter.greet())
    }

    @Test
    fun `turbine collects flows in tests`() = runTest {
        flowOf(1, 2, 3).test {
            assertEquals(1, awaitItem())
            assertEquals(2, awaitItem())
            assertEquals(3, awaitItem())
            awaitComplete()
        }
    }
}
