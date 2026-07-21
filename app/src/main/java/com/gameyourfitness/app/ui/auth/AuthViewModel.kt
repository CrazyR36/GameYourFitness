package com.gameyourfitness.app.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gameyourfitness.app.R
import com.gameyourfitness.app.domain.auth.AuthRepository
import com.gameyourfitness.app.domain.auth.AuthState
import com.gameyourfitness.app.domain.auth.SignInResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Gesamtzustand rund ums Anmelden — genau ein StateFlow (CLAUDE.md Abschnitt 5).
 */
data class AuthUiState(
    val authState: AuthState = AuthState.Unknown,
    val isSigningIn: Boolean = false,
    @StringRes val errorMessageRes: Int? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {
    private data class SignInProgress(val isSigningIn: Boolean = false, @StringRes val errorMessageRes: Int? = null)

    private val signInProgress = MutableStateFlow(SignInProgress())

    // Eagerly statt WhileSubscribed: Der Auth-Zustand ist app-lebenslang relevant
    // und soll auch ohne aktiven Collector aktuell sein (macht zudem die
    // value-basierten Unit-Tests deterministisch).
    val uiState: StateFlow<AuthUiState> =
        combine(authRepository.authState, signInProgress) { auth, progress ->
            AuthUiState(
                authState = auth,
                isSigningIn = progress.isSigningIn,
                errorMessageRes = progress.errorMessageRes
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AuthUiState())

    init {
        viewModelScope.launch { authRepository.restoreSession() }
    }

    fun onSignInClick() {
        if (signInProgress.value.isSigningIn) {
            return
        }
        signInProgress.value = SignInProgress(isSigningIn = true, errorMessageRes = null)
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle()
            signInProgress.value = SignInProgress(
                isSigningIn = false,
                errorMessageRes = result.toErrorMessageRes()
            )
        }
    }

    fun onSignOutClick() {
        viewModelScope.launch {
            authRepository.signOut()
            signInProgress.value = SignInProgress()
        }
    }

    @StringRes
    private fun SignInResult.toErrorMessageRes(): Int? = when (this) {
        SignInResult.Success -> null
        SignInResult.Cancelled -> R.string.login_error_cancelled
        SignInResult.NetworkError -> R.string.login_error_network
        SignInResult.Failed -> R.string.login_error_generic
    }
}
