package com.gameyourfitness.app.auth

import com.gameyourfitness.app.data.auth.gotrue.GoTrueApi
import com.gameyourfitness.app.data.auth.gotrue.GoTrueHttpApi
import com.gameyourfitness.app.data.auth.gotrue.SessionDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock-Grenze fuer Google-SSO (Issue #2): Nur der Tausch ID-Token→Session wird
 * ersetzt (GoTrue kann lokal gefaelschte Google-Tokens nicht akzeptieren) —
 * stattdessen liefert ein echter GoTrue-Signup die Session. Refresh und Logout
 * laufen unveraendert gegen das echte GoTrue.
 */
@Singleton
class TestGoTrueApi @Inject constructor(private val real: GoTrueHttpApi, private val testBackend: GoTrueTestBackend) :
    GoTrueApi {
    override suspend fun signInWithIdToken(idToken: String): SessionDto = testBackend.signUpNewUser()

    override suspend fun refresh(refreshToken: String): SessionDto = real.refresh(refreshToken)

    override suspend fun signOut(accessToken: String) = real.signOut(accessToken)
}
