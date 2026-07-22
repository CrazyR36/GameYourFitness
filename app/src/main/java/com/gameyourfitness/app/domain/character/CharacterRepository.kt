package com.gameyourfitness.app.domain.character

/**
 * Ergebnis des Charakter-Abrufs. Deckt die drei Bildschirmzustände ab
 * (CLAUDE.md Abschnitt 7): [Success] → Inhalt, [Empty] → Leerzustand,
 * [NetworkError]/[Failed] → Fehlerzustand.
 */
sealed interface CharacterResult {
    data class Success(val character: Character) : CharacterResult

    /** Backend lieferte keine Charakterzeile (z. B. Profil noch nicht angelegt). */
    data object Empty : CharacterResult

    /** Kein Netz / Backend nicht erreichbar. */
    data object NetworkError : CharacterResult

    /** Sonstiger Fehler (HTTP-Fehler, keine gültige Session). */
    data object Failed : CharacterResult
}

/**
 * Einziger Zugang der UI zum Charakter-Spielstand (CLAUDE.md Abschnitt 5:
 * Backend-Zugriff ausschließlich über Repository-Interfaces im domain-Layer).
 */
interface CharacterRepository {
    suspend fun getCharacter(): CharacterResult
}
