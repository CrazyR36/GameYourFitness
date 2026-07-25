package com.gameyourfitness.app.ui.theme

/**
 * Zentrale Bewegungs-Parameter (CLAUDE.md Abschnitt 7). Grundsatz: jede Animation laeuft in
 * ihren Zielwert und **settelt** — es gibt bewusst keine Endlos-/Idle-Animation, damit
 * Screenshot-Tests deterministisch bleiben (Roborazzi rendert den Endframe). Keine
 * Dauer-Literale in Composables.
 */
object Motion {
    // Dauer in Millisekunden.
    const val XP_FILL_MS = 650
    const val STATE_FADE_MS = 220
    const val POPUP_IN_MS = 320
    const val POPUP_OUT_MS = 180

    // Start-/Ziel-Skalierung fuer das Level-Up-Popup (Overshoot beim Einblenden).
    const val POPUP_INITIAL_SCALE = 0.85f
    const val POPUP_EXIT_SCALE = 0.9f
}
