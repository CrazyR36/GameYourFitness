package com.gameyourfitness.app.levelup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gameyourfitness.app.MainActivity
import com.gameyourfitness.app.auth.GoTrueTestBackend
import com.gameyourfitness.app.character.TestCharacterApi
import com.gameyourfitness.app.data.auth.SessionStore
import com.gameyourfitness.app.workout.TestWorkoutApi
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
 * E2E-Slice #5: Ein Training über die EP-Schwelle löst ein serverseitig erkanntes Level-Up
 * aus (die RPC liefert level_before/level_after), das „System-Fenster"-Popup erscheint, und
 * das neue Level übersteht einen App-Neustart (serverseitig persistiert). Echte RPC gegen
 * den Supabase-Stack; die Session wird direkt geseedet (Login ist in #2 abgedeckt).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LevelUpE2ETest {
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
    fun test_reachingXpThresholdShowsLevelUpPopup() {
        runBlocking { sessionStore.save(goTrueTestBackend.obtainRealSession()) }

        launchApp()
        waitForTag("character_level")
        composeRule.onNodeWithTag("character_level").assertTextEquals("1")

        // 5 x 20 @ 0 kg = 100 EP → genau Level 2.
        logWorkout(exercise = "Kniebeuge", sets = "5", reps = "20", weight = "0")

        waitForTag("level_up_popup")
        composeRule.onNodeWithTag("level_up_level").assertTextEquals("2")

        composeRule.onNodeWithTag("level_up_dismiss").performClick()
        waitForText("character_level", "2")
    }

    @Test
    fun test_levelUpPersistsAfterAppRestart() {
        runBlocking { sessionStore.save(goTrueTestBackend.obtainRealSession()) }

        launchApp()
        waitForTag("character_level")
        logWorkout(exercise = "Kniebeuge", sets = "5", reps = "20", weight = "0")
        waitForTag("level_up_popup")
        composeRule.onNodeWithTag("level_up_dismiss").performClick()
        waitForText("character_level", "2")

        // Neustart: dieselbe Session, Level wird serverseitig aus total_xp abgeleitet.
        scenario?.close()
        launchApp()
        waitForText("character_level", "2")
        composeRule.onNodeWithTag("character_level").assertTextEquals("2")
    }

    @Test
    fun test_multipleLevelUpsInOneWorkoutAreAllApplied() {
        runBlocking { sessionStore.save(goTrueTestBackend.obtainRealSession()) }

        launchApp()
        waitForTag("character_level")

        // 20 x 50 @ 0 kg = 1000 EP → Level 5 (vier Stufen auf einmal).
        logWorkout(exercise = "Kniebeuge", sets = "20", reps = "50", weight = "0")

        waitForTag("level_up_popup")
        composeRule.onNodeWithTag("level_up_level").assertTextEquals("5")

        composeRule.onNodeWithTag("level_up_dismiss").performClick()
        waitForText("character_level", "5")
        composeRule.onNodeWithTag("character_xp_bar").assertIsDisplayed()
    }

    private fun logWorkout(exercise: String, sets: String, reps: String, weight: String) {
        composeRule.onNodeWithTag("character_log_workout").performClick()
        waitForTag("workout_form")
        composeRule.onNodeWithTag("workout_field_exercise").performTextInput(exercise)
        composeRule.onNodeWithTag("workout_field_sets").performTextInput(sets)
        composeRule.onNodeWithTag("workout_field_reps").performTextInput(reps)
        composeRule.onNodeWithTag("workout_field_weight").performTextInput(weight)
        composeRule.onNodeWithTag("workout_submit").performClick()
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
            runCatching { composeRule.onNodeWithTag(tag).assertTextEquals(text) }.isSuccess
        }
    }
}
