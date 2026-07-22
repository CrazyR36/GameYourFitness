package com.gameyourfitness.app.ui.character

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.gameyourfitness.app.R
import com.gameyourfitness.app.domain.character.Character
import com.gameyourfitness.app.domain.character.Stats
import com.gameyourfitness.app.domain.progression.Progression
import com.gameyourfitness.app.domain.progression.Rank
import com.gameyourfitness.app.ui.theme.GameYourFitnessTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot-Tests des Charakterbildschirms: Inhalt (Light + Dark) sowie die
 * Sonderzustaende Laden, Leer und Fehler (CLAUDE.md Abschnitt 7 — alle drei
 * Zustaende sind auch visuell belegt). Goldens: app/src/test/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CharacterScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sampleCharacter = Character(
        userId = "preview",
        totalXp = 250,
        rank = Rank.C,
        stats = Stats(strength = 18, vitality = 15, agility = 14, perception = 16)
    )

    private val contentState = CharacterUiState.Content(
        character = sampleCharacter,
        progress = Progression.levelProgress(sampleCharacter.totalXp)
    )

    private fun capture(name: String, darkTheme: Boolean, state: CharacterUiState) {
        composeRule.setContent {
            GameYourFitnessTheme(darkTheme = darkTheme) {
                CharacterScreen(state = state, onRetry = {}, onSignOut = {})
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun characterScreen_content_dark() = capture("characterScreen_content_dark", darkTheme = true, state = contentState)

    @Test
    fun characterScreen_content_light() =
        capture("characterScreen_content_light", darkTheme = false, state = contentState)

    @Test
    fun characterScreen_loading_dark() =
        capture("characterScreen_loading_dark", darkTheme = true, state = CharacterUiState.Loading)

    @Test
    fun characterScreen_empty_dark() =
        capture("characterScreen_empty_dark", darkTheme = true, state = CharacterUiState.Empty)

    @Test
    fun characterScreen_error_dark() = capture(
        "characterScreen_error_dark",
        darkTheme = true,
        state = CharacterUiState.Error(R.string.character_error_network)
    )
}
