package com.gameyourfitness.app.ui.character

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.gameyourfitness.app.R
import com.gameyourfitness.app.domain.character.Character
import com.gameyourfitness.app.domain.progression.LevelProgress
import com.gameyourfitness.app.ui.theme.Dimens
import com.gameyourfitness.app.ui.theme.Motion
import com.gameyourfitness.app.ui.theme.Shapes
import com.gameyourfitness.app.ui.theme.SystemDivider
import com.gameyourfitness.app.ui.theme.atmosphericBackground
import com.gameyourfitness.app.ui.theme.systemWindow

/**
 * Charakterbildschirm im "System-Fenster"-Stil. Stateless: bekommt State + Callbacks,
 * damit er einzeln testbar und screenshot-fähig ist (CLAUDE.md Abschnitt 5/7).
 *
 * Titel und Abmelden-Button sind in JEDEM Zustand sichtbar; nur der mittlere Bereich
 * wechselt zwischen Laden, Inhalt, Leer- und Fehlerzustand — mit weichem Übergang.
 */
@Composable
fun CharacterScreen(
    state: CharacterUiState,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onLogWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .atmosphericBackground()
            .testTag("character_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.screenPadding)
                .systemWindow()
                .padding(Dimens.systemWindowPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing, Alignment.CenterVertically)
        ) {
            Text(
                text = stringResource(R.string.character_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("character_title")
            )
            SystemDivider()

            // Uebergang nur bei Zustands-Wechsel (Typ), nicht bei Datenaktualisierung:
            // contentKey nach Klasse haelt Content->Content in-place (keine doppelten TestTags).
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = {
                    fadeIn(tween(Motion.STATE_FADE_MS)) togetherWith fadeOut(tween(Motion.STATE_FADE_MS))
                },
                label = "character_body"
            ) { current ->
                when (current) {
                    CharacterUiState.Loading -> LoadingBody()
                    is CharacterUiState.Content -> CharacterBody(current.character, current.progress, onLogWorkout)
                    CharacterUiState.Empty -> EmptyBody()
                    is CharacterUiState.Error -> ErrorBody(current, onRetry)
                }
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
private fun CharacterBody(character: Character, progress: LevelProgress, onLogWorkout: () -> Unit) {
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

        val logWorkoutLabel = stringResource(R.string.character_log_workout)
        Button(
            onClick = onLogWorkout,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("character_log_workout")
                .semantics { contentDescription = logWorkoutLabel }
        ) {
            Text(text = logWorkoutLabel)
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
    // Der Balken laeuft weich in den Zielwert (fuehlt sich nach EP-Gewinn an) und settelt
    // auf der ersten Komposition sofort auf dem echten Wert → Screenshot-deterministisch.
    val animatedFraction by animateFloatAsState(
        targetValue = progress.fractionToNextLevel,
        animationSpec = tween(Motion.XP_FILL_MS, easing = FastOutSlowInEasing),
        label = "xp_fill"
    )
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.contentSpacing)) {
        LinearProgressIndicator(
            progress = { animatedFraction },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.xpBarHeight)
                .clip(Shapes.bar)
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
