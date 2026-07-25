package com.gameyourfitness.app.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.unit.dp

/**
 * Kantige „System-Interface"-Ecken (RPG/HUD-Look, CLAUDE.md Abschnitt 7). Abgeschraegte
 * statt gerundeter Ecken betonen die technische Anmutung. Die System-Fenster sind bewusst
 * asymmetrisch angeschnitten (oben-links + unten-rechts), damit sie wie ein Terminal-Panel
 * wirken statt wie ein Formular. Zentral hier — keine Shape-Literale in Composables.
 */
object Shapes {
    // Vollflaechige System-Fenster und Popups.
    val systemWindow = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp)

    // Kleinere Panels/Flaechen innerhalb eines Fensters.
    val panelInset = CutCornerShape(8.dp)

    // EP-/Fortschrittsbalken.
    val bar = CutCornerShape(4.dp)
}
