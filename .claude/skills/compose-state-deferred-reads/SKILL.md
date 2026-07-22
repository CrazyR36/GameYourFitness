---
name: compose-state-deferred-reads
description: "Nutze diesen Skill, wenn Jetpack-Compose-Code Scroll-, Animations-, Gesten- oder anderen frame-rate-State in der Composition liest, sich ändernde Werte über Composable-Grenzen reicht, Wert-Form-Layout-/Draw-Modifier nutzt oder beobachtbaren State aus einer späteren Phase in eine bereits gelaufene zurückschreibt."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Compose: State Deferred Reads

## Grundprinzip

State-Reads invalidieren die Phase, die sie liest. Wird ein `State<T>` in einem Composable-Body gelesen, invalidieren Änderungen die Composition. Wird er in Layout oder Draw gelesen, können Änderungen nur Layout oder Draw invalidieren. Frame-rate-State wie Scroll-Offsets, Animationen und Drag-Positionen gehört meist in Layout/Draw, nicht in die Composition.

**Back-Writing** ist der symmetrische Fehlermodus: beobachtbaren State aus einer Phase schreiben, die die Invalidierung einer früheren Phase auslöst. Compose-Phasen laufen Composition → Layout → Draw. Snapshot-gestützten State aus Layout oder Draw in State zu schreiben, der in der Composition gelesen wird, invalidiert die Composition; während der Composition in State zu schreiben, der früher in derselben Composition gelesen wird, tut dasselbe. Beides plant Extra-Arbeit — oft kaskadierend in Geschwister-Lazy-Items.

Der Fix ist strukturell: Behalte den `State<T>` oder ein Provider-Lambda und lies den Wert in einem Layout-/Draw-Callback; erfasse Messungen in Callbacks und wende sie in der Measure-Phase an, nicht durch das Lesen von Measurement-State in Geschwister-Composable-Bodies.

## Wann diesen Skill nutzen

- `val x by animate*AsState(...)` wird an `Modifier.offset(x = ...)`, `Modifier.size(...)`, `Modifier.graphicsLayer(...)` oder einen anderen Wert-Form-Modifier übergeben.
- `LazyListState.firstVisibleItemScrollOffset`, `ScrollState.value`, `Animatable.value` oder Gesten-State wird in einem Composable-Body gelesen.
- Ein Composable nimmt `scrollOffset: Int`, `progress: Float`, `dragOffset: Offset` oder ähnliche frame-rate-Werte.
- Recomposition-Zähler steigen bei Scroll, Animation oder Gesten, selbst wenn Daten stabil sind.
- Ein Composable-Body ruft `stateMap[key] = …`, `list.addAll(…)` oder Ähnliches bei jeder Recomposition auf (Back-Writing Composition → Composition).
- Ein Lazy-Item erfasst die Größe mit `onSizeChanged` / `onGloballyPositioned` und ein Geschwister liest diese Höhe in der Composition (`Modifier.height(state.dp)`) — Back-Writing Layout → Composition.

## 0. Back-Writing

**Back-Writing** = beobachtbaren State in einer Phase schreiben, die die Invalidierung einer früheren (oder der aktuellen) Phase auslöst. Compose läuft Composition → Layout → Draw, also:

- Snapshot-State während der Composition schreiben, der in derselben Composition gelesen wird.
- Snapshot-State während des Layouts schreiben (z. B. aus `Modifier.layout`, `onSizeChanged`, `onGloballyPositioned`), der während der Composition gelesen wird.
- Snapshot-State während des Draws schreiben, der während der Composition oder des Layouts gelesen wird.

In allen Fällen plant der Schreiber Extra-Invalidierungsdurchläufe — oft kaskadierend in Geschwister-Lazy-Items.

Schreibe nicht bei jedem Durchlauf aus dem Composable-Body in `mutableStateOf`, `mutableStateListOf`, `mutableStateMapOf` oder anderen Snapshot-gestützten State:

