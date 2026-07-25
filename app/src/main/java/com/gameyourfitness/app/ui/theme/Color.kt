package com.gameyourfitness.app.ui.theme

import androidx.compose.ui.graphics.Color

// Dunkles Theme (Standard): tiefes Nachtblau mit Blau/Violett-Akzenten.
val NightBackground = Color(0xFF0B0F1A)
val NightSurface = Color(0xFF121A2B)
val GlowBlue = Color(0xFF4F8CFF)
val GlowViolet = Color(0xFF8B5CF6)
val GlowCyan = Color(0xFF38E1FF)
val NightOnBackground = Color(0xFFE3EAFB)
val NightOnSurface = Color(0xFFCBD5F0)
val AlertRed = Color(0xFFFF5470)

// Helles Theme (nur als Alternative, dunkel ist Standard).
val DayBackground = Color(0xFFF4F6FE)
val DaySurface = Color(0xFFFFFFFF)
val DayBlue = Color(0xFF2456D6)
val DayViolet = Color(0xFF6B3FD1)
val DayCyan = Color(0xFF0E7490)
val DayOnBackground = Color(0xFF141A2A)
val DayOnSurface = Color(0xFF232B40)
val DayAlertRed = Color(0xFFB3213C)

/**
 * Deckkraft-Token (keine Magic Numbers in Composables). Der Glow des atmosphaerischen
 * Hintergrunds ist auf Dunkel gerechnet und im Light-Theme deutlich dezenter, damit es
 * nicht schmutzig wirkt; die Umschaltung erfolgt ueber die Hintergrund-Helligkeit.
 */
object Alpha {
    const val SCRIM = 0.72f
    const val BACKDROP_GLOW_DARK = 0.12f
    const val BACKDROP_GLOW_LIGHT = 0.05f
    const val DARK_LUMINANCE_THRESHOLD = 0.5f
}
