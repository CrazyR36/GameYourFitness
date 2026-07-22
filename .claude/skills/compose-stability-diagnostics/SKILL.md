---
name: compose-stability-diagnostics
description: "Nutze diesen Skill beim Schreiben oder Review von Jetpack-Compose-Parameter-Stabilität, Compiler-Reports, Skippability, instabilen UI-State-Klassen, Collection-Parametern oder Strong-Skipping-Verhalten ab Kotlin 2.0+."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Compose-Stabilitäts-Diagnose

## Grundprinzip

Compose-Parameter-Fixes beginnen bei Evidenz. Identifiziere zuerst den Compiler-Modus und das Parameter-Vergleichsverhalten, ändere dann das Modell oder die Aufrufstelle, die das Skipping tatsächlich vereitelt.

Ab Kotlin 2.0.20+ ist Strong Skipping standardmäßig aktiv. Instabile Parameter machen restartable Composables nicht mehr automatisch non-skippable, aber instabile Parameter vergleichen per Instanz-Identität (`===`), stabile per Gleichheit (`equals`). Churny instabile Instanzen können Skipping trotzdem vereiteln.

## Diagnose-Vorgehen

1. Bestätige das Symptom: Recomposition-Zähler, Compiler-Report-Ausgabe oder ein vermuteter churny Parameter.
2. Identifiziere den Compiler-Modus: Kotlin-/Compose-Compiler-Version und ob Strong Skipping aktiv ist.
3. Erzeuge oder lies die Compose-Compiler-Reports für die ausgelieferte Variante.
4. Entscheide je verdächtigem Parameter, ob das Problem Stabilitätssemantik, Instanz-Churn oder ein vom Aufrufer erzeugtes Lambda/abgeleiteter Wert ist.
5. Wende den leichtesten Fix an, der Typ/Aufrufstelle wahrheitsgemäß macht.
6. Miss dieselbe Interaktion erneut oder lies denselben Report erneut, bevor du das Problem als behoben erklärst.

## 1. Strong Skipping zuerst interpretieren

Auf Kotlin 2.0.20+ ist Strong Skipping standardmäßig aktiv. In diesem Modus:

- Restartable Composables sind skippable, selbst wenn Parameter instabil sind, außer es wird explizit abgewählt.
- Stabile Parameter vergleichen mit `equals`.
- Instabile Parameter vergleichen mit Instanz-Gleichheit (`===`).
- Lambdas in Composables werden automatisch anhand ihrer Captures geremembert.

Frage: „Vergleichen diese Parameter so, wie ich erwarte, und erzeugen Aufrufer in jedem Frame neue instabile Instanzen?"

Für ältere Compiler-Setups oder deaktiviertes Strong Skipping gilt die alte Regel weiter: Ein restartable Composable mit instabilen Parametern kann restartable, aber nicht skippable sein.

## 2. Compiler-Reports erzeugen

Ab Kotlin 2.0+ wird der Compose Compiler über das Kotlin-Gradle-Plugin konfiguriert:

```kotlin
plugins {
    alias(libs.plugins.android.application) // or android.library / jvm
    alias(libs.plugins.kotlin.android)      // or kotlin.multiplatform / kotlin.jvm
    alias(libs.plugins.compose.compiler)
}

if (providers.gradleProperty("composeReports").orNull == "true") {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}
```

Baue dann die Variante, deren Compiler-Konfiguration dich interessiert, z. B.:

```bash
./gradlew :app:assembleRelease -PcomposeReports=true
```

Nutze Release-/nicht-debuggable-Builds für Laufzeit-Profiling. Compiler-Reports sind Build-Zeit-Ausgaben; wichtig ist daher, dass Variante und Compiler-Flags zu dem passen, was du auslieferst.

Wichtige Dateien:

| Datei | Was sie dir sagt |
|---|---|
| `<module>-classes.txt` | Stabilität von Klassen und Properties |
| `<module>-composables.txt` | Restartable/skippable-Status und Parameter-Stabilität |
| `<module>-composables.csv` | Dieselben Daten in sortierbarer Form |
| `<module>-module.json` | Aggregierte Metriken |

## 3. Nur das bewiesene Parameter-Problem beheben

Wähle den leichtesten Fix, der die Immutability- oder Gleichheitssemantik des Typs wahr macht.

### Immutable Collections

Zeigen Reports Collection-Interfaces im UI-State, bevorzuge `kotlinx.collections.immutable` an den UI-State-Grenzen:

```kotlin
// Vorher: instabile Collection-Interfaces
data class UiState(val items: List<Item>, val tags: Set<String>)

// Nachher: immutable Collection-Contracts
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

data class UiState(val items: ImmutableList<Item>, val tags: ImmutableSet<String>)
```

