---
name: kotlin-flow-state-event-modeling
description: "Nutze diesen Skill beim Schreiben oder Review von Kotlin-Flow-State- und -Event-APIs mit StateFlow, MutableStateFlow.update, SharedFlow, Channel, stateIn, SharingStarted, .value, receiveAsFlow, Einmal-Events oder Sentinel-Initialwerten."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Kotlin Flow: State- und Event-Modellierung

## Grundprinzip

**Wähle das Primitive, das zu Replay-, Fan-out- und Synchron-Read-Anforderungen passt.** `StateFlow`, `SharedFlow`, `Channel`-gestützte Flows und cold `Flow` unterscheiden sich in Buffering, wer jede Emission sieht und ob `.value` existiert. Falsche Wahl verliert Events, leakt Sharing-Coroutines oder zwingt falsche Domänen-Sentinels in den State.

## Wann diesen Skill nutzen

Du schreibst oder reviewst Kotlin-Code mit:

- `MutableStateFlow<T>(SomeSentinel)` — `NoUser`, `Empty`, `Loading` usw. — weil der echte Wert async ist
- `.stateIn(...)` in einer Funktion aufgerufen statt einer Property zugewiesen
- `SharingStarted.WhileSubscribed(...)` auf einem Flow, dessen `.value` synchron gelesen wird und frisch bleiben muss
- `MutableSharedFlow` für Navigations-Events, Snackbars oder andere Einmal-Emissionen, bei denen Verlust ein Bug wäre
- `.map { }` auf einem `StateFlow`, wenn Consumer weiterhin synchrones `.value` brauchen
- `MutableStateFlow.value = _state.value.copy(...)` oder Update-Code, der teure Objekte innerhalb von `update { ... }` baut

## SharedFlow für Single-Consumer-Einmal-Events

`SharedFlow`-Defaults haben keinen Replay-Buffer. Sammelt im exakten Moment der Emission nichts, ist das Event weg. Für einen **einzelnen UI-Consumer**, der Exactly-once-Events wie Navigation oder Snackbars behandelt, passt ein gebufferter `Channel`, exponiert als `Flow`, oft besser zur Semantik:

```kotlin
// ❌ SCHLECHT
private val _navEvents = MutableSharedFlow<NavigationEvent>()
val navEvents: SharedFlow<NavigationEvent> = _navEvents.asSharedFlow()

// ✅ GUT
private val _navEvents = Channel<NavigationEvent>(Channel.BUFFERED)
val navEvents: Flow<NavigationEvent> = _navEvents.receiveAsFlow()
```

`Channel.receiveAsFlow()` ist **Fan-out, kein Broadcast**: Bei mehreren Collectors wird jedes Event an **einen** Collector geliefert. `Channel.BUFFERED` ist bounded, also können Sends suspenden und `trySend` fehlschlagen. Müssen mehrere Observer alle dasselbe Event sehen, nutze stattdessen expliziten State, dauerhafte Speicherung oder einen bewusst konfigurierten `SharedFlow`.

## StateFlow verschmutzt mit ungültigen Sentinel-Defaults

`StateFlow` erzwingt einen Initialwert. Ist der echte Wert async, erfinden Entwickler manchmal falsche Domänenwerte — `NoUser`, `EmptyUser`, Platzhalter-IDs — und jeder Consumer ist gezwungen, diesen Sentinel als echte Daten zu behandeln.

```kotlin
// ❌ SCHLECHT — Sentinel leakt in den Typ
class UserSession(private val db: Db) {
    private val _user = MutableStateFlow<User>(NoUser)
    val user: StateFlow<User> = _user.asStateFlow()
    init { scope.launch { _user.value = db.load() } }
}
```

Ein Fix ist **Phasing**: den `StateFlow` erst exponieren, wenn der echte Wert existiert.

```kotlin
// ✅ GUT — Bootstrap suspendet; Observer sehen nur echte User
class UserSession(private val db: Db) {
    private var _user: MutableStateFlow<User>? = null
    val user: StateFlow<User>
        get() = checkNotNull(_user) { "Call login() first" }

    suspend fun login() {
        _user = MutableStateFlow(db.load())
    }
}
```

