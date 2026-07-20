package com.gameyourfitness.app.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.gameyourfitness.app.ui.theme.GameYourFitnessTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot-Tests des Startbildschirms (Light + Dark).
 * Goldens liegen unter app/src/test/screenshots/ und werden per
 * `recordRoborazziDebug` erneuert, `verifyRoborazziDebug` prueft dagegen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HomeScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_dark() {
        composeRule.setContent {
            GameYourFitnessTheme(darkTheme = true) {
                HomeScreen()
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/homeScreen_dark.png")
    }

    @Test
    fun homeScreen_light() {
        composeRule.setContent {
            GameYourFitnessTheme(darkTheme = false) {
                HomeScreen()
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/homeScreen_light.png")
    }
}
