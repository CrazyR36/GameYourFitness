package com.gameyourfitness.app.ui.character

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gameyourfitness.app.R
import com.gameyourfitness.app.domain.character.Character
import com.gameyourfitness.app.domain.character.CharacterRepository
import com.gameyourfitness.app.domain.character.CharacterResult
import com.gameyourfitness.app.domain.progression.LevelProgress
import com.gameyourfitness.app.domain.progression.Progression
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Gesamtzustand des Charakterbildschirms — genau ein StateFlow (CLAUDE.md Abschnitt 5).
 * Deckt Lade-, Inhalts-, Leer- und Fehlerzustand ab (CLAUDE.md Abschnitt 7).
 */
sealed interface CharacterUiState {
    data object Loading : CharacterUiState

    data class Content(val character: Character, val progress: LevelProgress) : CharacterUiState

    data object Empty : CharacterUiState

    data class Error(@StringRes val messageRes: Int) : CharacterUiState
}

@HiltViewModel
class CharacterViewModel @Inject constructor(private val characterRepository: CharacterRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<CharacterUiState>(CharacterUiState.Loading)
    val uiState: StateFlow<CharacterUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetry() = load()

    /** Lädt den Charakter neu — z. B. nachdem ein Krafttraining EP vergeben hat (#4). */
    fun reload() = load()

    private fun load() {
        _uiState.value = CharacterUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = characterRepository.getCharacter()) {
                is CharacterResult.Success -> CharacterUiState.Content(
                    character = result.character,
                    progress = Progression.levelProgress(result.character.totalXp)
                )

                CharacterResult.Empty -> CharacterUiState.Empty
                CharacterResult.NetworkError -> CharacterUiState.Error(R.string.character_error_network)
                CharacterResult.Failed -> CharacterUiState.Error(R.string.character_error_generic)
            }
        }
    }
}
