package com.gameyourfitness.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gameyourfitness.app.data.auth.SessionStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E-Slice #1 (angepasst in Slice #2): Die App startet ohne Crash.
 * Seit dem Login-Slice ist der Startbildschirm fuer nicht angemeldete Nutzer
 * der Login-Screen — der Test prueft daher diesen (Begruendung im Issue #2).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppStartE2ETest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var sessionStore: SessionStore

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { sessionStore.clear() }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun test_appStartsAndShowsLoginScreenWhenSignedOut() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("login_google_button").assertIsDisplayed()
    }
}
