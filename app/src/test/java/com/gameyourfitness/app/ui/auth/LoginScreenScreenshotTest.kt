package com.gameyourfitness.app.ui.auth

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.gameyourfitness.app.R
import com.gameyourfitness.app.ui.theme.GameYourFitnessTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot-Tests des Login-Screens (Light + Dark + Fehlerzustand).
 * Goldens liegen unter app/src/test/screenshots/ und werden per
 * `recordRoborazziDebug` erneuert, `verifyRoborazziDebug` prueft dagegen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LoginScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginScreen_dark() {
        composeRule.setContent {
            GameYourFitnessTheme(darkTheme = true) {
                LoginScreen(isSigningIn = false, errorMessageRes = null, onSignInClick = {})
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/loginScreen_dark.png")
    }

    @Test
    fun loginScreen_light() {
        composeRule.setContent {
            GameYourFitnessTheme(darkTheme = false) {
                LoginScreen(isSigningIn = false, errorMessageRes = null, onSignInClick = {})
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/loginScreen_light.png")
    }

    @Test
    fun loginScreen_error_dark() {
        composeRule.setContent {
            GameYourFitnessTheme(darkTheme = true) {
                LoginScreen(
                    isSigningIn = false,
                    errorMessageRes = R.string.login_error_network,
                    onSignInClick = {}
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/loginScreen_error_dark.png")
    }
}
