package com.gameyourfitness.app.domain.character

import com.gameyourfitness.app.domain.progression.Rank

/**
 * Die vier Grundwerte eines Charakters (Solo-Leveling-Stil).
 * STR = Kraft, VIT = Ausdauer/Vitalität, AGI = Beweglichkeit, PER = Wahrnehmung/Disziplin.
 */
data class Stats(
    val strength: Int,
    val vitality: Int,
    val agility: Int,
    val perception: Int
)

/**
 * Spielstand eines Nutzers, wie ihn der Charakterbildschirm anzeigt.
 *
 * Das Level ist bewusst NICHT gespeichert, sondern wird per
 * [com.gameyourfitness.app.domain.progression.Progression] aus [totalXp] abgeleitet
 * (eine Quelle der Wahrheit — CLAUDE.md Abschnitt 6).
 */
data class Character(
    val userId: String,
    val totalXp: Long,
    val rank: Rank,
    val stats: Stats
)
