---
name: design-system
description: >-
  Verbindliches UI-/Styling-Vokabular für GameYourFitness (dunkles RPG-„System-Fenster"-
  Design, Blau/Violett-Glow). Nutze diesen Skill immer, wenn ein Compose-Screen, ein
  Dialog/Popup, eine Karte, ein EP-/Fortschrittsbalken, ein Stat-Element, ein Level-Up-
  Popup oder irgendein sichtbares UI-Element gebaut oder geändert wird — auch wenn nur
  „ein Screen", „ein Popup", „die Ansicht" oder „das Design" erwähnt wird, ohne das Wort
  „Styling". Liefert die echten Theme-Tokens, das System-Fenster-Grundgerüst, die TestTag-
  und Barrierefreiheit-Regeln und die Pflicht-Zustände (Laden/Leer/Fehler) samt
  Screenshot-Test. Für Compose-Mechanik (State-Hoisting, Recomposition, Slot-API) an die
  compose-*-Skills verweisen; hier geht es um das projektspezifische Aussehen.
---

# design-system — Aussehen von GameYourFitness

Dieser Skill kodiert CLAUDE.md Abschnitt 7 mit den **echten** Tokens und Mustern des Repos.
Ziel: Jeder UI-Slice greift dasselbe visuelle Vokabular ab, statt es neu zu erfinden.

**Abgrenzung:** Dieser Skill regelt *Aussehen, Tokens, System-Fenster, Zustände, Tests*.
Für Compose-**Mechanik** (State-Hoisting, State-Holder/UI-Split, Recomposition-Performance,
Slot-API, Side-Effects, UI-Test-Strategie) → die `compose-*`-Skills von chrisbanes nutzen.

## Grundregel: keine hartkodierten Werte

Alle Farben, Abstände und Textstile kommen aus dem zentralen Theme — **nie** Literale in
Composables (Abschnitt 7). Fehlt ein Wert, wird er in der Token-Datei ergänzt, nicht im
Composable erfunden.

| Kategorie | Datei | Zugriff im Composable |
|---|---|---|
| Farben | `ui/theme/Color.kt` (+ `Theme.kt`) | `MaterialTheme.colorScheme.*` |
| Abstände/Größen | `ui/theme/Dimens.kt` | `Dimens.*` |
| Typografie | `ui/theme/Type.kt` | `MaterialTheme.typography.*` |
| Theme-Einstieg | `ui/theme/Theme.kt` | `GameYourFitnessTheme(darkTheme = true) { … }` |

### Token-Kurzreferenz (dunkel = Standard)

- **Farben:** `background`=NightBackground `#0B0F1A`, `surface`=NightSurface `#121A2B`,
  `primary`=GlowBlue `#4F8CFF`, `secondary`=GlowViolet `#8B5CF6`, `tertiary`=GlowCyan
  `#38E1FF`, `error`=AlertRed `#FF5470`, `onBackground` `#E3EAFB`, `onSurface` `#CBD5F0`.
  Ein Light-Pendant existiert (`DayBackground` …) — dunkel bleibt Produktstandard.
- **Abstände:** `screenPadding`/`systemWindowPadding` 24dp, `systemWindowBorder` 1dp,
  `contentSpacing` 16dp, `inlineProgressSize` 20dp, `progressStroke` 2dp.
- **Typo:** `titleLarge` (Monospace Bold 22, weite Laufweite) für Titel, `bodyLarge`
  (SansSerif 16) für Fließtext, `labelLarge` (Monospace Medium 14) für Labels,
  `displayMedium` (Monospace Bold 32) für große Zahlen (Level).

## Das „System-Fenster" — kanonisches Grundgerüst

Jeder **Vollbild**-Screen ist ein Glow-Rahmen-Fenster, das die **volle Breite und volle Höhe**
nutzt (echtes HUD-Panel, nicht ein kleines schwebendes Kästchen) — mit **immer etwas Abstand
zum Rahmen**: `screenPadding` außen (Luft für den Glow bis zum Bildschirmrand) und
`systemWindowPadding` innen (Inhalt berührt nie den Rahmen). Inhalt wird als Gruppe **vertikal
zentriert** (`Arrangement.spacedBy(..., Alignment.CenterVertically)`), damit auch bei wenig
Inhalt oben und unten gleichmäßig Platz zum Rahmen bleibt. Real umgesetzt in
`ui/auth/LoginScreen.kt` und `ui/character/CharacterScreen.kt` — daran halten:

