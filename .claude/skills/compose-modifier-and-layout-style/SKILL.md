---
name: compose-modifier-and-layout-style
description: "Nutze diesen Skill beim Schreiben oder Review von Jetpack-Compose-Layout-APIs, Modifier-Parametern, Modifier-Chain-Konstruktion, hartkodierten Root-Layout-Entscheidungen oder Layout-Wrappern um eine einzelne Bedingung."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Compose: Modifier- und Layout-Stil

## Grundprinzip

Ein Composable, das Layout emittiert, ist ein Leaf, das der *Parent* platziert — der Parent entscheidet Position, Größe, Alignment, Padding. Die Aufgabe des Composables ist Struktur (was drin ist), nicht Platzierung (wo es hingeht). Drei Regeln folgen:

- **Deklariere einen `modifier`-Parameter und wende ihn auf die Root an**, damit der Parent seine Aufgabe wirklich erledigen kann. `.fillMaxWidth()` auf der Root eines Composables hartzukodieren nimmt diese Entscheidung jedem künftigen Aufrufer weg.
- **Baue Modifier-Chains als einen fluenten Ausdruck**, nicht als schrittweise Neuzuweisungen. Beides kompiliert zum selben, aber die Chain *liest* sich in einem Durchgang als Absicht.
- **Bedingtes Rendering gehört dorthin, wo die Bedingung gilt.** Ein Layout-Aufruf, dessen einziger Content ein `if` ist, existiert nur, um die Bedingung zu halten — schiebe das `if` stattdessen nach außen.

Diese reisen zusammen, weil dasselbe Composable meist alle drei auslöst: Du deklarierst seine Parameter (Regel 1), der Aufrufer baut eine Chain, um es zu positionieren (Regel 2), und der Body hat eine Bedingung, die du zu wrappen versucht sein könntest (Regel 3).

## Wann diesen Skill nutzen

- Du schreibst eine `@Composable fun`, die ein Layout aufruft (`Box`, `Column`, `Row`, `LazyColumn`, `Text`, `Image`, `Surface`, `Card`, `Layout { … }`, alles aus `compose.foundation.layout` oder `compose.material*`), und ihre Signatur hat keinen `modifier`-Parameter, oder einen, der nicht auf die Root angewendet wird, oder ein hartkodiertes `.fillMaxWidth()`/`.padding(...)` auf der Root.
- Du siehst `var m = Modifier` gefolgt von `m = m.padding(…)`, `m = m.background(…)` usw.
- Ein `modifier = …`-Argument hat drei oder mehr verkettete Aufrufe in einer Zeile.
- Der Body eines Composables ist `Layout { if (cond) Content() }` — eine Bedingung, sonst nichts.

## 1. Deklariere einen `modifier`-Parameter

Für Composables, die Layout emittieren, bevorzuge einen `modifier`-Parameter nach den erforderlichen Parametern und vor Content-/Lambda-Parametern, mit Default `Modifier`. Der Name ist genau `modifier` — nicht `mod`, nicht `m`, nicht `wrapperModifier`.

```kotlin
// ❌ SCHLECHT — kein modifier-Param; Aufrufer kann das nicht positionieren, dimensionieren oder constrainen
@Composable
fun HomeScreenHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}
```

```kotlin
// ✅ GUT — Parent entscheidet Breite und Padding; das Composable beschreibt nur Struktur
@Composable
fun HomeScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}
```

Der Aufrufer schreibt jetzt `HomeScreenHeader(title, subtitle, Modifier.fillMaxWidth().padding(horizontal = 16.dp))` einmal, am Home-Screen — der einzigen Stelle, die weiß, dass das Layout diese tatsächlich will.

## 2. Wende den Modifier des Aufrufers auf die Root an, und zwar zuerst

Wenn das Root-Layout bereits andere Argumente nimmt (Alignment, Arrangement, Padding, *das dem Composable intrinsisch ist*), geht der vom Aufrufer gelieferte Modifier trotzdem auf den `modifier`-Parameter des Root-Layouts — und die lokale Chain des Composables wird danach angehängt.

