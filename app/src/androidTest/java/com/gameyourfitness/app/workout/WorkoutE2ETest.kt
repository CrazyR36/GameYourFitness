package com.gameyourfitness.app.workout

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gameyourfitness.app.MainActivity
import com.gameyourfitness.app.R
import com.gameyourfitness.app.auth.GoTrueTestBackend
import com.gameyourfitness.app.character.TestCharacterApi
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
 * E2E-Slice #4: Ein Krafttraining wird über das „System-Fenster"-Popup erfasst; die EP
 * berechnet der Server (echte RPC gegen den Supabase-Stack), danach zeigt der neu geladene
 * Charakterbildschirm den gestiegenen EP-Balken und STR. Die Session wird direkt geseedet
 * (der Google-Login ist in #2 abgedeckt).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkoutE2ETest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var sessionStore: SessionStore

    @Inject
    lateinit var goTrueTestBackend: GoTrueTestBackend

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        TestWorkoutApi.reset()
        TestCharacterApi.reset()
        runBlocking { sessionStore.clear() }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun test_userLogsStrengthWorkoutAndSeesXpIncrease() {
        runBlocking { sessionStore.save(goTrueTestBackend.obtainRealSession()) }

        launchApp()
        waitForTag("character_level")
        composeRule.onNodeWithTag("character_xp_text").assertTextEquals("0 / 100 EP")
        composeRule.onNodeWithTag("character_stat_str").assertTextEquals("0")

        composeRule.onNodeWithTag("character_log_workout").performClick()
        waitForTag("workout_form")

        composeRule.onNodeWithTag("workout_field_exercise").performTextInput("Kniebeuge")
        composeRule.onNodeWithTag("workout_field_sets").performTextInput("5")
        composeRule.onNodeWithTag("workout_field_reps").performTextInput("5")
        composeRule.onNodeWithTag("workout_field_weight").performTextInput("60")
        composeRule.onNodeWithTag("workout_submit").performClick()

        // Server vergibt 40 EP (5*5*(100+60)/100) → Popup schließt, Charakter lädt neu.
        waitForText("character_xp_text", "40 / 100 EP")
        composeRule.onNodeWithTag("character_xp_text").assertTextEquals("40 / 100 EP")
        composeRule.onNodeWithTag("character_stat_str").assertTextEquals("1")
    }

    @Test
    fun test_invalidWorkoutInputShowsValidationError() {
        runBlocking { sessionStore.save(goTrueTestBackend.obtainRealSession()) }

        launchApp()
        waitForTag("character_level")
        composeRule.onNodeWithTag("character_log_workout").performClick()
        waitForTag("workout_form")

        composeRule.onNodeWithTag("workout_field_exercise").performTextInput("Kniebeuge")
        composeRule.onNodeWithTag("workout_field_sets").performTextInput("5")
        composeRule.onNodeWithTag("workout_field_reps").performTextInput("0")
        composeRule.onNodeWithTag("workout_field_weight").performTextInput("60")
        composeRule.onNodeWithTag("workout_submit").performClick()

        // Rein clientseitige Ablehnung: Feldfehler, Popup bleibt offen, keine EP-Änderung.
        waitForTag("workout_error_reps")
        composeRule.onNodeWithTag("workout_error_reps")
            .assertTextEquals(string(R.string.workout_error_reps_range))
        composeRule.onNodeWithTag("workout_form").assertIsDisplayed()
        composeRule.onNodeWithTag("character_xp_text").assertTextEquals("0 / 100 EP")
    }

    @Test
    fun test_workoutSubmitOfflineShowsErrorAndKeepsInput() {
        runBlocking { sessionStore.save(goTrueTestBackend.obtainRealSession()) }
        TestWorkoutApi.forceNetworkError = true

        launchApp()
        waitForTag("character_level")
        composeRule.onNodeWithTag("character_log_workout").performClick()
        waitForTag("workout_form")

        composeRule.onNodeWithTag("workout_field_exercise").performTextInput("Kniebeuge")
        composeRule.onNodeWithTag("workout_field_sets").performTextInput("5")
        composeRule.onNodeWithTag("workout_field_reps").performTextInput("5")
        composeRule.onNodeWithTag("workout_field_weight").performTextInput("60")
        composeRule.onNodeWithTag("workout_submit").performClick()

        waitForTag("workout_submit_error")
        composeRule.onNodeWithTag("workout_submit_error")
            .assertTextEquals(string(R.string.workout_submit_error_network))
        // Eingaben bleiben erhalten (Popup blieb offen).
        composeRule.onNodeWithTag("workout_field_sets").assertTextContains("5")
        composeRule.onNodeWithTag("workout_field_reps").assertTextContains("5")
        composeRule.onNodeWithTag("workout_field_weight").assertTextContains("60")
        composeRule.onNodeWithTag("workout_form").assertIsDisplayed()
    }

    private fun launchApp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(tag: String, text: String, timeoutMillis: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertTextEquals(text)
            }.isSuccess
        }
    }

    private fun string(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)
}