```kotlin
// ❌ SCHLECHT — mutiert die beobachtbare Map während der Composition; Geschwister komponieren wiederholt neu
@Composable
fun MergeOverlay(parent: Map<Key, ViewState>, overlay: Map<Key, ViewState>): Map<Key, ViewState> {
    val merged = remember { mutableStateMapOf<Key, ViewState>() }
    merged.clear()
    merged.putAll(parent)
    merged.putAll(overlay)   // Back-Writing Composition → Composition
    return merged
}

// ✅ GUT — read-only Merge; keine Composition-Zeit-Writes
@Composable
fun MergeOverlay(parent: Map<Key, ViewState>, overlay: Map<Key, ViewState>): Map<Key, ViewState> =
    remember(parent, overlay) {
        if (overlay.isEmpty()) parent else parent + overlay
    }
```

Bevorzuge `remember(keys) { … }` für abgeleitete read-only Snapshots. Reserviere `mutableState*`-Writes für Event-Callbacks (`onClick`) oder Effects — nicht zum Neuaufbau abgeleiteter Daten bei jeder Composition.

Callbacks wie `onSizeChanged` schreiben *während des Layouts*. Das ist nur sicher, wenn keine frühere Phase den resultierenden State liest — siehe phasenübergreifende Messung unten.

### Phasenübergreifende Messung (Layout → Composition Back-Write)

Wenn Zeile A misst und Zeile B As Höhe matchen muss, lies As erfasste Größe nicht in Bs Composable-Body. `onSizeChanged` schreibt während des Layouts; liest B es in der Composition, hat Layout gerade in die Composition zurückgeschrieben:

```kotlin
var anchorHeightPx by remember { mutableIntStateOf(0) }

// ❌ SCHLECHT — B liest Measurement-State in der Composition; Einfügen/Fokus können B doppelt neu komponieren
RowA(Modifier.onSizeChanged { anchorHeightPx = it.height })
RowB(Modifier.height(with(LocalDensity.current) { anchorHeightPx.toDp() }))  // Composition-Read

// ✅ GUT — auf A erfassen; auf B nur in der Measure-Phase anwenden
RowA(Modifier.onSizeChanged { if (it.height != anchorHeightPx) anchorHeightPx = it.height })
RowB(
    Modifier.decorateMeasureConstraints { incoming ->
        if (anchorHeightPx > 0) incoming.copy(minHeight = anchorHeightPx, maxHeight = anchorHeightPx)
        else incoming
    },
)
```

`decorateMeasureConstraints` ist ein kleiner Layout-Helfer (siehe [`compose-modifier-and-layout-style`](../compose-modifier-and-layout-style/SKILL.md)). Solange die Höhe unbekannt ist, nutzen Geschwister einen festen Fallback in der Composition; sobald bekannt, invalidiert nur das Layout — keine zusätzliche Composition-Kaskade.

## 1. Block-Form-Modifier bevorzugen

Mehrere Modifier haben Wert-Formen und Block-Formen. Die Wert-Form erhält bereits in der Composition gelesene Werte; die Block-Form kann während Layout oder Draw lesen.

```kotlin
// Vorher: animierter Wert wird in der Composition durch den `by`-Delegate gelesen
@Composable
fun SelectionPill(selectedIndex: Int) {
    val offsetX by animateDpAsState(120.dp * selectedIndex)
    Box(Modifier.offset(x = offsetX))
}

// Nachher: State bleibt, der Wert wird im Layout-Phase-offset-Block gelesen
@Composable
fun SelectionPill(selectedIndex: Int) {
    val offsetX = animateDpAsState(120.dp * selectedIndex)
    Box(
        Modifier.offset {
            IntOffset(offsetX.value.roundToPx(), 0)
        },
    )
}
```

Häufige Ersetzungen:

| Composition-Read | Deferred Read |
|---|---|
| `Modifier.offset(x = animatedX)` | `Modifier.offset { IntOffset(animatedX.value.roundToPx(), 0) }` |
| `Modifier.graphicsLayer(translationY = y)` | `Modifier.graphicsLayer { translationY = yProvider() }` |
| `val radius by animateFloatAsState(...); drawBehind { drawCircle(radius = radius) }` | `val radius = animateFloatAsState(...); drawBehind { drawCircle(radius = radius.value) }` |

Der `drawBehind`-Block ist bereits Draw-Phase; wichtig ist, dass der `State.value`-Read ebenfalls in diesem Block passiert.

## 2. Provider über Composable-Grenzen reichen

Würde der schnell wechselnde Wert eine Composable-Grenze überschreiten, reiche ein Provider-Lambda statt eines Snapshot-Werts:

