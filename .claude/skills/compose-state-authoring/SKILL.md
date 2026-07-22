---
name: compose-state-authoring
description: "Nutze diesen Skill beim Schreiben oder Review von Jetpack-Compose-Code mit einem nackten lokalen var in einem @Composable, remember { mutableStateOf(...) }, mutableStateListOf/mutableStateMapOf oder @ReadOnlyComposable."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Compose-State-Authoring

Nicht jedes `remember { … }` gehört hierher. Dieser Skill behandelt **lokalen UI-State** (`remember { mutableStateOf(…) }`, `mutableStateListOf` / `mutableStateMapOf`) und **`@ReadOnlyComposable`**. Andere remember-APIs leben in fokussierten Skills:

- **`rememberCoroutineScope` / `rememberUpdatedState`** → [`compose-side-effects`](../compose-side-effects/SKILL.md)
- **`rememberLazyListState` / `rememberScrollState`** für frame-rate-Reads → [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md)
- **Fokus-Navigation, Fokus-State, `FocusRequester`-Ownership, Verhalten** → [`compose-focus-navigation`](../compose-focus-navigation/SKILL.md)

## Grundprinzip

Ein `@Composable` ist eine Funktion, die die Runtime erneut ausführt, wann immer sich ihre Eingaben ändern. Lokalen State korrekt zu schreiben, läuft auf zwei Fragen hinaus:

1. **Veränderlicher lokaler State** — überlebt mein `var` die Recomposition *und* löst sie aus? Wenn nicht, wird er bei jeder Recomposition still zurückgesetzt und Schreibzugriffe sind unsichtbar.
2. **Welche Art Composable ist das?** — *mutiere* ich die Composition (platziere Layout-Knoten, allokiere Slots, `remember`) oder *lese* ich sie nur? Wenn nur gelesen, lässt `@ReadOnlyComposable` die Runtime Arbeit sparen.

Wird eines falsch, sind die Symptome subtil: State, der verschwindet, oder Optimierungen, die nicht greifen.

## Wann diesen Skill nutzen

Du schreibst oder reviewst Compose-Code und siehst eines davon:

- `var x = …` in einer `@Composable fun` oder einem beliebigen Composable-Lambda (`Column { var x = … }`)
- Eine `@Composable fun` (oder ein `@Composable get()`-Property-Accessor), deren Body nie etwas layoutet
- `@ReadOnlyComposable` auf einer Funktion, die `Text`, `Box`, `Column`, `remember`, … aufruft
- Ein Composable, dessen sichtbarer State bei Rotation, Theme-Wechsel oder Recomposition rätselhaft zurückgesetzt wird

## 1. `var` in einem Composable muss State-backed sein

Recomposition führt das Composable von oben erneut aus. Ein lokales `var` wird bei jedem Durchlauf *neu initialisiert* — der Wert der letzten Recomposition ist weg, und das Schreiben darauf sagt der Runtime nicht, dass sie neu komponieren soll.

```kotlin
// ❌ SCHLECHT — counter wird bei jeder Recomposition zurückgesetzt; Klicks aktualisieren die UI nie
@Composable
fun Counter() {
    var count = 0
    Button(onClick = { count++ }) { Text("$count") }
}

// ❌ EBENFALLS SCHLECHT — dieselbe Regel gilt in Composable-Content-Lambdas
@Composable
fun Wrapper() {
    Row {
        var count = 0         // Rows Content-Lambda ist ebenfalls @Composable
        // …
    }
}
```

```kotlin
// ✅ GUT — `remember` überlebt die Recomposition, `mutableStateOf` löst sie aus
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }
    Button(onClick = { count++ }) { Text("$count") }
}
```

Zwei Teile, und beide zählen:

- `remember { … }` — *überlebt die Recomposition*. Ohne es wird der Wert jedes Mal neu erzeugt.
- `mutableStateOf(…)` — *löst die Recomposition aus*. Ohne es sind Mutationen für die Runtime unsichtbar.

Für Collections bevorzuge `mutableStateListOf` / `mutableStateMapOf` (ebenfalls `remember`-t). Sie emittieren bei jedem Read Snapshot-Reads und bei jeder Mutation Snapshot-Writes. Ein `remember { mutableStateOf(mutableListOf<X>()) }` gefolgt von `list.add(x)` wird *nicht* neu komponieren, weil `MutableList.add` nicht durch den State-Setter geht — du müsstest den Wert ersetzen (`state = state + x`).

### Back-Writing von Snapshot-State während der Composition

