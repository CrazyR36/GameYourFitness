package com.gameyourfitness.app.ui.character

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Stateful-Hülle: hostet das [CharacterViewModel] und reicht State + Callbacks an den
 * stateless [CharacterScreen]. Das Abmelden liegt weiterhin beim Auth-Zustand
 * (Callback aus [com.gameyourfitness.app.ui.AppRoot]).
 */
@Composable
fun CharacterRoute(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CharacterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    CharacterScreen(
        state = uiState,
        onRetry = viewModel::onRetry,
        onSignOut = onSignOut,
        modifier = modifier
    )
}
