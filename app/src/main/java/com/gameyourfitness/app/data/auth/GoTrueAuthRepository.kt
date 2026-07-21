package com.gameyourfitness.app.data.auth

import com.gameyourfitness.app.data.auth.google.GoogleIdTokenClient
import com.gameyourfitness.app.data.auth.google.GoogleIdTokenResult
import com.gameyourfitness.app.data.auth.gotrue.GoTrueApi
import com.gameyourfitness.app.data.auth.gotrue.GoTrueHttpException
import com.gameyourfitness.app.data.auth.gotrue.toAuthSession
import com.gameyourfitness.app.domain.auth.AuthRepository
import com.gameyourfitness.app.domain.auth.AuthSession
import com.gameyourfitness.app.domain.auth.AuthState
import com.gameyourfitness.app.domain.auth.SignInResult
import com.gameyourfitness.app.domain.time.EpochClock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class GoTrueAuthRepository @Inject constructor(
    private val googleIdTokenClient: GoogleIdTokenClient,
    private val goTrueApi: GoTrueApi,
    private val sessionStore: SessionStore,
    private val clock: EpochClock
) : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override suspend fun restoreSession() {
        val stored = sessionStore.load()
        if (stored == null) {
            _authState.value = AuthState.SignedOut
            return
        }
        if (!stored.isExpired(clock.now())) {
            _authState.value = stored.toSignedIn()
            return
        }
        refreshExpiredSession(stored)
    }

    private suspend fun refreshExpiredSession(stored: AuthSession) {
        try {
            val fresh = goTrueApi.refresh(stored.refreshToken).toAuthSession(clock.now())
            sessionStore.save(fresh)
            _authState.value = fresh.toSignedIn()
        } catch (_: GoTrueHttpException) {
            // GoTrue lehnt den Refresh-Token ab → Session ist wirklich ungueltig.
            sessionStore.clear()
            _authState.value = AuthState.SignedOut
        } catch (_: IOException) {
            // Kein Netz ist kein Grund zum Abmelden: Session behalten, Refresh
            // passiert beim naechsten Anlauf.
            _authState.value = stored.toSignedIn()
        }
    }

    override suspend fun signInWithGoogle(): SignInResult =
        when (val tokenResult = googleIdTokenClient.fetchIdToken()) {
            is GoogleIdTokenResult.Cancelled -> SignInResult.Cancelled
            is GoogleIdTokenResult.Failure -> SignInResult.Failed
            is GoogleIdTokenResult.Success -> exchangeIdToken(tokenResult.idToken)
        }

    private suspend fun exchangeIdToken(idToken: String): SignInResult = try {
        val session = goTrueApi.signInWithIdToken(idToken).toAuthSession(clock.now())
        sessionStore.save(session)
        _authState.value = session.toSignedIn()
        SignInResult.Success
    } catch (_: IOException) {
        SignInResult.NetworkError
    } catch (_: GoTrueHttpException) {
        SignInResult.Failed
    }

    override suspend fun signOut() {
        val stored = sessionStore.load()
        if (stored != null) {
            try {
                goTrueApi.signOut(stored.accessToken)
            } catch (_: IOException) {
                // Lokal wird trotzdem abgemeldet; der Server-Token laeuft von selbst ab.
            } catch (_: GoTrueHttpException) {
                // dito
            }
        }
        sessionStore.clear()
        _authState.value = AuthState.SignedOut
    }

    private fun AuthSession.toSignedIn() = AuthState.SignedIn(userId = userId, email = email)
}
