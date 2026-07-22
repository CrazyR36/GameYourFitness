---
name: compose-side-effects
description: "Nutze diesen Skill beim Schreiben oder Review von Jetpack-Compose-Code mit LaunchedEffect, DisposableEffect, SideEffect, rememberCoroutineScope, rememberUpdatedState, snapshotFlow, Snackbar, Navigation, Fokus-Anforderungen, Analytics oder Event-Flow-Collection."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Compose: Side Effects

## Grundprinzip

Composable-Bodies beschreiben UI. Sie können neu komponiert, übersprungen oder abgebrochen werden. Arbeit, die die Außenwelt ändert, gehört in eine Effect-API, deren Lifecycle zur Arbeit passt.

## Wähle den kleinsten Effect

| Bedarf | API |
|---|---|
| Compose-State nach jeder erfolgreichen Recomposition an Nicht-Compose-Code publizieren | `SideEffect` |
| Einen Listener, Callback, Observer oder eine Ressource registrieren/deregistrieren | `DisposableEffect(keys...)` |
| Suspendierende, verzögerte oder gekeyte Einmal-Arbeit ausführen | `LaunchedEffect(keys...)` |
| Suspendierende Arbeit aus einem Nutzer-Event-Callback starten | `rememberCoroutineScope()` |
| Compose-Snapshot-Reads in einer Coroutine in einen Flow umwandeln | `snapshotFlow { ... }` in `LaunchedEffect` |

## Effect-Keys

Keys definieren die Restart-Identität. Ändert sich ein Key, wird der alte Effect abgebrochen/disposed und ein neuer startet.

```kotlin
// ✅ Collection neu starten, wenn sich userId ändert
LaunchedEffect(userId) {
    repository.events(userId).collect { event -> handle(event) }
}

// ❌ Unit versteckt eine sich ändernde Eingabe; Collection nutzt weiter die erste userId
LaunchedEffect(Unit) {
    repository.events(userId).collect { event -> handle(event) }
}
```

Nutze stabile, semantische Keys:

- Nutze das, dessen Lifecycle der Effect folgt: `userId`, `screenId`, `lifecycleOwner`, `focusRequester`.
- Nutze keine breiten Objekte (`state`, `viewModel`), wenn nur eine Property zählt.
- Füge keine sich ändernden Lambdas als Keys hinzu, außer du willst wirklich Restarts bei jeder Lambda-Änderung.

## Veraltete Captures vermeiden

Für lang laufende Effects, die nicht neu starten sollen, aber den neuesten Callback oder Wert brauchen, nutze `rememberUpdatedState`.

```kotlin
@Composable
fun Timeout(onTimeout: () -> Unit) {
    val latestOnTimeout by rememberUpdatedState(onTimeout)

    LaunchedEffect(Unit) {
        delay(1_000)
        latestOnTimeout()
    }
}
```

Nutze das, wenn der Lifecycle „einmal starten" ist, aber das aufgerufene Lambda frisch bleiben soll. Häufige Fälle:

- Ein Timeout- oder Splash-Effect soll nicht neu starten, wenn sich `onTimeout` ändert, aber den neuesten Callback aufrufen.
- Ein Lifecycle-Observer soll am selben Owner registriert bleiben, aber die neuesten `onStart`/`onStop`-Lambdas aufrufen.
- Ein lang laufender Collector soll seinen Collection-Lifecycle behalten, aber den neuesten Event-Handler aufrufen.

Nutze `rememberUpdatedState` nicht, um der Wahl richtiger Keys auszuweichen. Soll der geänderte Wert die Arbeit neu starten, mache ihn stattdessen zum Key:

```kotlin
// SCHLECHT: userId-Änderungen sollen die Collection neu starten, nicht einen erfassten Wert aktualisieren.
val latestUserId by rememberUpdatedState(userId)
LaunchedEffect(Unit) {
    repository.events(latestUserId).collect { event -> handle(event) }
}

// GUT: der Collection-Lifecycle folgt userId.
LaunchedEffect(userId) {
    repository.events(userId).collect { event -> handle(event) }
}
```

### `rememberUpdatedState`-Werte sind in `remember {}`-Blöcken veraltet

`rememberUpdatedState` gibt ein `State`-Objekt zurück, dessen `.value` bei jeder Recomposition aktualisiert wird. Das „latest"-Verhalten hilft nur, wenn der State **lazy gelesen** wird — in einem Effect-Body oder einem später laufenden Lambda — nicht, wenn der Wert eager erfasst wird.

In einem `remember {}`-Block läuft das Producer-Lambda einmal. Den Delegate dort zu lesen, snapshottet den aktuellen `.value` ins geremberte Objekt — künftige State-Updates erreichen es nie:

```kotlin
val latestChannelId by rememberUpdatedState(channelId)

// ❌ SCHLECHT — channelId wird einmal gelesen, wenn remembers Lambda ausführt;
// die Destination hält den Initialwert für immer
val destination = remember {
    Destination(channelId = latestChannelId)
}

// ✅ GUT — rememberUpdatedState weglassen; remember auf den sich ändernden Wert keyen
val destination = remember(channelId) {
    Destination(channelId = channelId)
}

// ✅ EBENFALLS GUT — ein umschließendes Lambda verzögert den Read auf jede Invocation
val destination = remember {
    Destination(channelId = { latestChannelId })
}
```

Dieselbe Falle gilt überall, wo ein `rememberUpdatedState`-Delegate **eager gelesen** wird statt hinter einem Lambda oder Effect-Body verzögert: in `remember` konstruierte data classes, einmal im Setup-Block von `DisposableEffect` gebaute Objekte oder jeder zur Erzeugungszeit ausgewertete Ausdruck.

Wenn der erfasste Wert die Neuerzeugung des geremberten Objekts auslösen soll, mache ihn zu einem `remember`-Key und lass `rememberUpdatedState` ganz weg. Reserviere `rememberUpdatedState` für Werte, die in einem lang lebenden Scope (Effect-Coroutine, Event-Callback) frisch bleiben müssen, **ohne** diesen Scope neu zu starten.

`rememberUpdatedState` macht Render-State auch nicht „nicht-recomposing". Muss die UI einen sich ändernden Wert anzeigen, lies normalen `State` in der Composition oder nutze die Deferred-Read-Muster in [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md) für frame-rate-Werte.

## Flow sammeln

Nutze `LaunchedEffect` für **Side-Effect-/Event-Flows**: Snackbars, Navigations-Events, Analytics-Events, Fokus-Kommandos oder andere Streams, bei denen jede Emission imperative Arbeit auslöst.

```kotlin
LaunchedEffect(events) {
    events.collect { event ->
        snackbarHostState.showSnackbar(event.message)
    }
}
```

Sammle Render-State nicht imperativ, nur um lokalen State zu mutieren. Für UI-State sammle nahe am State-Holder und reiche schlichte Werte ins UI-Composable — die **State-Holder-vs-UI-Trennung**, `collectAsStateWithLifecycle()` / `collectAsState()` und preview-freundliche Verdrahtung sind in [`compose-state-holder-ui-split`](../compose-state-holder-ui-split/SKILL.md) behandelt. Dupliziere diese Architektur hier nicht.

Auf Android bevorzuge lifecycle-bewusste Collection, wo verfügbar; nutze `collectAsState()` auf Targets ohne lifecycle-bewusste APIs.

Für Compose-State-Reads nutze `snapshotFlow`:

```kotlin
LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex }
        .distinctUntilChanged()
        .collect { index -> analytics.visibleIndex(index) }
}
```

`snapshotFlow { ... }.map { ... }` ohne terminales `collect` tut nichts.

## Nutzer-Events

Nutze `rememberCoroutineScope()`, wenn ein Klick oder eine Geste suspendierende Arbeit startet:

```kotlin
@Composable
fun SaveButton(snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            scope.launch {
                snackbarHostState.showSnackbar("Saved")
            }
        },
    ) {
        Text("Save")
    }
}
```

Vermeide „Event-Flag"-State nur, um ein `LaunchedEffect` auszulösen. Der Klick *ist* bereits das Event.

## Registrierung und Cleanup

Nutze `DisposableEffect` für gepaartes Setup/Teardown:

```kotlin
@Composable
fun ObserveLifecycle(owner: LifecycleOwner, observer: LifecycleObserver) {
    DisposableEffect(owner, observer) {
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
        }
    }
}
```

Jeder Registrierungspfad sollte einen passenden `onDispose`-Cleanup-Pfad haben.

## Häufige Fehler

