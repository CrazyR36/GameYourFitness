package com.gameyourfitness.app.data.character

import com.gameyourfitness.app.domain.character.Character
import com.gameyourfitness.app.domain.character.Stats
import com.gameyourfitness.app.domain.progression.Rank
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Schmaler PostgREST-Ausschnitt, den der Charakter-Slice braucht: eine Zeile aus
 * `public.profiles` lesen. Eigenes Interface, damit E2E-Tests genau diesen Zugriff
 * ersetzen können (Offline-Fall, siehe Issue #3) — analog zu `GoTrueApi` (#2).
 */
interface CharacterApi {
    /**
     * Liest die Profilzeile des Nutzers zum [accessToken]. RLS liefert ohnehin nur
     * die eigene Zeile; `null`, wenn keine existiert.
     *
     * @throws PostgrestHttpException bei HTTP-Fehlerstatus.
     * @throws java.io.IOException bei Netzwerkfehlern.
     */
    suspend fun fetchCharacterRow(accessToken: String): CharacterRow?
}

/** Rohzeile aus `public.profiles` (nur die für den Charakter relevanten Spalten). */
@Serializable
data class CharacterRow(
    @SerialName("user_id") val userId: String,
    @SerialName("total_xp") val totalXp: Long,
    val rank: String,
    val strength: Int,
    val vitality: Int,
    val agility: Int,
    val perception: Int
)

/**
 * Bildet die Rohzeile auf das Domänenmodell ab. Unbekannte Rang-Strings fallen auf
 * [Rank.E] zurück (die DB-Check-Constraint hält gültige Werte ohnehin klein).
 */
fun CharacterRow.toCharacter(): Character = Character(
    userId = userId,
    totalXp = totalXp,
    rank = runCatching { Rank.valueOf(rank) }.getOrDefault(Rank.E),
    stats = Stats(strength = strength, vitality = vitality, agility = agility, perception = perception)
)

/** PostgREST hat mit einem HTTP-Fehlerstatus geantwortet (kein Netzwerkfehler). */
class PostgrestHttpException(val statusCode: Int, message: String) :
    Exception("PostgREST-Fehler $statusCode: $message")