```kotlin
@Composable
fun ExampleScreen(state: UiState, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .atmosphericBackground()          // radialer Backdrop-Glow (ui/theme/SystemWindow.kt)
            .testTag("example_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()                 // volle Breite UND Höhe
                .padding(Dimens.screenPadding) // Abstand zum Bildschirmrand (Glow-Raum)
                .systemWindow()                // Glow-Schatten + Surface-Füllung + Gradient-Rand
                .padding(Dimens.systemWindowPadding), // Abstand Inhalt ↔ Rahmen (nie berühren)
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.contentSpacing, Alignment.CenterVertically)
        ) {
            Text(
                text = stringResource(R.string.example_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("example_title")
            )
            SystemDivider()                    // Gradient-Naht (ui/theme/SystemWindow.kt)
            // Inhalt je Zustand (siehe unten)
        }
    }
}
```

`Modifier.systemWindow()`, `Modifier.atmosphericBackground()` und `SystemDivider` liegen in
`ui/theme/SystemWindow.kt`; Ecken/Bewegung/Deckkraft als Token in `Shapes.kt`/`Motion.kt`/`Alpha`.

Popups (Level-Up, Rang-Aufstieg, Formulare im Dialog) sind bewusst **inhaltsgroß** statt
vollflächig: `fillMaxWidth().systemWindow().padding(systemWindowPadding)`, mit denselben
Abständen zum Rahmen und Ein-/Ausblende-Animation — die Animation muss über deaktivierbare
Animationen/`AnimationTestRule` testbar bleiben.

> **Screenshot-Höhe:** Ein Vollbild-Screen kann höher als das 320×470dp-Default-Testgerät sein
> und würde am unteren Rand abgeschnitten. Dann im Screenshot-Test die Leinwand erhöhen
> (`@Config(sdk = [35], qualifiers = "+h900dp")`), damit das Golden das ganze Fenster inkl.
> aller Buttons zeigt. Auf echten Geräten passt der Screen (E2E belegt es).

## Pflichtregeln (werden im Review/Test geprüft)

- **Stateless:** Composables bekommen State + Callbacks als Parameter, enthalten keine
  Businesslogik (Abschnitt 5) — dadurch einzeln testbar und screenshot-fähig.
- **TestTag:** jedes prüfbare Element bekommt `Modifier.testTag("…")` in **snake_case**
  (`login_title`, `home_logout_button`). TestTags sind gepflegte öffentliche API (Abschnitt 2).
- **Barrierefreiheit:** jedes interaktive Element hat `contentDescription` (via
  `semantics { contentDescription = … }`); Kontraste geprüft.
- **Texte** kommen aus `res/values/strings.xml` (`stringResource(...)`), nie als Literal.
- **Drei Zustände Pflicht:** jeder Screen definiert **Laden**, **Leer** und **Fehler** —
  alle drei werden getestet (Abschnitt 7). Fehler nutzt `colorScheme.error`; Ladeanzeige
  ist der `CircularProgressIndicator` mit `Dimens.progressStroke`/`inlineProgressSize`;
  Buttons sind während des Sendens `enabled = false`.
- **Screenshot-Test** für jeden neuen/geänderten Screen in **Light + Dark**
  (`app/src/test/screenshots/`).

## Gotchas (so geht es hier meist schief)

- **Hartkodierte Farbe/dp** → detekt/Review meckert. Immer erst Token in `Color.kt`/
  `Dimens.kt` ergänzen. Das ist die häufigste Abweichung.
- **Screenshot-Goldens vergessen:** Neue Screens brauchen Goldens. Die CI nimmt sie beim
  Erstlauf auf und committet sie (`.github/workflows/ci.yml`); bei gewollter UI-Änderung
  lokal `./gradlew recordRoborazziDebug`. Fehlende Goldens auf `main` sind ein CI-Fehler.
- **Screenshot-Tests sind JUnit 4 + Robolectric** (Vintage-Engine), **nicht** JUnit 5 —
  `@RunWith(RobolectricTestRunner)` + `org.junit.Test` (siehe `docs/DECISIONS.md`,
  2026-07-19). Reine Domänentests bleiben JUnit 5.
- **Animation ohne Testbarkeit:** Läuft eine Animation ungebremst, wird der Screenshot-Test
  flaky. Animationen deaktivierbar halten.
- **Dark ist Standard:** `GameYourFitnessTheme` koppelt `darkTheme` bewusst **nicht** an die
  Systemeinstellung. Für den Light-Screenshot `darkTheme = false` explizit setzen.
