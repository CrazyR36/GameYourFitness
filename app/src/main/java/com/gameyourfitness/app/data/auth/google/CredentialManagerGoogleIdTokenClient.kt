package com.gameyourfitness.app.data.auth.google

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.gameyourfitness.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Echte ID-Token-Beschaffung ueber die Credential Manager API.
 * Wichtig: serverClientId ist die WEB-Client-ID (nicht die Android-Client-ID) —
 * GoTrue prueft das Token gegen genau diese Audience (siehe README).
 */
@Singleton
class CredentialManagerGoogleIdTokenClient @Inject constructor(private val activityProvider: CurrentActivityProvider) :
    GoogleIdTokenClient {
    override suspend fun fetchIdToken(): GoogleIdTokenResult {
        val activity = activityProvider.currentActivity
            ?: return GoogleIdTokenResult.Failure("Keine aktive Activity")
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty()) {
            return GoogleIdTokenResult.Failure("GOOGLE_WEB_CLIENT_ID ist nicht konfiguriert")
        }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val credential = CredentialManager.create(activity)
                .getCredential(activity, request)
                .credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleIdTokenResult.Failure("Unerwarteter Credential-Typ: ${credential.type}")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleIdTokenResult.Cancelled
        } catch (e: GetCredentialException) {
            GoogleIdTokenResult.Failure(e.message)
        }
    }
}