```kotlin
// ❌ SCHLECHT — modifier akzeptiert, aber nie angewendet
@Composable
fun Avatar(url: String, modifier: Modifier = Modifier) {
    Image(painter = rememberAsyncImagePainter(url), contentDescription = null)
}

// ❌ SCHLECHT — auf ein Kind angewendet, nicht auf die Root; Größen-/Positionsänderungen des Aufrufers greifen nicht
@Composable
fun Avatar(url: String, modifier: Modifier = Modifier) {
    Box {
        Image(
            painter = rememberAsyncImagePainter(url),
            contentDescription = null,
            modifier = modifier,
        )
    }
}

// ❌ SCHLECHT — der Modifier des Aufrufers landet zuletzt, also gewinnt die eigene Größe des Composables
@Composable
fun Avatar(url: String, modifier: Modifier = Modifier) {
    Image(
        painter = rememberAsyncImagePainter(url),
        contentDescription = null,
        modifier = Modifier
            .clip(CircleShape)
            .size(48.dp)
            .then(modifier),
    )
}
```

```kotlin
// ✅ GUT — Modifier des Aufrufers zuerst, dann die intrinsische Chain des Composables
@Composable
fun Avatar(url: String, modifier: Modifier = Modifier) {
    Image(
        painter = rememberAsyncImagePainter(url),
        contentDescription = null,
        modifier = modifier
            .clip(CircleShape)
            .size(48.dp),
    )
}
```

Reihenfolge zählt: In einer Modifier-Chain ist das *frühere* Segment der äußere Wrapper. Der Modifier des Aufrufers sollte der äußerste sein, damit ein vom Aufrufer geliefertes `.size(...)` oder `.padding(...)` die Defaults des Composables überschreiben kann, statt von ihnen überschrieben zu werden.

## 3. Layout-Entscheidungen nicht auf der Root hartkodieren

Hat die Root des Composables `.fillMaxWidth()`, `.padding(horizontal = 16.dp)`, `.height(56.dp)` usw., kann der Aufrufer sie *nicht weglassen*. Das sind Layout-Entscheidungen, die der Parent besitzen sollte.

```kotlin
// ❌ SCHLECHT — jeder Aufrufer füllt jetzt die maximale Breite, ob er will oder nicht
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),   // ← hartkodiert
    ) { Text(text) }
}

// ✅ GUT — Aufrufer fügt .fillMaxWidth() hinzu, wenn (und nur wenn) er es will
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) { Text(text) }
}
```

Die Ausnahme sind Modifier, die Teil der **Identität** des Composables sind — was einen `Avatar` zum Avatar macht (das `.clip(CircleShape)` und eine Default-`.size(48.dp)`), nicht wo er auf dem Screen sitzt. Test: Kannst du dir einen Aufrufer vorstellen, der eine Version dieses Composables *ohne* diesen Modifier will? Wenn ja, schiebe ihn raus. Wenn nein (ein Avatar ohne `clip(CircleShape)` ist kein Avatar), behalte ihn — aber setze ihn *nach* dem Modifier des Aufrufers in die Chain (siehe §2).

## 4. Modifier-Chains als einen fluenten Ausdruck bauen

Recomposition führt den Composable-Body erneut aus — jeder Modifier-Ausdruck wird neu ausgewertet. `var modifier =` Schritt für Schritt neu zuzuweisen sieht plausibel aus, bricht aber den visuellen Fluss, lädt zu weiterer Mutation ein und produziert nichts, was eine Chain nicht auch tut.

