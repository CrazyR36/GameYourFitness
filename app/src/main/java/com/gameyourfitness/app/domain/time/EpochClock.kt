package com.gameyourfitness.app.domain.time

/**
 * Injizierbare Uhr (Epoch-Sekunden), damit Ablauf-Logik ohne echte Zeit testbar ist.
 */
fun interface EpochClock {
    fun now(): Long
}
