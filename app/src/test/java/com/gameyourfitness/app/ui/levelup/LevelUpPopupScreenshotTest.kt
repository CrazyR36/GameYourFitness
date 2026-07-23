package com.gameyourfitness.app.ui.levelup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.gameyourfitness.app.ui.theme.Dimens
import com.gameyourfitness.app.ui.theme.GameYourFitnessTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot-Tests des Level-Up-Popup-Inhalts (Light + Dark). Der animierte Overlay-Rahmen
 * ([LevelUpOverlay]) wird nicht aufgenommen — nur der stateless Inhalt. Goldens:
 * app/src/test/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LevelUpPopupScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent {
            GameYourFitnessTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(Dimens.screenPadding),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun levelUpPopup_dark() = capture("levelUpPopup_dark", darkTheme = true) {
        LevelUpPopup(level = 5, onDismiss = {})
    }

    @Test
    fun levelUpPopup_light() = capture("levelUpPopup_light", darkTheme = false) {
        LevelUpPopup(level = 5, onDismiss = {})
    }
}