```kotlin
// ❌ SCHLECHT — visueller Fluss in Neuzuweisungen zerbrochen; `var` lädt zu mehr Mutation ein
@Composable
fun Demo() {
    var m = Modifier
    m = m.padding(16.dp)
    m = m.fillMaxSize()
    Box(m) { }
}

// ❌ EBENFALLS SCHLECHT — gleiche Form, aufgehübscht mit .then()
@Composable
fun Demo() {
    var m = Modifier
    m = m.padding(16.dp)
    m = m.then(Modifier.fillMaxSize())
    Box(m) { }
}
```

```kotlin
// ✅ GUT
@Composable
fun Demo() {
    val m = Modifier
        .padding(16.dp)
        .fillMaxSize()
    Box(m) { }
}
```

`val`, nicht `var`: Ist die Chain gebaut, sollte nichts sie neu binden. Die Neuzuweisungs-Form ist es, die `var` nötig aussehen lässt; die Chain-Form braucht es nicht.

### Inline an der Aufrufstelle ist für kurze Chains in Ordnung

Für ein oder zwei Aufrufe baue den Modifier inline. Die „in ein `val` extrahieren"-Regel lohnt sich erst, wenn die Chain lang genug ist, um einen Namen wert zu sein, oder wenn sich dieselbe Chain wiederholt.

```kotlin
// ✅ GUT — kurze Chain inline
Box(modifier = Modifier.fillMaxWidth()) { … }
Box(modifier = Modifier.padding(8.dp).background(Color.Red)) { … }
```

### Bedingte Segmente bleiben auf der Chain

Ein häufiger Grund, zu `var` zu greifen, ist „der Modifier hängt von einer Bedingung ab". Tut er nicht — spleiße die Bedingung inline:

```kotlin
// ✅ GUT — Bedingung in der Chain, weiterhin ein Ausdruck
Box(
    modifier = Modifier
        .fillMaxWidth()
        .then(if (selected) Modifier.background(Color.Red) else Modifier),
)
```

`Modifier` (der leere Modifier) ist das Identitätselement für `.then` — er lässt dich die Chain-Form behalten, wenn ein Branch nichts beiträgt.

## 5. Mehrzeilige Formatierung an der Aufrufstelle

Wenn die Chain eines `modifier`-Arguments **drei oder mehr** Aufrufe hat, formatiere mehrzeilig mit einem Aufruf pro Zeile. Rücke die Chain so ein, dass die gepunkteten Aufrufe unter dem Wert ausgerichtet sind.

```kotlin
// ❌ SCHLECHT — drei+ Aufrufe in einer Zeile; schwer zu scannen
Box(
    modifier = modifier.fillMaxSize().padding(16.dp).weight(1f),
)

// ✅ GUT
Box(
    modifier = modifier
        .fillMaxSize()
        .padding(16.dp)
        .weight(1f),
)
```

Ein oder zwei Aufrufe bleiben in einer Zeile — die Schwelle ist die Anzahl der Aufrufe, nicht die Zeichenzahl. Hat ein einzelner Aufruf sehr lange Argumente, ist das ein anderes Problem (ein `val` extrahieren oder die Argumente kürzen).

Das gilt *nur* für einen Parameter namens `modifier`. Andere fluent-stilartige Argumente sind hier nicht abgedeckt.

## 6. Einzelne Bedingungen aus dem Layout hochziehen

Wenn der *einzige* Content eines Layouts ein `if` ist, existiert das Layout nur, um die Bedingung zu „halten". Schiebe das `if` nach außen — das Layout existiert dann nur, wenn es etwas zu zeigen hat.

```kotlin
// ❌ SCHLECHT — Column immer emittiert; nur ihr innerer Content ist bedingt
@Composable
fun A() {
    Column {
        if (showHeader) {
            Text("Title")
            Text("Subtitle")
        }
    }
}

// ✅ GUT — Column existiert nur, wenn sie Content hat
@Composable
fun A() {
    if (showHeader) {
        Column {
            Text("Title")
            Text("Subtitle")
        }
    }
}
```

