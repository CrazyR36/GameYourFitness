package com.gameyourfitness.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GlowBlue,
    secondary = GlowViolet,
    tertiary = GlowCyan,
    background = NightBackground,
    surface = NightSurface,
    onPrimary = NightBackground,
    onSecondary = NightOnBackground,
    onBackground = NightOnBackground,
    onSurface = NightOnSurface,
    error = AlertRed,
)

private val LightColorScheme = lightColorScheme(
    primary = DayBlue,
    secondary = DayViolet,
    tertiary = DayCyan,
    background = DayBackground,
    surface = DaySurface,
    onPrimary = DaySurface,
    onSecondary = DayBackground,
    onBackground = DayOnBackground,
    onSurface = DayOnSurface,
    error = DayAlertRed,
)

/**
 * Zentrales App-Theme. Dunkel ist der Produktstandard (CLAUDE.md Abschnitt 7),
 * deshalb ist [darkTheme] nicht an die Systemeinstellung gekoppelt.
 */
@Composable
fun GameYourFitnessTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content,
    )
}
