package com.gameyourfitness.app.domain.progression

/**
 * Fortschritt innerhalb des aktuellen Levels — alles, was der EP-Balken braucht.
 *
 * @param level aktuelles Level (>= [Progression.START_LEVEL]).
 * @param xpIntoLevel bereits im aktuellen Level gesammelte EP.
 * @param xpForLevel EP-Spanne des aktuellen Levels (Aufstieg L→L+1).
 * @param fractionToNextLevel Füllgrad des Balkens, 0f..1f.
 */
data class LevelProgress(
    val level: Int,
    val xpIntoLevel: Long,
    val xpForLevel: Long,
    val fractionToNextLevel: Float
)

/**
 * Zentrale Spiellogik für Level, EP-Kurve und Rang-Schwellen (CLAUDE.md Abschnitt 6:
 * eine Datei, keine Magic Numbers, reine Funktionen ohne Android-Framework).
 *
 * **Erste Version, zur Bestätigung vorgelegt (Issue #3).** Alle Balancing-Werte
 * sind Konstanten an dieser einen Stelle und dadurch trivial anpassbar.
 *
 * EP-Kurve: Der Aufstieg von Level L auf L+1 kostet `XP_STEP_PER_LEVEL * L` EP.
 * Kumulativ ergibt das eine quadratische Kurve: um Level L zu erreichen, sind
 * `XP_STEP_PER_LEVEL * (L-1) * L / 2` EP nötig (L1=0, L2=100, L3=300, L5=1000, …).
 */
object Progression {
    /** Basis-EP der Levelstufe; Stufe L→L+1 kostet [XP_STEP_PER_LEVEL] * L EP. */
    const val XP_STEP_PER_LEVEL: Long = 100L

    /**
     * Nenner der Gewichts-Skalierung in [strengthWorkoutXp]: 0 kg ⇒ Faktor 1,0 (reines
     * Volumen), je [WEIGHT_XP_DIVISOR] kg wächst der Faktor um 1,0.
     */
    const val WEIGHT_XP_DIVISOR: Long = 100L

    /** STR-Zuwachs je erfasstem Krafttraining (erste Version, Issue #4 — flach). */
    const val STRENGTH_STAT_GAIN_PER_WORKOUT: Int = 1

    /** Startlevel eines neuen Charakters (0 EP). */
    const val START_LEVEL: Int = 1

    /**
     * Interne Obergrenze der Levelkurve — reine Sicherheits-/Overflow-Schranke, KEIN
     * Spielziel: Level [MAX_LEVEL] entspricht rund 5 Mrd. EP und ist im Spiel nie erreichbar.
     */
    const val MAX_LEVEL: Int = 9999

    // Rang-Mindestlevel (erste Version, Issue #3) — Konstanten statt Magic Numbers.
    private const val MIN_LEVEL_D = 5
    private const val MIN_LEVEL_C = 10
    private const val MIN_LEVEL_B = 20
    private const val MIN_LEVEL_A = 35
    private const val MIN_LEVEL_S = 50

    /** Kumulative EP, um [level] zu erreichen. [START_LEVEL] erfordert 0 EP. */
    fun xpToReachLevel(level: Int): Long {
        require(level >= START_LEVEL) { "Level $level unter dem Startlevel $START_LEVEL" }
        val l = level.toLong()
        return XP_STEP_PER_LEVEL * (l - 1L) * l / 2L
    }

    /**
     * Höchstes Level, dessen EP-Schwelle [totalXp] bereits erreicht hat. Rein ganzzahlige
     * Binärsuche (exponentielle Schranke), gegen Overflow durch [MAX_LEVEL] gedeckelt.
     */
    fun levelForXp(totalXp: Long): Int {
        if (totalXp <= 0L) return START_LEVEL
        if (totalXp >= xpToReachLevel(MAX_LEVEL)) return MAX_LEVEL
        var hi = START_LEVEL + 1
        while (xpToReachLevel(hi) <= totalXp) hi *= 2
        var lo = START_LEVEL
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (xpToReachLevel(mid) <= totalXp) lo = mid else hi = mid
        }
        return lo
    }

    /** Fortschritt im aktuellen Level für den EP-Balken. */
    fun levelProgress(totalXp: Long): LevelProgress {
        val safeXp = totalXp.coerceAtLeast(0L)
        val level = levelForXp(safeXp)
        val currentBase = xpToReachLevel(level)
        val nextBase = xpToReachLevel(level + 1)
        val span = nextBase - currentBase
        val into = safeXp - currentBase
        val fraction = if (span <= 0L) 0f else (into.toFloat() / span.toFloat()).coerceIn(0f, 1f)
        return LevelProgress(level = level, xpIntoLevel = into, xpForLevel = span, fractionToNextLevel = fraction)
    }

    /**
     * EP für ein Krafttraining (erste Version, Issue #4 — bestätigungspflichtig, #16).
     * Reine Ganzzahl-Arithmetik: `sätze · wdh · (100 + gewicht) / 100`. 0 kg ⇒ reines
     * Volumen (Körpergewicht). Diese Kotlin-Funktion und die SQL-Funktion
     * `log_strength_workout` sind Spiegelbilder; der E2E-Test gleicht sie Ende-zu-Ende ab.
     *
     * Erwartet plausibilisierte Eingaben (siehe `StrengthWorkoutValidator`); negative
     * Werte werden defensiv auf 0 gehoben, damit nie negative EP entstehen.
     */
    fun strengthWorkoutXp(sets: Int, reps: Int, weightKg: Int): Long {
        val safeSets = sets.coerceAtLeast(0).toLong()
        val safeReps = reps.coerceAtLeast(0).toLong()
        val safeWeight = weightKg.coerceAtLeast(0).toLong()
        return safeSets * safeReps * (WEIGHT_XP_DIVISOR + safeWeight) / WEIGHT_XP_DIVISOR
    }

    /**
     * Mindestlevel, ab dem [rank] überhaupt erreichbar ist. Gatet ab #10 den
     * Aufstiegstest; in #3 nur als bestätigungsfähige Konstante hinterlegt.
     */
    fun minLevelForRank(rank: Rank): Int = when (rank) {
        Rank.E -> START_LEVEL
        Rank.D -> MIN_LEVEL_D
        Rank.C -> MIN_LEVEL_C
        Rank.B -> MIN_LEVEL_B
        Rank.A -> MIN_LEVEL_A
        Rank.S -> MIN_LEVEL_S
    }
}