Der Vorteil ist kein Performance-Gewinn — die Runtime kommt mit beidem klar — sondern dass die zweite Form sich als „Header-Section, bedingt" *liest*. Die erste liest sich als „immer-präsente Column, die Content haben kann oder nicht".

### Die Ausnahmen (und warum)

- **Das Layout trägt visuelle Semantik, die nicht bedingt ist.** Wenn der Layout-Aufruf `modifier`, `contentAlignment`, `horizontalArrangement` oder `verticalAlignment` übergibt, beschreiben diese Argumente den *Container*, nicht den Content. Die Bedingung hochzuziehen verliert diese entweder (der Container kollabiert mit dem Content) oder dupliziert sie in beide Branches. Belasse es.

  ```kotlin
  // ✅ SO LASSEN — modifier auf dem Container leistet sichtbare Arbeit
  @Composable
  fun A(modifier: Modifier = Modifier) {
      Box(modifier = modifier) {
          if (something) {
              Text("Bleh1")
              Text("Bleh2")
          }
      }
  }
  ```

- **Es gibt Geschwister zum `if`.** Das Layout hat anderen Content; das `if` ist nur ein Teil. Hochziehen zieht entweder die Geschwister raus (ändert das Layout) oder lässt eine andere Form zurück. Belasse es.

- **`if … else …` mit beiden Branches, die Composables beitragen.** Beide Branches leisten Arbeit; nichts zum Hochziehen; das Layout *ist* der geteilte Container.

  ```kotlin
  // ✅ SO LASSEN — beide Branches tragen zum Layout bei
  Box {
      if (something) Text("Hint") else innerTextField()
  }
  ```

## 7. Measure-Phase-Constraint-Dekoration

Wenn Composable A eine Größe erfasst und Composable B sie matchen muss, **lies die erfasste Größe nicht in Bs Composable-Body** (`Modifier.height(state.dp)`). Das bindet B an die Composition, wann immer sich der Measurement-State ändert.

Erfasse in einem Layout-Callback auf A; wende auf B in `Modifier.layout` an, damit nur das Layout invalidiert:

```kotlin
fun Modifier.decorateMeasureConstraints(
    decorate: (Constraints) -> Constraints,
): Modifier = layout { measurable, incoming ->
    val constraints = decorate(incoming).constrain(incoming)
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}
```

```kotlin
// Hochgezogen am gemeinsamen Parent beider Zeilen:
//   var anchorHeightPx by remember { mutableIntStateOf(0) }

// Gemessene Zeile — State nur aus onSizeChanged schreiben
RowAnchor(Modifier.onSizeChanged { size -> if (size.height != anchorHeightPx) anchorHeightPx = size.height })

// Geschwister-Zeilen — anchorHeightPx nur in layout lesen
RowSibling(
    Modifier.decorateMeasureConstraints { incoming ->
        if (anchorHeightPx > 0) {
            // Auf die eingehenden Grenzen clampen, damit der Constraint nie das Max des Parents überschreitet.
            incoming.copy(minHeight = anchorHeightPx, maxHeight = anchorHeightPx)
        } else {
            incoming
        }
    },
)
```

Nutze einen Composition-Zeit-Fallback (feste Höhe) nur, solange `anchorHeightPx` `0` ist. Siehe [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md) für das vollständige phasenübergreifende Muster.

## Kurzreferenz