**Back-Writing** heißt, beobachtbaren State in einer Phase zu schreiben, die die Invalidierung einer früheren (oder der aktuellen) Phase auslöst. `mutableState*` aus dem Composable-Body zu mutieren, schreibt in denselben Composition-Durchlauf zurück und plant einen weiteren. Baue abgeleitete Daten nicht so neu auf:

```kotlin
// ❌ SCHLECHT — clear + putAll bei jeder Composition
val merged = remember { mutableStateMapOf<Key, ViewState>() }
merged.clear()
merged.putAll(parent)
merged.putAll(overlay)

// ✅ GUT — unveränderlicher Snapshot, aus den Eingaben geremembert
val merged = remember(parent, overlay) {
    if (overlay.isEmpty()) parent else parent + overlay
}
```

Ist das Ergebnis für die aktuellen Eingaben read-only, genügt `remember(keys) { … }`. Siehe [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md) für phasenübergreifende Messung und Measure-Phase-Fixes.

### Wann diese Regel NICHT gilt

- **Im Producer-Block von `remember { … }`.** Der läuft einmal pro Key-Änderung, nicht bei jeder Recomposition. Ein lokales `var` dort ist in Ordnung: `val builder = remember { mutableListOf<X>().apply { var n = 0; … } }`.
- **In nicht-`@Composable`-Lambdas, die *aus* einem Composable *heraus*gereicht werden.** `onClick = { var a = 0; … }` ist ein einfaches `() -> Unit`. Lokale vars dort sind normales Kotlin.
- **In einfachen (nicht-`@Composable`) Hilfsfunktionen.** Nur Composable-Scopes sind betroffen.

## 2. Der `@ReadOnlyComposable`-Contract

`@ReadOnlyComposable` erklärt, dass ein Composable Composition-State *nur liest* — kein `Text`, kein `Box`, kein `remember`, keine Layout-Knoten, keine positionalen Slots. Die Runtime kann dann das Allokieren einer Gruppe für den Aufruf überspringen, was für schnelle accessor-artige Composables zählt (`MaterialTheme.colorScheme`, `LocalDensity.current`, Design-System-Token-Accessoren).

Der Contract ist **bidirektional**:

- **Füge `@ReadOnlyComposable` hinzu**, wenn jeder Composable-Aufruf, den dein Body macht, selbst `@ReadOnlyComposable` ist (oder es gar keine Composable-Aufrufe gibt — z. B. eine Funktion, die nur `LocalFoo.current` liest und einen Wert zurückgibt).
- **Füge es nicht hinzu**, wenn du irgendein nicht-read-only Composable aufrufst. Die Optimierung nimmt an, dass du nicht an der Composition teilnimmst; das zu verletzen erzeugt falsches Recomposition-Verhalten für Aufrufer.

```kotlin
// ✅ GUT — liest nur Composition-Locals, kein Layout, kein remember
@Composable
@ReadOnlyComposable
fun appSpacing(): Dp = LocalDimensions.current.spacing

// ✅ GUT — Composable-Property-Getter; dieselbe Regel
val accent: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.tertiary
```

```kotlin
// ❌ SCHLECHT — als read-only annotiert, layoutet aber eine Box; Contract verletzt
@Composable
@ReadOnlyComposable
fun Header(): Int {
    Box {}                  // ← nicht-read-only Composable-Aufruf
    return 42
}

// ❌ SCHLECHT — ruft ein normales Composable aus einem read-only auf
@Composable
@ReadOnlyComposable
fun computed(): Int = nonReadOnlyHelper()
```

### Heuristik für „soll ich es hinzufügen"

Enthält der Body eines davon, füge `@ReadOnlyComposable` **nicht** hinzu:

- Einen Layout-Aufruf: `Box`, `Column`, `Row`, `LazyColumn`, `Text`, alles aus `androidx.compose.foundation.layout` oder `androidx.compose.material*`.
- Einen Side-Effect-Aufruf: `LaunchedEffect`, `DisposableEffect`, `SideEffect`, `produceState`.
- `remember { … }` — positionale Memoization ist Composition-State.
- Eine `@Composable`-Lambda-Invocation (`content()`).
- Eine Invocation einer nicht-`@ReadOnlyComposable`-Composable-Funktion.

Liest der Body nur `Local*.current`, ruft andere `@ReadOnlyComposable`-Funktionen auf oder macht reine Berechnung, **füge es hinzu**.

### Wann diese Regel NICHT gilt

