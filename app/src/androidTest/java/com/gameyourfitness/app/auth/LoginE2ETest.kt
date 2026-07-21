package com.gameyourfitness.app.auth

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import com.gameyourfitness.app.data.auth.SessionStore
import com.gameyourfitness.app.data.auth.google.GoogleIdTokenResult
import com.gameyourfitness.app.domain.time.EpochClock
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
 * E2E-Slice #2: Google-Login gegen den echten Supabase-Stack (GoTrue echt).
 *
 * Mock-Grenze (siehe Issue #2): GoTrue validiert Google-ID-Tokens gegen Googles
 * JWKS — deshalb wird die ID-Token-Beschaffung (FakeGoogleIdTokenClient) und der
 * Tausch ID-Token→Session (TestGoTrueApi: echter GoTrue-Signup per E-Mail/Passwort)
 * ersetzt. Session, Refresh, Logout, Persistenz und Profil-Trigger laufen echt.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LoginE2ETest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var sessionStore: SessionStore

    @Inject
    lateinit var goTrueTestBackend: GoTrueTestBackend

    @Inject
    lateinit var clock: EpochClock

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        FakeGoogleIdTokenClient.reset()
        runBlocking { sessionStore.clear() }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun test_userCanSignInWithGoogleAndSeesHomeScreen() {
        launchApp()
        waitForTag("login_screen")

        composeRule.onNodeWithTag("login_google_button").performClick()

        waitForTag("home_screen")
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("home_logout_button").assertIsDisplayed()
    }

    @Test
    fun test_signInFailureShowsErrorMessage() {
        FakeGoogleIdTokenClient.nextResult = GoogleIdTokenResult.Cancelled
        launchApp()
        waitForTag("login_screen")

        composeRule.onNodeWithTag("login_google_button").performClick()

        waitForTag("login_error")
        composeRule.onNodeWithTag("login_error")
            .assertTextEquals(string(R.string.login_error_cancelled))
        // Kein Crash, weiterhin Login-Screen, erneuter Versuch moeglich:
        composeRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("login_google_button").assertIsEnabled()
    }

    @Test
    fun test_sessionPersistsAfterAppRestart() {
        // Echte GoTrue-Session besorgen und mit absichtlich abgelaufenem Access-Token
        // persistieren: Der App-Start muss die Session aus DataStore wiederherstellen
        // UND den Token-Refresh gegen das echte GoTrue durchfuehren.
        runBlocking {
            val session = goTrueTestBackend.obtainRealSession()
            sessionStore.save(session.copy(expiresAtEpochSeconds = clock.now() - 60))
        }

        launchApp()

        waitForTag("home_screen")
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun test_userCanSignOutAndReturnsToLogin() {
        runBlocking { sessionStore.save(goTrueTestBackend.obtainRealSession()) }
        launchApp()
        waitForTag("home_screen")

        composeRule.onNodeWithTag("home_logout_button").performClick()

        waitForTag("login_screen")
        composeRule.onNodeWithTag("login_google_button").assertIsDisplayed()
        // Session ist geloescht: gespeicherte Sitzung existiert nicht mehr.
        runBlocking { check(sessionStore.load() == null) { "Session wurde beim Abmelden nicht geloescht" } }
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
