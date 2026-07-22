package com.gameyourfitness.app.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.gameyourfitness.app.domain.character.Character
import com.gameyourfitness.app.domain.progression.LevelProgress
import com.gameyourfitness.app.ui.theme.Dimens

/**
 * Charakterbildschirm im "System-Fenster"-Stil. Stateless: bekommt State + Callbacks,
 * damit er einzeln testbar und screenshot-fähig ist (CLAUDE.md Abschnitt 5/7).
 *
 * Titel und Abmelden-Button sind in JEDEM Zustand sichtbar; nur der mittlere Bereich
 * wechselt zwischen Laden, Inhalt, Leer- und Fehlerzustand.
 */
@Composable
fun CharacterScreen(
    state: CharacterUiState,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("character_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.screenPadding)
                .border(Dimens.systemWindowBorder, MaterialTheme.colorScheme.primary)
                .padding(Dimens.systemWindowPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)
        ) {
            Text(
                text = stringResource(R.string.character_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("character_title")
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.secondary)

            when (state) {
                CharacterUiState.Loading -> LoadingBody()
                is CharacterUiState.Content -> CharacterBody(state.character, state.progress)
                CharacterUiState.Empty -> EmptyBody()
                is CharacterUiState.Error -> ErrorBody(state, onRetry)
            }

            val signOutLabel = stringResource(R.string.character_sign_out)
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("character_sign_out")
                    .semantics { contentDescription = signOutLabel }
            ) {
                Text(text = signOutLabel)
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("character_loading")
        )
    }
}

@Composable
private fun EmptyBody() {
    Text(
        text = stringResource(R.string.character_empty),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("character_empty")
    )
}

@Composable
private fun ErrorBody(state: CharacterUiState.Error, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.contentSpacing)
    ) {
        Text(
            text = stringResource(state.messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("character_error")
        )
        val retryLabel = stringResource(R.string.character_retry)
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("character_retry_button")
                .semantics { contentDescription = retryLabel }
        ) {
            Text(text = retryLabel)
        }
    }
}

@Composable
private fun CharacterBody(character: Character, progress: LevelProgress) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            KeyValueBlock(
                label = stringResource(R.string.character_level_label),
                value = progress.level.toString(),
                valueTag = "character_level"
            )
            KeyValueBlock(
                label = stringResource(R.string.character_rank_label),
                value = character.rank.name,
                valueTag = "character_rank"
            )
        }

        XpBar(progress)

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.statSpacing)) {
            Text(
                text = stringResource(R.string.character_stats_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            StatRow(R.string.character_stat_str, character.stats.strength, "character_stat_str")
            StatRow(R.string.character_stat_vit, character.stats.vitality, "character_stat_vit")
            StatRow(R.string.character_stat_agi, character.stats.agility, "character_stat_agi")
            StatRow(R.string.character_stat_per, character.stats.perception, "character_stat_per")
        }
    }
}

@Composable
private fun KeyValueBlock(label: String, value: String, valueTag: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(valueTag)
        )
    }
}

@Composable
private fun XpBar(progress: LevelProgress) {
    val xpText = stringResource(R.string.character_xp_format, progress.xpIntoLevel, progress.xpForLevel)
    val barDescription = stringResource(
        R.string.character_xp_content_description,
        progress.xpIntoLevel,
        progress.xpForLevel
    )
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.contentSpacing)) {
        LinearProgressIndicator(
            progress = { progress.fractionToNextLevel },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.xpBarHeight)
                .testTag("character_xp_bar")
                .semantics { contentDescription = barDescription }
        )
        Text(
            text = xpText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag("character_xp_text")
        )
    }
}

@Composable
private fun StatRow(labelRes: Int, value: Int, valueTag: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.testTag(valueTag)
        )
    }
}