Ist Abwesenheit, Laden oder Fehler ein echter State, modelliere ihn explizit (`User?`, `sealed interface UserUiState`, `Result` usw.). Der Bug ist ein falscher Domänenwert, der sich als echte Daten ausgibt, nicht jeder Initialwert.

## MutableStateFlow mit `update { ... }` mutieren

Bevorzuge `MutableStateFlow.update { current -> ... }` gegenüber dem Lesen von `.value` und Zurückschreiben. `update` wendet die Transformation atomar gegen den neuesten State an, was verlorene Updates vermeidet, wenn mehrere Coroutines denselben State mutieren.

```kotlin
// SCHLECHT — read/modify/write kann nebenläufige Updates verlieren.
_state.value = _state.value.copy(
    selectedId = id,
    details = details,
)

// GUT — Transformation startet vom neuesten State.
_state.update { current ->
    current.copy(
        selectedId = id,
        details = details,
    )
}
```

Halte Objekterzeugung außerhalb des `update`-Blocks, außer sie braucht den aktuellen State. Das Update-Lambda kann wiederholt werden, also läuft teure Arbeit oder Side Effects darin ggf. mehr als einmal:

```kotlin
// GUT — details hängt nicht vom aktuellen State ab, also einmal bauen.
val details = Details.from(response)
_state.update { current ->
    current.copy(details = details)
}

// GUT — abgeleiteter Wert hängt vom aktuellen State ab, also drinnen berechnen.
_state.update { current ->
    val nextItems = current.items.replaceById(updatedItem)
    current.copy(items = nextItems)
}
```

Der Block sollte eine reine, schnelle State-Transformation sein: keine Netzwerkaufrufe, Datenbankschreibvorgänge, Logging-Side-Effects, zufällige IDs oder Zeit-Reads, außer diese Werte wurden vor dem Block erfasst.

## `stateIn()` in einer Funktion

```kotlin
// ❌ SCHLECHT — neue Sharing-Coroutine bei jedem Aufruf
fun getPreferences(): StateFlow<Prefs> =
    repo.prefsFlow.stateIn(scope, SharingStarted.Eagerly, Prefs.Default)
```

Jeder Aufruf von `getPreferences()` startet eine frische Coroutine auf `scope`, die nie abschließt. Die Performance stirbt bei wiederholten Reads schnell.

```kotlin
// ✅ GUT — eine geteilte Instanz, einmal berechnet
val preferences: StateFlow<Prefs> =
    repo.prefsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs.Default)
```

## `WhileSubscribed` mit synchronem `.value`

`SharingStarted.WhileSubscribed(timeout)` trennt den Upstream, wenn keine aktiven Collectors da sind. Während der Trennung gibt `.value` den letzten gecachten Wert zurück, der veraltet oder noch der Initialwert sein kann.

**Regel:** Muss `.value` frisch oder ohne aktiven Collector initialisiert sein, nutze `SharingStarted.Eagerly` oder explizite Initialisierung. `WhileSubscribed` ist in Ordnung, wenn veraltete/gecachte Werte akzeptabel sind und Consumer primär asynchron sammeln.

## `.map` auf `StateFlow` verliert `.value`

```kotlin
// ❌ SCHLECHT — `name.value` kompiliert nicht; es ist jetzt ein einfacher Flow
val name: Flow<String> = userState.map { it.name }
```

Brauchst du synchrones `.value`, terminiere die Kette mit `.stateIn(...)`:

```kotlin
// ✅ GUT
val name: StateFlow<String> = userState
    .map { it.name }
    .stateIn(viewModelScope, SharingStarted.Eagerly, userState.value.name)
```

Community-„derived state flow"-Utilities führen die Transformation bei jedem `.value`-Read aus — nur akzeptabel für schnelle, idempotente Transformationen. Standardmäßig `.stateIn(...)`.