```kotlin
// Vorher: HomeScreen liest Scroll-Offset in der Composition und reicht den Wert nach unten
@Composable
fun HomeScreen() {
    val listState = rememberLazyListState()
    LazyColumn(state = listState) {
        item { HeroImage(scrollOffset = listState.firstVisibleItemScrollOffset) }
    }
}

@Composable
fun HeroImage(scrollOffset: Int, modifier: Modifier = Modifier) {
    AsyncImage(
        model = "...",
        modifier = modifier.graphicsLayer(translationY = -scrollOffset / 2f),
    )
}

// Nachher: der einzige Read passiert in graphicsLayer
@Composable
fun HomeScreen() {
    val listState = rememberLazyListState()
    LazyColumn(state = listState) {
        item {
            HeroImage(
                scrollOffsetProvider = {
                    if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset
                    } else {
                        0
                    }
                },
            )
        }
    }
}

@Composable
fun HeroImage(scrollOffsetProvider: () -> Int, modifier: Modifier = Modifier) {
    AsyncImage(
        model = "...",
        modifier = modifier.graphicsLayer {
            translationY = -scrollOffsetProvider() / 2f
        },
    )
}
```

Versieh Provider-Parameter mit dem Suffix `Provider`, wenn das den Deferred-Read-Contract verdeutlicht.

## 3. Weitere Layout-/Draw-Read-Stellen

State-Reads können auch verzögert werden in:

- `Modifier.layout { measurable, constraints -> ... }`
- Eigenem `Alignment.align(...)`
- `drawWithContent`, `drawBehind` und anderen Draw-Modifiern
- Block-Form-Layer-/Layout-Modifiern wie `graphicsLayer { ... }` und `offset { ... }`

Nutze diese, wenn der State ändert, *wo* etwas platziert oder gemalt wird. Entscheidet der State, *welche Composables existieren*, gehört er in die Composition.

## Kurzreferenz

| Symptom | Diagnose | Fix |
|---|---|---|
| `val x by animateFloatAsState(...)` dann `Modifier.offset(...)` | `by` liest in der Composition | `State<Float>` behalten und `.value` in `offset {}` lesen |
| `Modifier.graphicsLayer(translationY = animatedY)` | Property-Argument-Form nutzt Composition-Werte | `graphicsLayer { translationY = ... }` nutzen |
| `Child(scrollOffset = listState.firstVisibleItemScrollOffset)` | Schnell wechselnder Wert überschreitet Grenze | `Child(scrollOffsetProvider = { ... })` |
| Draw-Block komponiert weiterhin jeden Frame neu | Wert wurde vor dem Draw-Block gelesen | Den `State.value`-Read in den Draw-Block verschieben |
| State wählt zwischen verschiedenen UI-Branches | Composition-Entscheidung | Den Read in der Composition behalten |
| `mergedMap.putAll(overlay)` im Composable-Body | Back-Writing Composition → Composition | `remember(parent, overlay) { parent + overlay }` |
| Geschwister `Modifier.height(measuredPx.toDp())` | Back-Writing Layout → Composition | Measure-Phase-Constraint-Dekoration |
| Identitäts-Cache für read-only Merge | Risiko veralteter Overlays | `remember(keys)` auf unveränderlichem Ergebnis |

## Wann NICHT anwenden

- Der State steuert, welche Composables emittiert werden.
- Die Animation ist einmalig, günstig und Klarheit gewinnt.
- Du schreibst Tests, wo direkte Wert-Assertions einfacher sind.
- Laufzeit-Evidenz zeigt, dass Recomposition nicht der Flaschenhals ist.

## Verwandt

- [`compose-state-authoring`](../compose-state-authoring/SKILL.md) — wann `mutableState*` in die Composition gehört vs. in Callbacks.
- [`compose-state-holder-ui-split`](../compose-state-holder-ui-split/SKILL.md) — wo die State-Holder-vs-schlichte-UI-Trennung gilt, wenn man Provider/Lambdas über Grenzen reicht.
- [`compose-stability-diagnostics`](../compose-stability-diagnostics/SKILL.md) — Parameter-Stabilität und Compiler-Reports.
- [`compose-modifier-and-layout-style`](../compose-modifier-and-layout-style/SKILL.md) — Measure-Phase-Constraint-Dekorations-Helfer.