| Symptom | Diagnose | Fix |
|---|---|---|
| `@Composable fun Foo(text: String)` mit `Column`/`Box`/`Text` im Body | Kein `modifier`-Param (§1) | `modifier: Modifier = Modifier` hinzufügen; an die Root reichen |
| `modifier: Modifier = Modifier` deklariert, aber nie referenziert | Param ignoriert (§2) | Auf den `modifier`-Arg des Root-Layouts anwenden |
| `modifier` an ein Kind gereicht, nicht an die Root | Falsches Ziel (§2) | Auf den `modifier` des äußersten Layouts verschieben |
| `modifier = Modifier.x().y().then(modifier)` | Modifier des Aufrufers zuletzt (§2) | Umsortieren: `modifier = modifier.x().y()` |
| `modifier = modifier.fillMaxWidth().padding(...)` an einer Allzweck-Komponente | Layout hartkodiert (§3) | Die hartkodierten Aufrufe entfernen; Aufrufer sollen sie hinzufügen |
| Geschwister-Composables in der Datei haben auch kein `modifier` | Verbreitungs-Anti-Pattern | Dieses fixen; Geschwister opportunistisch fixen |
| `mod: Modifier = Modifier` oder `wrapperModifier: Modifier = Modifier` | Falscher Name (§1) | Auf genau `modifier` umbenennen |
| `var m = Modifier` gefolgt von `m = m.xxx()`-Neuzuweisungen | Schrittweise Modifier-Konstruktion (§4) | Eine fluente Chain auf einem `val`, oder inline bauen |
| `var m = Modifier; m = m.then(Modifier.xxx())` | Gleiche Form via `.then` (§4) | `.then(Modifier.x())` in der Chain zu `.x()` zusammenfassen |
| Modifier-Branch braucht eine Bedingung | Griff zu `var` (§4) | `.then(if (c) Modifier.x() else Modifier)` in der Chain |
| `modifier = modifier.a().b().c()` in einer Zeile | Lange Chain nicht formatiert (§5) | Ein Aufruf pro Zeile, unter dem Wert eingerückt |
| `Layout { if (cond) X() }` ohne anderen Content und ohne Layout-Tuning-Args | Hochziehen (§6) | Das `if` aus dem Layout schieben |
| `Box(modifier = …) { if (cond) X() }` | Layout trägt Semantik — belassen (§6-Ausnahme) | So lassen |
| `Box { if (cond) X() else Y() }` | Beide Branches tragen bei — belassen (§6-Ausnahme) | So lassen |
| Geschwister-Lazy-Zeile liest `height(state)` aus der Messung einer anderen Zeile | Composition-Zeit-Größenkopplung (§7) | Auf gemessener Zeile erfassen; via `decorateMeasureConstraints` auf Geschwister anwenden |

## Wann NICHT anwenden

- **Composables, die kein Layout emittieren.** Ein `@Composable fun computeColor(): Color` oder ein `@Composable @ReadOnlyComposable`-Accessor emittiert keinen Layout-Knoten. Kein `modifier`-Parameter nötig (und ein `@ReadOnlyComposable` könnte keinen akzeptieren — siehe `compose-state-authoring`).
- **`@Preview`-Funktionen.** Previews sind Wegwerf-Einstiegspunkte; das Framework ruft sie ohne Aufrufer auf. Ein `modifier`-Parameter wäre ungenutzter Ballast.
- **Test-only-Composables** in `*Test`-Quellen, deren einziger Aufrufer `composeTestRule.setContent { … }` ist. Gleiche Begründung wie bei Previews.
- **Interne Layout-Primitive, die einen `modifier` als ihren *ersten erforderlichen* Parameter nehmen** (sehr selten; Framework-Ebene). Die Regel ist „erster *optionaler* Param"; einige private Utilities haben `modifier` legitim vorne als erforderlich.
- **Modifier, der imperativ aus Animations-State zusammengesetzt wird.** Ein Modifier, der durch Anhängen von Werten aus `Animatable` oder anderen prozeduralen Quellen gebaut wird, kann legitim Zwischenvariablen brauchen. Die Chain ist nicht das Ziel; Lesbarkeit ist es. Wird die Chain zum schlechteren Ausdruck, schreibe die imperative Form.
- **Slot-APIs, die Modifier speichern** in einer data class oder einem Builder (selten; meist Framework-Ebene-Code). Die Fluent-Chain-Idee betrifft die Konstruktion an der Nutzer-Stelle.
- **Test-Composables**, die bestimmte Recomposition-Formen fixieren — meist so oder so in Ordnung; refactore Test-Composables nicht rein aus Stilgründen.