- **`override fun`-Deklarationen.** Die Annotation ist Teil des Contracts; ist die Basis nicht `@ReadOnlyComposable`, kannst du einen Override nicht dazu machen. Refactore die Basis oder akzeptiere, dass der Override die Gruppen-Erzeugungskosten zahlt.
- **Abstrakte Deklarationen.** Kein Body zum Prüfen.

## Verwandt: Side Effects leben in ihrem eigenen Skill

Braucht ein Composable `LaunchedEffect`, `DisposableEffect`, `SideEffect`, `rememberCoroutineScope`, `rememberUpdatedState`, `snapshotFlow`, Snackbar-/Navigations-Behandlung, Analytics oder Flow-Collection, nutze [`compose-side-effects`](../compose-side-effects/SKILL.md).

Fokus teilt sich nach Frage auf: **Navigation, Fokus-State, `FocusRequester`-Ownership, Verhalten** → [`compose-focus-navigation`](../compose-focus-navigation/SKILL.md); **wann** man das imperative `requestFocus` aufruft (Effect-Timing, Lifecycle, Keys, API-Wahl) → [`compose-side-effects`](../compose-side-effects/SKILL.md).

Dieser Skill dreht sich um das korrekte Schreiben von Compose-State. `rememberUpdatedState` ist Effect-Capture-State, kein allgemeiner Ersatz für `remember { mutableStateOf(...) }`. Side Effects haben eigene Lifecycle- und Keying-Regeln, und sie in einem fokussierten Skill zu halten vermeidet zwei Quellen der Wahrheit.

## Kurzreferenz

| Symptom | Diagnose | Fix |
|---|---|---|
| `var x = …` im Body einer `@Composable fun` | Nicht recomposition-sicher (§1) | `var x by remember { mutableStateOf(…) }` |
| `var x = …` im Content-Lambda von `Column { … }` / `Row { … }` | Dasselbe — Content-Lambdas sind `@Composable` (§1) | Gleicher Fix |
| `remember { mutableStateOf(list) }` dann `.add(x)` komponiert nicht neu | Mutation umgeht den State-Setter | `mutableStateListOf` nutzen oder den Wert ersetzen: `state = state + x` |
| `stateMap.clear(); stateMap.putAll(...)` im Composable-Body | Back-Writing Composition → Composition | `remember(keys) { derivedSnapshot }` |
| `@Composable fun` ohne `Text`/`Box`/`remember`/Effect-Aufrufe | Könnte `@ReadOnlyComposable` sein (§2) | `@ReadOnlyComposable` über `@Composable` setzen |
| `@ReadOnlyComposable`-Funktion, die `Box {}` / `Column {}` / ein normales Composable aufruft | Contract-Verletzung (§2) | `@ReadOnlyComposable` entfernen |

## Wann NICHT anwenden

- **Tests** mit `composeTestRule.setContent { … }` folgen denselben Regeln — sie sind Produktions-Composables.
- **`produceState`** hat einen eigenen Producer-Block, der in einer Coroutine läuft; du brauchst *darin* kein `LaunchedEffect`.
- **`derivedStateOf`** hat eigene Belange rund um Stabilität und Gleichheit — hier außerhalb des Rahmens; es geht ums *Verhindern* von Recomposition, nicht ums Schreiben von State.
- **`override`s** von read-only-Composable-Deklarationen: Die Annotation ist durch die Basis festgelegt; du kannst sie lokal nicht hinzufügen oder entfernen.

## Warnzeichen im Review

| Gedanke | Realität |
|---|---|
| „Es ist ein kleines Composable, das nackte `var` ist okay" | Recomposition kann jederzeit feuern. Der Reset ist per Design nicht-deterministisch — und später ein einzelner Bug-Report. |
| „Ich füge `@ReadOnlyComposable` hinzu, weil die Funktion einfach aussieht" | „Einfach" ist nicht das Kriterium. „Macht nur read-only-Aufrufe" ist es. |
| „Ich greife immer zu `LaunchedEffect`, weil ich das kenne" | Nutze `compose-side-effects`; die Effect-API-Wahl hängt von Lifecycle und Keys ab. |
| „Ich mache einfach `.add()` auf die geremberte Liste" | Ein `mutableStateOf(List)` beobachtet keine interne Mutation — nutze `mutableStateListOf` oder ersetze den Wert. |
| „Der Override braucht `@ReadOnlyComposable`, damit er passt" | Ist die Basis nicht `@ReadOnlyComposable`, kannst du es einem Override nicht hinzufügen. Refactore stattdessen die Basis. |
