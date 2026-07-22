package com.gameyourfitness.app.domain.progression

/**
 * Jäger-Rang im Solo-Leveling-Stil: E (Anfang) bis S (Spitze). Der Rang ist
 * bewusst KEIN reines Level-Derivat — er wird ab #10 über einen Aufstiegstest
 * verdient. [Progression.minLevelForRank] definiert nur die Voraussetzung.
 */
enum class Rank {
    E,
    D,
    C,
    B,
    A,
    S
}
