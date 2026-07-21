package com.gameyourfitness.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import com.gameyourfitness.app.domain.auth.AuthState
import com.gameyourfitness.app.ui.auth.AuthViewModel
import com.gameyourfitness.app.ui.auth.LoginScreen
import com.gameyourfitness.app.ui.home.HomeScreen

/**
 * Schaltet ohne Navigation-Library zwischen Login- und Startbildschirm anhand
 * des Auth-Zustands. Navigation-Compose kommt erst mit echten Navigationszielen
 * (siehe Issue #2, Umsetzungsentscheidungen).
 */
@Composable
fun AppRoot(modifier: Modifier = Modifier, viewModel: AuthViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState.authState) {
        AuthState.Unknown ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("splash_loading"),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

        AuthState.SignedOut ->
            LoginScreen(
                isSigningIn = uiState.isSigningIn,
                errorMessageRes = uiState.errorMessageRes,
                onSignInClick = viewModel::onSignInClick,
                modifier = modifier
            )

        is AuthState.SignedIn ->
            HomeScreen(
                onSignOut = viewModel::onSignOutClick,
                modifier = modifier
            )
    }
}
