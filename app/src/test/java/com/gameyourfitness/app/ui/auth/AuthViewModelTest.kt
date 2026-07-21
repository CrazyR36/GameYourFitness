package com.gameyourfitness.app.ui.auth

import com.gameyourfitness.app.R
import com.gameyourfitness.app.domain.auth.AuthRepository
import com.gameyourfitness.app.domain.auth.AuthState
import com.gameyourfitness.app.domain.auth.SignInResult
import com.gameyourfitness.app.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class AuthViewModelTest {
    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unknown)
    private val repository = mockk<AuthRepository>(relaxed = true) {
        every { authState } returns authStateFlow
    }

    private fun viewModel() = AuthViewModel(repository)

    @Test
    fun `beim Start wird die Session wiederhergestellt`() = runTest {
        viewModel()
        coVerify { repository.restoreSession() }
    }

    @Test
    fun `UiState spiegelt den Auth-Zustand aus dem Repository`() = runTest {
        val vm = viewModel()
        assertEquals(AuthState.Unknown, vm.uiState.value.authState)

        authStateFlow.value = AuthState.SignedIn("user-1", "a@example.com")

        assertEquals(AuthState.SignedIn("user-1", "a@example.com"), vm.uiState.value.authState)
    }

    @Test
    fun `waehrend des Logins ist isSigningIn aktiv und danach wieder inaktiv`() = runTest {
        val gate = CompletableDeferred<SignInResult>()
        coEvery { repository.signInWithGoogle() } coAnswers { gate.await() }
        authStateFlow.value = AuthState.SignedOut
        val vm = viewModel()

        vm.onSignInClick()
        assertTrue(vm.uiState.value.isSigningIn)

        gate.complete(SignInResult.Success)
        assertFalse(vm.uiState.value.isSigningIn)
    }

    @Test
    fun `waehrend eines laufenden Logins wird kein zweiter gestartet`() = runTest {
        val gate = CompletableDeferred<SignInResult>()
        coEvery { repository.signInWithGoogle() } coAnswers { gate.await() }
        authStateFlow.value = AuthState.SignedOut
        val vm = viewModel()

        vm.onSignInClick()
        vm.onSignInClick()
        gate.complete(SignInResult.Success)

        coVerify(exactly = 1) { repository.signInWithGoogle() }
    }

    @Test
    fun `abgebrochener Login zeigt die Abbruch-Fehlermeldung`() = runTest {
        coEvery { repository.signInWithGoogle() } returns SignInResult.Cancelled
        authStateFlow.value = AuthState.SignedOut
        val vm = viewModel()

        vm.onSignInClick()

        assertEquals(R.string.login_error_cancelled, vm.uiState.value.errorMessageRes)
        assertFalse(vm.uiState.value.isSigningIn)
    }

    @Test
    fun `Netzwerkfehler zeigt die Netzwerk-Fehlermeldung`() = runTest {
        coEvery { repository.signInWithGoogle() } returns SignInResult.NetworkError
        authStateFlow.value = AuthState.SignedOut
        val vm = viewModel()

        vm.onSignInClick()

        assertEquals(R.string.login_error_network, vm.uiState.value.errorMessageRes)
    }

    @Test
    fun `sonstiger Fehler zeigt die allgemeine Fehlermeldung`() = runTest {
        coEvery { repository.signInWithGoogle() } returns SignInResult.Failed
        authStateFlow.value = AuthState.SignedOut
        val vm = viewModel()

        vm.onSignInClick()

        assertEquals(R.string.login_error_generic, vm.uiState.value.errorMessageRes)
    }

    @Test
    fun `erfolgreicher Login setzt keine Fehlermeldung`() = runTest {
        coEvery { repository.signInWithGoogle() } returns SignInResult.Success
        authStateFlow.value = AuthState.SignedOut
        val vm = viewModel()

        vm.onSignInClick()

        assertNull(vm.uiState.value.errorMessageRes)
        assertFalse(vm.uiState.value.isSigningIn)
    }

    @Test
    fun `erneuter Login-Versuch entfernt die alte Fehlermeldung`() = runTest {
        // Erster Versuch schlaegt fehl → Fehlermeldung gesetzt.
        coEvery { repository.signInWithGoogle() } returns SignInResult.Cancelled
        authStateFlow.value = AuthState.SignedOut
        val vm = viewModel()
        vm.onSignInClick()
        assertEquals(R.string.login_error_cancelled, vm.uiState.value.errorMessageRes)

        // Zweiter Versuch haengt: waehrend des Ladens ist die alte Meldung weg.
        val gate = CompletableDeferred<SignInResult>()
        coEvery { repository.signInWithGoogle() } coAnswers { gate.await() }
        vm.onSignInClick()

        assertNull(vm.uiState.value.errorMessageRes)
        assertTrue(vm.uiState.value.isSigningIn)
        gate.complete(SignInResult.Success)
    }

    @Test
    fun `Abmelden delegiert an das Repository`() = runTest {
        authStateFlow.value = AuthState.SignedIn("user-1", null)
        val vm = viewModel()

        vm.onSignOutClick()

        coVerify { repository.signOut() }
    }

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }
}