## Entscheidung: welcher Flow-Typ?

| Bedarf | Primitive |
|------|-----------|
| State, der immer einen Wert hat, gelesen von async Collectors **und** synchronem Code | `StateFlow`, oft mit `SharingStarted.Eagerly`, wenn `.value` zählt |
| Hot Stream, mehrere Subscriber, **keine** Anforderung an synchrones `.value` | `SharedFlow` |
| Diskrete Events für **einen** Consumer, Exactly-once-Übergabe | `Channel(BUFFERED).receiveAsFlow()` erwägen |
| Cold Stream, ein Consumer pro Collection | Einfacher `Flow` |

Bist du versucht, zu `SharedFlow` zu greifen, frage: Wäre das Verwerfen einer Emission ein Bug, und wie viele Consumer müssen sie sehen? Muss ein Consumer sie exactly-once behandeln, passt vielleicht ein `Channel`. Muss jeder Observer sie sehen, modelliere dauerhaften State oder konfiguriere bewusst einen Broadcast-Stream.

## Kurzreferenz

| Symptom | Problem | Fix |
|---------|---------|-----|
| `MutableStateFlow<X>(FakeDomainValue)` | Ungültiger Platzhalter-Default | Abwesenheit explizit modellieren oder Phase-Initialisierung nutzen |
| `MutableSharedFlow<Event>` für Single-Consumer-Nav/-Snackbar | Verlustbehafteter Default-Event-Stream | `Channel(BUFFERED).receiveAsFlow()` erwägen |
| `fun foo() = flow.stateIn(...)` | Sharing-Coroutine pro Aufruf | Als `val` / geteilte Instanz machen |
| `WhileSubscribed` + `.value` muss frisch/initialisiert sein | Veraltete oder Initialdaten | `SharingStarted.Eagerly` oder explizite Initialisierung |
| `stateFlow.map { ... }` als State konsumiert | Verlorenes `.value` | Mit `.stateIn(...)` terminieren |
| `_state.value = _state.value.copy(...)` | Nicht-atomares read/modify/write | `_state.update { it.copy(...) }` |
| Teure Objekterzeugung in `update { ... }`, die den aktuellen State nicht nutzt | Arbeit kann sich bei Update-Retry wiederholen | Vor `update` bauen; nur Current-State-Transformationen drinnen halten |

## Warnzeichen im Review

| Gedanke | Realität |
|---------|---------|
| „Wir brauchen `SharedFlow`, weil es mehrere Subscriber gibt" | Mehrere Subscriber ändern die Semantik. `Channel.receiveAsFlow()` ist kein Broadcast; wähle das Event-Modell bewusst. |
| „Wir nutzen `WhileSubscribed`, um Ressourcen zu sparen" | Nur wenn veraltete/initiale `.value`-Reads akzeptabel sind. Vor dem Anwenden verifizieren. |
| „Ich nutze einen Sentinel, bis echte Daten laden" | Consumer behandeln ihn als echte Domäne; bevorzuge explizite UI-/State-Modellierung oder Phasing. |
| „Ich konstruiere das neue Objekt in `update`, weil es praktisch ist" | Das Lambda kann retryen. Konstruiere außerhalb, außer es hängt vom aktuellen State ab. |

## Verwandt

- [`kotlin-control-flow`](../kotlin-control-flow/SKILL.md) — `when`, Guard-Bedingungen, Exhaustiveness, Smart Casts und Early Returns beim Modellieren von State und Events wählen.
- [`kotlin-coroutines-structured-concurrency`](../kotlin-coroutines-structured-concurrency/SKILL.md) — Scope-Ownership, init-Launches, Fire-and-forget-Grenzen, Cancellation, `runBlocking`
- [`compose-side-effects`](../compose-side-effects/SKILL.md) — Event-Flows sammeln und Side Effects in Compose verdrahten
- [`compose-state-holder-ui-split`](../compose-state-holder-ui-split/SKILL.md) — wo State-Holder Flows an die UI exponieren
