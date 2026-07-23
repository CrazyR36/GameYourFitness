package com.gameyourfitness.app.domain.progression

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgressionTest {
    @Test
    fun `kumulative EP-Schwellen folgen der quadratischen Kurve`() {
        assertEquals(0L, Progression.xpToReachLevel(1))
        assertEquals(100L, Progression.xpToReachLevel(2))
        assertEquals(300L, Progression.xpToReachLevel(3))
        assertEquals(600L, Progression.xpToReachLevel(4))
        assertEquals(1000L, Progression.xpToReachLevel(5))
        assertEquals(4500L, Progression.xpToReachLevel(10))
    }

    @Test
    fun `Level unter dem Startlevel ist unzulaessig`() {
        assertThrows(IllegalArgumentException::class.java) { Progression.xpToReachLevel(0) }
    }

    @Test
    fun `levelForXp liefert Startlevel bei 0 oder negativen EP`() {
        assertEquals(1, Progression.levelForXp(0))
        assertEquals(1, Progression.levelForXp(-500))
    }

    @Test
    fun `levelForXp trifft die Schwellen exakt (Grenzwerte)`() {
        assertEquals(1, Progression.levelForXp(99))
        assertEquals(2, Progression.levelForXp(100))
        assertEquals(2, Progression.levelForXp(101))
        assertEquals(2, Progression.levelForXp(299))
        assertEquals(3, Progression.levelForXp(300))
        assertEquals(3, Progression.levelForXp(599))
        assertEquals(4, Progression.levelForXp(600))
        assertEquals(9, Progression.levelForXp(4499))
        assertEquals(10, Progression.levelForXp(4500))
        assertEquals(10, Progression.levelForXp(4501))
    }

    @Test
    fun `levelForXp bleibt bei sehr grossen EP korrekt (geschlossene Form plus Korrektur)`() {
        // xpToReachLevel(141)=987000 <= 1_000_000 < 1_001_100 = xpToReachLevel(142)
        assertEquals(141, Progression.levelForXp(1_000_000))
        assertEquals(987_000L, Progression.xpToReachLevel(141))
        assertEquals(1_001_100L, Progression.xpToReachLevel(142))
    }

    @Test
    fun `levelProgress fuellt den Balken innerhalb des Levels korrekt`() {
        val start = Progression.levelProgress(0)
        assertEquals(1, start.level)
        assertEquals(0L, start.xpIntoLevel)
        assertEquals(100L, start.xpForLevel)
        assertEquals(0f, start.fractionToNextLevel, DELTA)

        val mid = Progression.levelProgress(50)
        assertEquals(1, mid.level)
        assertEquals(50L, mid.xpIntoLevel)
        assertEquals(100L, mid.xpForLevel)
        assertEquals(0.5f, mid.fractionToNextLevel, DELTA)

        val level2 = Progression.levelProgress(150)
        assertEquals(2, level2.level)
        assertEquals(50L, level2.xpIntoLevel)
        assertEquals(200L, level2.xpForLevel)
        assertEquals(0.25f, level2.fractionToNextLevel, DELTA)
    }

    @Test
    fun `levelProgress liefert direkt an der Schwelle einen leeren Balken des neuen Levels`() {
        val atThreshold = Progression.levelProgress(300)
        assertEquals(3, atThreshold.level)
        assertEquals(0L, atThreshold.xpIntoLevel)
        assertEquals(300L, atThreshold.xpForLevel)
        assertEquals(0f, atThreshold.fractionToNextLevel, DELTA)
    }

    @Test
    fun `levelProgress haelt den Fuellgrad stets zwischen 0 und 1`() {
        for (xp in longArrayOf(0, 1, 99, 100, 250, 599, 4500, 1_000_000)) {
            val fraction = Progression.levelProgress(xp).fractionToNextLevel
            assertTrue(fraction in 0f..1f) { "Fuellgrad $fraction fuer $xp EP ausserhalb [0,1]" }
        }
    }

    @Test
    fun `strengthWorkoutXp folgt der bestaetigungsfaehigen ersten Version`() {
        // saetze * wdh * (100 + gewicht) / 100 (Ganzzahl).
        assertEquals(40L, Progression.strengthWorkoutXp(sets = 5, reps = 5, weightKg = 60))
        // 0 kg = reines Volumen (Koerpergewicht).
        assertEquals(30L, Progression.strengthWorkoutXp(sets = 3, reps = 10, weightKg = 0))
        // Gewicht 100 kg verdoppelt das Volumen.
        assertEquals(60L, Progression.strengthWorkoutXp(sets = 3, reps = 10, weightKg = 100))
        // Ganzzahl-Division schneidet ab (1*1*(100+50)/100 = 1).
        assertEquals(1L, Progression.strengthWorkoutXp(sets = 1, reps = 1, weightKg = 50))
        assertEquals(0L, Progression.strengthWorkoutXp(sets = 0, reps = 0, weightKg = 0))
    }

    @Test
    fun `strengthWorkoutXp hebt negative Eingaben defensiv auf 0`() {
        assertEquals(0L, Progression.strengthWorkoutXp(sets = -5, reps = 10, weightKg = 20))
        assertEquals(0L, Progression.strengthWorkoutXp(sets = 5, reps = -3, weightKg = 20))
        // Negatives Gewicht wird auf 0 gehoben → reines Volumen, nie negative EP.
        assertEquals(25L, Progression.strengthWorkoutXp(sets = 5, reps = 5, weightKg = -80))
    }

    @Test
    fun `STR-Zuwachs je Krafttraining ist die bestaetigungsfaehige erste Version`() {
        assertEquals(1, Progression.STRENGTH_STAT_GAIN_PER_WORKOUT)
    }

    @Test
    fun `Rang-Schwellen sind die bestaetigte erste Version und steigen streng monoton`() {
        assertEquals(1, Progression.minLevelForRank(Rank.E))
        assertEquals(5, Progression.minLevelForRank(Rank.D))
        assertEquals(10, Progression.minLevelForRank(Rank.C))
        assertEquals(20, Progression.minLevelForRank(Rank.B))
        assertEquals(35, Progression.minLevelForRank(Rank.A))
        assertEquals(50, Progression.minLevelForRank(Rank.S))

        val thresholds = Rank.entries.map { Progression.minLevelForRank(it) }
        assertEquals(thresholds.sorted(), thresholds)
        assertEquals(thresholds.toSet().size, thresholds.size)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
