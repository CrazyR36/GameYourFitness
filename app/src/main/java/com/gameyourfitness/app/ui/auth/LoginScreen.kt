package com.gameyourfitness.app.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.gameyourfitness.app.R
import com.gameyourfitness.app.ui.theme.Dimens
import com.gameyourfitness.app.ui.theme.SystemDivider
import com.gameyourfitness.app.ui.theme.atmosphericBackground
import com.gameyourfitness.app.ui.theme.systemWindow

/**
 * Login-Screen im "System-Fenster"-Stil. Stateless: bekommt State + Callback,
 * damit er einzeln testbar und screenshot-faehig ist (CLAUDE.md Abschnitt 5/7).
 */
@Composable
fun LoginScreen(
    isSigningIn: Boolean,
    @StringRes errorMessageRes: Int?,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .atmosphericBackground()
            .testTag("login_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.screenPadding)
                .systemWindow()
                .padding(Dimens.systemWindowPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.contentSpacing, Alignment.CenterVertically)
        ) {
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("login_title")
            )
            SystemDivider()
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("login_subtitle")
            )

            if (errorMessageRes != null) {
                Text(
                    text = stringResource(errorMessageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("login_error")
                )
            }

            val signInLabel = stringResource(R.string.login_google_button)
            Button(
                onClick = onSignInClick,
                enabled = !isSigningIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_google_button")
                    .semantics { contentDescription = signInLabel }
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        strokeWidth = Dimens.progressStroke,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(Dimens.inlineProgressSize)
                            .testTag("login_progress")
                    )
                } else {
                    Text(text = signInLabel)
                }
            }
        }
    }
}