| Fehler | Diagnose | Fix |
|---|---|---|
| Netzwerk-Request direkt im Composable-Body | Nebenarbeit in der Composition | Meist in ein ViewModel/State-Holder verschieben; `LaunchedEffect` nur für UI-eigene gekeyte Arbeit nutzen |
| Analytics-Property aus dem Composable-Body geschrieben | Nebenarbeit in der Composition | `SideEffect` nutzen, wenn es nach jeder erfolgreichen Recomposition publizieren soll |
| Impression/Event aus dem Composable-Body geloggt | Nebenarbeit in der Composition | `LaunchedEffect(key)` nutzen, wenn es einmal pro Key laufen soll |
| `LaunchedEffect(Unit)` erfasst sich änderndes `id` | Fehlender Key | Auf `id` keyen oder `rememberUpdatedState` nutzen, wenn es nicht neu starten darf |
| `rememberUpdatedState(id)` genutzt, damit `LaunchedEffect(Unit)` nach `id`-Änderung weiterläuft | Versteckter Lifecycle-Bug | Den Effect auf `id` keyen |
| Lang lebender Effect ruft nach Recomposition einen alten Callback auf | Veralteter Capture | Callback mit `rememberUpdatedState` wrappen und den Wrapper im Effect aufrufen |
| `rememberUpdatedState`-Delegate direkt in `remember {}` gelesen (z. B. `Destination(id = latestId)`) | Wert einmal erfasst, nie aufgefrischt | Den Wert zum `remember`-Key machen: `remember(id) { Destination(id = id) }` |
| `LaunchedEffect(state) { ... }` startet zu oft neu | Zu breiter Key | Auf die spezifische Property keyen |
| `LaunchedEffect(...) { nonSuspendSetter() }` | Falscher Effect-Typ | Meist `SideEffect`; `LaunchedEffect` nur für gekeyte Einmal-/verzögerte Arbeit behalten |
| Listener in `LaunchedEffect` hinzugefügt ohne Cleanup | Fehlendes Disposal | `DisposableEffect` nutzen |
| Aus Klick starten via `shouldShowSnackbar = true` | Event-Flag-Anti-Pattern | `rememberCoroutineScope()` im Klick-Callback nutzen |
| `if (isFocused) { … }` oder Fokus-Read im Composable-Body für Nebenarbeit | Nebenarbeit während der Composition | `LaunchedEffect(focused) { … }` oder `snapshotFlow` |
| `onSizeChanged { heightState = it.height }` auf gemessenem Composable | Layout → Composition Back-Write, wenn ein Geschwister `heightState` in der Composition liest | Geschwister müssen die Höhe in der Measure-Phase konsumieren, nicht `Modifier.height(state.dp)` in der Composition |

## Fokus und Messung

**Fokus:** Fokus im Composable-Body zu lesen, um **Nebenarbeit** anzutreiben (Preloading, Analytics, Toasts), führt diese Arbeit während der Composition aus. Beobachte Fokus stattdessen in einem Effect:

```kotlin
// ❌ SCHLECHT — Nebenarbeit läuft während der Composition jedes Mal, wenn `focused` true ist,
// inklusive transienter Fokus-Durchläufe; `SideEffect` läuft nach jeder erfolgreichen Recomposition erneut
@Composable
fun Preloader(interactionSource: MutableInteractionSource) {
    val focused by interactionSource.collectIsFocusedAsState()
    if (focused) {
        preloadImages()
    }
}

// ✅ GUT — Nebenarbeit in einem gekeyten Effect
@Composable
fun Preloader(interactionSource: MutableInteractionSource) {
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) preloadImages()
    }
}
```

Nutze `snapshotFlow { … }` in `LaunchedEffect`, wenn du mehrere Snapshot-Reads samplen oder schnelle Änderungen debouncen musst, ohne den Effect auf jeden abgeleiteten Wert zu keyen. Für TV-/D-Pad-Fokus-Navigations-Semantik siehe [`compose-focus-navigation`](../compose-focus-navigation/SKILL.md).

**Messung:** `onSizeChanged` / `onGloballyPositioned` sind valide **Callbacks**, aber sie feuern während der Layout-Phase. Dort Snapshot-State zu schreiben ist nur sicher, wenn keine frühere Phase ihn liest. Liest ein Geschwister diesen State in der Composition, schreibt Layout in die Composition zurück und das Geschwister komponiert bei jedem Measure-Durchlauf neu. Wende erfasste Dimensionen in `Modifier.layout` an (siehe [`compose-modifier-and-layout-style`](../compose-modifier-and-layout-style/SKILL.md) §7 und [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md)).

## Warnzeichen im Review

- „Das läuft nur einmal" über Code in einem Composable-Body.
- `LaunchedEffect(Unit)` in einer Funktion mit sich ändernden Parametern.
- Eine Flow-Kette in einem Effect ohne terminale Collection.
- Effects, deren Keys gewählt sind, um Lint ruhigzustellen, statt Lifecycle zu modellieren.
- Callback-Lambdas, die aus lang lebenden Effects genutzt werden, ohne Key oder `rememberUpdatedState`.
- `rememberUpdatedState`-Delegate eager in einem `remember {}`-Block oder Objekt-Konstruktor gelesen — der Wert wird einmal erfasst und nie aufgefrischt.