Producer konvertieren einmal an der Grenze mit `.toImmutableList()` / `.toImmutableSet()`.

### `@Immutable` / `@Stable`

- Nutze `@Immutable`, wenn jede Property effektiv unveränderlich ist und Gleichheit den gesamten beobachtbaren State beschreibt.
- Nutze `@Stable` für Typen, deren veränderlicher State von Compose beobachtbar ist, typischerweise über `MutableState`.

Annotiere nicht, um einen Report stummzuschalten. Ein falsches Stabilitätsversprechen kann veraltete UI erzeugen.

### Drittanbieter-Immutable-Typen

Für Typen, die du nicht annotieren, aber wahrheitsgemäß als immutable behandeln kannst, nutze `stabilityConfigurationFiles`:

```kotlin
composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_stability.conf"),
    )
}
```

```text
java.math.BigDecimal
java.math.BigInteger
java.time.*
kotlinx.datetime.*
```

Liste nur Typen, deren Immutability du zu versprechen bereit bist. Liste keine veränderlichen Typen wie `java.util.Date`.

## 4. Lazy-Item-Eingaben stabilisieren

Kommt die Recomposition von Lazy-Items aus Call-Site-Churn, stabilisiere die an jedes Item übergebenen Werte, statt Modelle blind zu annotieren.

Ziehe pro-Item-Eingaben, die für die Lebensdauer des Items stabil sind, hoch und remembere sie:

```kotlin
// ❌ SCHLECHT — neue Lambda-Instanzen, wenn das Elternteil neu komponiert
items(list, key = { it.id }) { item ->
    RowCard(
        onClick = { onItemClick(item.id) },
        isHighlighted = { item.id == selectedId },
    )
}

// ✅ GUT — stabile Captures für diese Item-Instanz
items(list, key = { it.id }) { item ->
    val onClick = remember(item.id) { { onItemClick(item.id) } }
    val isHighlighted = remember(item.id, selectedId) { item.id == selectedId }
    RowCard(onClick = onClick, isHighlighted = isHighlighted)
}
```

Ziehe auch Zeilen-Positions-Metadaten (`isFirst`, `isLast`, Eckradien) mit `remember(index) { … }` hoch, wenn der Wert nur vom Index abhängt — erwarte aber nicht, dass das allein Back-Writing- oder phasenübergreifende Mess-Bugs behebt.

Verifiziere Fokuswechsel und Einfügungen nach dem Hochziehen mit Recomposition-Zähler-Assertions.

## Kurzreferenz

| Symptom | Diagnose | Fix |
|---|---|---|
| Kotlin 2.0.20+, aber alte Docs sagen „instabil = non-skippable" | Strong Skipping hat den Default geändert | Stattdessen Vergleichssemantik und Instanz-Churn prüfen |
| `unstable val items: List<Item>` | Interface-Collection | `ImmutableList<Item>` oder anderen echten Immutable-Wrapper nutzen |
| `unstable val price: BigDecimal` | Externer Immutable-Typ | Zur Stability-Config hinzufügen |
| `@Immutable` auf einem Typ mit veränderlichen Internals | Falsches Versprechen | Modell fixen oder Annotation entfernen |
| Composable skippt schlecht trotz Strong Skipping | Neue instabile Instanz pro Recomposition | Remembern, hochziehen oder Typ stabil/equality-basiert machen |
| Lazy-Items komponieren bei Eltern-Recomposition neu trotz unveränderter Daten | Neue Lambda- oder abgeleitete-Wert-Instanz pro Eltern-Recomposition (§4) | Pro Item mit `remember(item.id) { … }` hochziehen |
| Reports werden nicht erzeugt | Compose-Compiler-Plugin fehlt oder Flag nicht gesetzt | `org.jetbrains.kotlin.plugin.compose` anwenden und Destinations aktivieren |

## Wann NICHT anwenden

- Das Problem ist Back-Writing über Phasen oder phasenübergreifende Mess-Reads. Nutze [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md).
- Das Problem ist ein schnell wechselnder `State`-Read in der Composition, etwa Scroll oder Animation. Nutze [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md).
- Der Recomposition-Zähler passt zu echten Datenänderungen.
- Der Bug sind falsche Daten oder veralteter State, nicht überschüssige Arbeit.
- Der Code ist nur für Tests und Lesbarkeit ist wichtiger als Report-Sauberkeit.

## Verwandt

- [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md) — frame-rate-State sollte oft in Layout/Draw statt in der Composition gelesen werden.
- [`compose-recomposition-performance`](../compose-recomposition-performance/SKILL.md) — Einstiegspunkt, wenn unklar ist, welche Recomposition-Achse betroffen ist.
