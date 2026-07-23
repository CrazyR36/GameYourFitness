package com.gameyourfitness.app.ui.levelup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.gameyourfitness.app.R
import com.gameyourfitness.app.domain.progression.Progression
import com.gameyourfitness.app.ui.theme.Dimens

// Deckkraft des abdunkelnden Hintergrunds hinter dem Popup (UI-Token, keine Magic Number).
private const val SCRIM_ALPHA = 0.72f

/**
 * Animiertes „System-Fenster"-Overlay für den Level-Aufstieg (#5). Blendet über
 * [AnimatedVisibility] ein/aus (auf dem CI-Emulator sind Animationen deaktiviert → testbar).
 * [level] `null` = ausgeblendet; beim Ausblenden zeigt das Popup weiterhin das zuletzt
 * erreichte Level, damit die Exit-Animation nicht auf eine leere Zahl springt.
 */
@Composable
fun LevelUpOverlay(level: Int?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var lastLevel by remember { mutableIntStateOf(Progression.START_LEVEL) }
    LaunchedEffect(level) {
        if (level != null) lastLevel = level
    }
    AnimatedVisibility(
        visible = level != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .testTag("level_up_overlay"),
            contentAlignment = Alignment.Center
        ) {
            LevelUpPopup(level = lastLevel, onDismiss = onDismiss)
        }
    }
}

/**
 * Stateless Inhalt des Level-Up-Popups: Titel, das neue Level als große Zahl und ein
 * „Weiter"-Button. Screenshot-fähig ohne Animation (die steckt in [LevelUpOverlay]).
 */
@Composable
fun LevelUpPopup(level: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.level_up_content_description, level)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.screenPadding)
            .background(MaterialTheme.colorScheme.background)
            .border(Dimens.systemWindowBorder, MaterialTheme.colorScheme.primary)
            .padding(Dimens.systemWindowPadding)
            .testTag("level_up_popup")
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)
    ) {
        Text(
            text = stringResource(R.string.level_up_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("level_up_title")
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.secondary)
        Text(
            text = stringResource(R.string.character_level_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = level.toString(),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("level_up_level")
        )
        val dismissLabel = stringResource(R.string.level_up_dismiss)
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("level_up_dismiss")
                .semantics { contentDescription = dismissLabel }
        ) {
            Text(text = dismissLabel)
        }
    }
}
