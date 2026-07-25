package com.gameyourfitness.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance

// Geometrie des atmosphaerischen Hintergrund-Glows (keine Magic Numbers im Composable).
private object BackdropTokens {
    const val CENTER_Y_FRACTION = 0.32f
    const val RADIUS_FACTOR = 0.75f
}

/**
 * Das „System-Fenster": Glow-Schatten + Surface-Fuellung + Gradient-Rand in einem Zug
 * (CLAUDE.md Abschnitt 7). Der Glow ist **statisch** (kein Idle-/Endlos-Effekt) und damit
 * screenshot-deterministisch. Ersetzt das frühere `.background(...).border(...)`-Paar.
 */
@Composable
fun Modifier.systemWindow(shape: Shape = Shapes.systemWindow): Modifier {
    val scheme = MaterialTheme.colorScheme
    return this
        .shadow(
            elevation = Dimens.glowElevation,
            shape = shape,
            ambientColor = scheme.primary,
            spotColor = scheme.secondary
        )
        .background(scheme.surface, shape)
        .border(
            width = Dimens.systemWindowBorder,
            brush = Brush.linearGradient(listOf(scheme.primary, scheme.secondary, scheme.tertiary)),
            shape = shape
        )
}

/**
 * Atmosphaerische Buehne hinter einem Vollbild-Screen: dunkler Grund mit einem weichen
 * radialen Glow im oberen Drittel, damit das Fenster ueber Tiefe schwebt statt auf einer
 * platten Farbflaeche zu sitzen. Statisch → screenshot-deterministisch; im Light-Theme
 * automatisch dezenter (ueber die Helligkeit des Hintergrunds).
 */
@Composable
fun Modifier.atmosphericBackground(): Modifier {
    val scheme = MaterialTheme.colorScheme
    val glowAlpha = if (scheme.background.luminance() < Alpha.DARK_LUMINANCE_THRESHOLD) {
        Alpha.BACKDROP_GLOW_DARK
    } else {
        Alpha.BACKDROP_GLOW_LIGHT
    }
    val glow = scheme.primary.copy(alpha = glowAlpha)
    val base = scheme.background
    return this.drawBehind {
        drawRect(base)
        drawRect(
            Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = center.copy(y = size.height * BackdropTokens.CENTER_Y_FRACTION),
                radius = size.maxDimension * BackdropTokens.RADIUS_FACTOR
            )
        )
    }
}

/**
 * HUD-Trennlinie: ein duenner horizontaler Gradient, der an den Raendern ausblendet —
 * wirkt wie eine Interface-Naht statt wie ein flacher Material-Divider. Ersetzt
 * `HorizontalDivider` in den System-Fenstern.
 */
@Composable
fun SystemDivider(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.dividerThickness)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, scheme.primary, scheme.secondary, Color.Transparent)
                )
            )
    )
}
