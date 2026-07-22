package com.gameyourfitness.app.ui.character

import com.gameyourfitness.app.R
import com.gameyourfitness.app.domain.character.Character
import com.gameyourfitness.app.domain.character.CharacterRepository
import com.gameyourfitness.app.domain.character.CharacterResult
import com.gameyourfitness.app.domain.character.Stats
import com.gameyourfitness.app.domain.progression.Rank
import com.gameyourfitness.app.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CharacterViewModelTest {
    private val repository = mockk<CharacterRepository>()

    private fun viewModel() = CharacterViewModel(repository)

    private val character = Character(
        userId = "user-1",
        totalXp = 150,
        rank = Rank.D,
        stats = Stats(strength = 15, vitality = 12, agility = 11, perception = 13)
    )

    @Test
    fun `zeigt zuerst Loading und danach den Inhalt`() = runTest {
        val gate = CompletableDeferred<CharacterResult>()
        coEvery { repository.getCharacter() } coAnswers { gate.await() }

        val vm = viewModel()
        assertEquals(CharacterUiState.Loading, vm.uiState.value)

        gate.complete(CharacterResult.Success(character))
        assertEquals(CharacterUiState.Content::class, vm.uiState.value::class)
    }

    @Test
    fun `Erfolg liefert Content mit aus den EP abgeleitetem Fortschritt`() = runTest {
        coEvery { repository.getCharacter() } returns CharacterResult.Success(character)

        val content = viewModel().uiState.value as CharacterUiState.Content

        assertEquals(character, content.character)
        assertEquals(2, content.progress.level)
        assertEquals(50L, content.progress.xpIntoLevel)
        assertEquals(200L, content.progress.xpForLevel)
        assertEquals(0.25f, content.progress.fractionToNextLevel, 0.0001f)
    }

    @Test
    fun `Leerergebnis liefert den Leerzustand`() = runTest {
        coEvery { repository.getCharacter() } returns CharacterResult.Empty

        assertEquals(CharacterUiState.Empty, viewModel().uiState.value)
    }

    @Test
    fun `Netzwerkfehler liefert den Fehlerzustand mit Netz-Meldung`() = runTest {
        coEvery { repository.getCharacter() } returns CharacterResult.NetworkError

        val error = viewModel().uiState.value as CharacterUiState.Error
        assertEquals(R.string.character_error_network, error.messageRes)
    }

    @Test
    fun `sonstiger Fehler liefert den Fehlerzustand mit generischer Meldung`() = runTest {
        coEvery { repository.getCharacter() } returns CharacterResult.Failed

        val error = viewModel().uiState.value as CharacterUiState.Error
        assertEquals(R.string.character_error_generic, error.messageRes)
    }

    @Test
    fun `onRetry laedt den Charakter erneut`() = runTest {
        coEvery { repository.getCharacter() } returns CharacterResult.NetworkError
        val vm = viewModel()
        assertEquals(CharacterUiState.Error::class, vm.uiState.value::class)

        coEvery { repository.getCharacter() } returns CharacterResult.Success(character)
        vm.onRetry()

        assertEquals(CharacterUiState.Content::class, vm.uiState.value::class)
        coVerify(exactly = 2) { repository.getCharacter() }
    }

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }
}
