package com.gameyourfitness.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E-Slice #1: App startet und zeigt den Startbildschirm.
 * Interagiert ausschliesslich ueber sichtbare UI-Elemente (TestTag/Text).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppStartE2ETest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun test_appStartsAndShowsHomeScreen() {
        val title = composeRule.activity.getString(R.string.home_title)
        val subtitle = composeRule.activity.getString(R.string.home_subtitle)

        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(subtitle).assertIsDisplayed()
    }
}
