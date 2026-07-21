package com.gameyourfitness.app.domain.auth

/**
 * Ergebnis eines Anmeldeversuchs — die UI mappt jeden Fall auf eine
 * verstaendliche Meldung.
 */
sealed interface SignInResult {
    data object Success : SignInResult

    /** Nutzer hat den Google-Dialog abgebrochen. */
    data object Cancelled : SignInResult

    /** Server nicht erreichbar (kein Netz o. ae.). */
    data object NetworkError : SignInResult

    /** Sonstiger Fehler (Google-Credential oder GoTrue lehnt ab). */
    data object Failed : SignInResult
}