Die deklarationsseitigen Regeln (§1–§3) sollten nicht übersprungen werden, nur weil „dieses Composable ist intern", „nur an einer Stelle genutzt", „ich hätte lieber nicht den extra Parameter in der Signatur" oder „wir kennen alle Aufrufer schon". Genau das sind die Rechtfertigungen, die Composables produzieren, die an dem Tag zum Einzelfall werden, an dem jemand sie zweimal aufrufen will.

## Warnzeichen im Review

| Gedanke | Realität |
|---|---|
| „Dieses Composable ist nur intern — `modifier` hinzuzufügen ist Over-Engineering" | Der Parameter ist acht Zeichen und ein Default. Es ist kein Over-Engineering; es ist die Konvention. Es wegzulassen ist das Over-Engineering — eine Eigenentscheidung gegen den Strich jeder Compose-API. |
| „Es wird nur an einer Stelle genutzt, also kenne ich die Layout-Anforderungen" | „Nur an einer Stelle" beschreibt heute. Die Kosten des Parameters zahlst du einmal; die Kosten, Aufrufer zu refactoren, wenn die zweite Nutzung auftaucht, zahlst du pro Aufrufer. |
| „Die Geschwister-Composables in dieser Datei haben auch kein `modifier`, ich passe mich dem Stil an" | Ein Anti-Pattern zu verbreiten ist keine Stilanpassung. Fixe dieses. Fixe die Geschwister opportunistisch. |
| „Der Parent will hier immer `.fillMaxWidth()`" | Dann übergibt der Parent `.fillMaxWidth()`. Das Composable entscheidet das nicht für Aufrufer, die es noch nicht getroffen hat. |
| „Ich füge es hinzu, wenn jemand es braucht" | Du bist jemand. Du brauchst es jetzt (für die Konvention). Der nächste Aufrufer fügt es auch nicht hinzu — er umgeht seine Abwesenheit. |
| „Es ist ein winziges Composable — der Modifier-Param ist Rauschen" | Der Param ist acht Zeichen an der Deklaration und null Zeichen an jeder Aufrufstelle, die ihn nicht braucht. Das „Rauschen" ist eingebildet. |
| „Ich habe `modifier` hinzugefügt, aber `.fillMaxWidth()` auf der Root behalten, damit der Home-Screen es nicht muss" | Dann kann der *Nicht*-Home-Screen-Aufrufer es nicht abschalten. Verschiebe das `.fillMaxWidth()` zum Aufrufer. |
| „Ich brauche `var` für den Modifier, weil die Chain von einer Bedingung abhängt" | Ein bedingtes Segment ist `.then(if (c) Modifier.x() else Modifier)`, weiterhin auf einer Chain. Kein `var` nötig. |
| „Drei Zeilen sind zu wenig für mehrzeilig" | Drei verkettete Aufrufe *sind* die Schwelle. Unter drei: eine Zeile. Ab drei: mehrzeilig. |
| „Die Column trägt nichts bei, aber ich behalte sie für Symmetrie" | Dann ziehe die Bedingung hoch und behalte die Column im Konsequenz-Branch — Symmetrie erhalten, kein immer-präsenter Container. |
| „Ich setze das `if` hinein, weil das Layout schon existiert" | „Schon vorhanden" ist der Bug. Das Layout sollte nicht existieren, wenn die Bedingung falsch ist. |

## Verwandt

- [`compose-slot-api-pattern`](../compose-slot-api-pattern/SKILL.md) — die andere Hälfte des Deklarierens der öffentlichen API eines wiederverwendbaren Composables: `@Composable () -> Unit`-Slots für variablen Content nehmen. Eine wiederverwendbare Komponente nimmt beides — einen `modifier`-Parameter *und* Slots — der Aufrufer besitzt Platzierung *und* Inhalt.
- [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md) — Back-Writing über Phasen und verzögerte Measurement-Reads.
