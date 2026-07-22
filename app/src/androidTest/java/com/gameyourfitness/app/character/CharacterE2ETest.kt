package com.gameyourfitness.app.character

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gameyourfitness.app.MainActivity
import com.gameyourfitness.app.R
import com.gameyourfitness.app.auth.GoTrueTestBackend
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
 * E2E-Slice #3: Der Charakterbildschirm liest echt aus PostgREST (RLS aktiv).
 * Die Session wird direkt geseedet (der Google-Login ist in #2 abgedeckt); der
 * „gefüllter EP-Balken"-Fall seedet EP/Stats per service_role (Client bleibt read-only).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CharacterE2ETest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var sessionStore: SessionStore

    @Inject
    lateinit var goTrueTestBackend: GoTrueTestBackend

    @Inject
    lateinit var seeder: CharacterTestSeeder

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        TestCharacterApi.reset()
        runBlocking { sessionStore.clear() }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun test_newUserSeesInitialCharacterValues() {
        runBlocking { sessionStore.save(goTrueTestBackend.obtainRealSession()) }

        launchApp()
        waitForTag("character_level")

        composeRule.onNodeWithTag("character_level").assertTextEquals("1")
        composeRule.onNodeWithTag("character_rank").assertTextEquals("E")
        composeRule.onNodeWithTag("character_stat_str").assertTextEquals("10")
        composeRule.onNodeWithTag("character_stat_vit").assertTextEquals("10")
        composeRule.onNodeWithTag("character_stat_agi").assertTextEquals("10")
        composeRule.onNodeWithTag("character_stat_per").assertTextEquals("10")
        composeRule.onNodeWithTag("character_xp_text").assertTextEquals("0 / 100 EP")
    }

    @Test
    fun test_characterScreenShowsLevelRankXpBarAndStats() {
        runBlocking {
            val session = goTrueTestBackend.obtainRealSession()
            sessionStore.save(session)
            // Geseedete Historie: 150 EP → Level 2, Balken bei 50/200; abweichende Stats.
            seeder.seed(
                userId = session.userId,
                totalXp = 150,
                strength = 15,
                vitality = 12,
                agility = 11,
                perception = 13
            )
        }

        launchApp()
        waitForTag("character_level")

        composeRule.onNodeWithTag("character_level").assertTextEquals("2")
        composeRule.onNodeWithTag("character_rank").assertTextEquals("E")
        composeRule.onNodeWithTag("character_stat_str").assertTextEquals("15")
        composeRule.onNodeWithTag("character_stat_vit").assertTextEquals("12")
        composeRule.onNodeWithTag("character_stat_agi").assertTextEquals("11")
        composeRule.onNodeWithTag("character_stat_per").assertTextEquals("13")
        composeRule.onNodeWithTag("character_xp_text").assertTextEquals("50 / 200 EP")
        composeRule.onNodeWithTag("character_xp_bar").assertIsDisplayed()
    }

    @Test
    fun test_characterScreenShowsErrorStateWhenOffline() {
        runBlocking { sessionStore.save(goTrueTestBackend.obtainRealSession()) }
        TestCharacterApi.forceNetworkError = true

        launchApp()
        waitForTag("character_error")

        composeRule.onNodeWithTag("character_error")
            .assertTextEquals(string(R.string.character_error_network))
        composeRule.onNodeWithTag("character_retry_button").assertIsDisplayed()

        // Netz zurück → Retry lädt den Charakter erfolgreich.
        TestCharacterApi.forceNetworkError = false
        composeRule.onNodeWithTag("character_retry_button").performClick()

        waitForTag("character_level")
        composeRule.onNodeWithTag("character_level").assertTextEquals("1")
    }

    private fun launchApp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)
}
