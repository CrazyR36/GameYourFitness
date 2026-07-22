---
name: kotlin-coroutines-structured-concurrency
description: "Nutze diesen Skill beim Schreiben oder Review von Kotlin-Code, der einen CoroutineScope speichert, aus init/nicht-suspendierenden APIs launcht, runBlocking aufruft oder breite Exceptions um suspend-Aufrufe fängt."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Kotlin Coroutines: Structured Concurrency

## Grundprinzip

Eine gut strukturierte Coroutine ist eine in sich geschlossene Einheit asynchroner Arbeit — ein Eingang, ein Ausgang, an einen Lifecycle gebunden, der an der Aufrufstelle bekannt ist.

**Scopes sollten meist an den Lifecycle des Aufrufers gebunden sein, nicht als Property auf dem Aufgerufenen gespeichert.** Ein gespeicherter `CoroutineScope` ist ein starkes Review-Signal: Die Klasse muss beweisen, dass sie Cancellation, Fehlerreporting, Restart-Verhalten und Lifecycle besitzt. Die meisten Repositories, Manager, Use Cases und Datenquellen können das nicht beweisen, also sollten sie stattdessen `suspend`-APIs exponieren.

Der Fix ist fast immer derselbe: **Mache die API `suspend` und lass den Aufrufer den Scope besitzen.**

## Wann diesen Skill nutzen

Du schreibst oder reviewst Kotlin-Code und siehst eines davon:

- Eine Klasse mit `private val scope: CoroutineScope` (Konstruktor-Param als Property gespeichert)
- Einen `init { scope.launch { ... } }`-Block
- Eine nicht-suspendierende öffentliche Funktion, deren Body `scope.launch { ... }` ist
- `runBlocking { ... }` in suspend-fähigem Anwendungscode oder in Tests, wo `runTest` gelten sollte
- `runCatching { suspendCall() }` oder ein `catch` auf `Exception` / `Throwable` um einen `suspend`-Aufruf ohne Rethrow von `CancellationException`
- Ein `catch (e: CancellationException)` (oder Äquivalent) um Suspension, das nicht rethrowt

## Der Silent-Cancellation-Bug

Der Grund, warum eine nicht-besessene `CoroutineScope`-Property so gefährlich ist: **Sobald ein Scope gecancelt ist, schließt jedes künftige `launch` darauf still als gecancelt ab — keine Exception, kein Log, nichts.** Die Arbeit passiert einfach nicht. Das ist einer der am schwersten zu diagnostizierenden Coroutine-Bugs, und er tritt auf, wenn eine Klasse eine langlebige Referenz auf einen Lifecycle hält, den sie nicht besitzt.

Sind APIs `suspend`, kann das nicht passieren: Der Scope des Aufrufers ist entweder lebendig (Arbeit läuft) oder die Aufrufstelle cancelt (der Aufrufer weiß es).

## Anti-Patterns und Fixes

### 1. CoroutineScope als Property gespeichert

```kotlin
// ❌ SCHLECHT
@Inject
class UserRepository(
    private val scope: CoroutineScope,
    private val api: UserApi,
) {
    fun refresh() {
        scope.launch { _state.value = api.fetchUser() }
    }
}

// ✅ GUT
@Inject
class UserRepository(
    private val api: UserApi,
) {
    suspend fun refresh(): User = api.fetchUser()
}
```

Das Repository muss überhaupt nichts mehr über Coroutines wissen. Der Aufrufer (ein ViewModel, ein Use Case) entscheidet über welchen Scope, mit welcher Fehlerbehandlung, mit welcher Cancellation-Semantik.

### 2. init-Block-Launches

```kotlin
// ❌ SCHLECHT: Side Effect zur Konstruktionszeit, unbegrenzte Arbeit
class UserSession(private val scope: CoroutineScope, private val api: Api) {
    init { scope.launch { _user.value = api.load() } }
}
```

Der Konstruktor kehrt sofort zurück. Der Aufrufer kann das Laden nicht `await`en, keine Fehler sehen, nicht canceln. Die Klasse ist „lebendig", aber ihr State ist undefiniert.

```kotlin
// ✅ GUT: expliziter Bootstrap, Aufrufer besitzt die Suspension
class UserSession(private val api: Api) {
    private var _user: User? = null
    val user: User get() = checkNotNull(_user) { "Call init() first" }

    suspend fun init() { _user = api.load() }
}
```

### 3. Fire-and-forget aus Nicht-UI-Klassen

Eine nicht-suspendierende öffentliche Funktion auf einer **Nicht-UI-Klasse** (Repository, Manager, Use Case, Datenquelle), die in einen klassen-eigenen Scope launcht. Der Aufrufer bekommt kein Ergebnis, keinen Fehler, keine Cancellation und keine Garantie, dass die Arbeit je lief.

```kotlin
// ❌ SCHLECHT — Repository mit gespeichertem Scope und Fire-and-forget-öffentlicher API
class AnalyticsClient(private val scope: CoroutineScope, private val api: Api) {
    fun track(event: Event) {
        scope.launch { api.send(event) }      // Aufrufer hat keine Ahnung, was passiert
    }
    fun signOut() {
        scope.launch { api.signOut() }        // stiller Fehlschlag, wenn Scope gecancelt
    }
}
```

```kotlin
// ✅ GUT
class AnalyticsClient(private val api: Api) {
    suspend fun track(event: Event) = api.send(event)
    suspend fun signOut() = api.signOut()
}
```

#### Ausnahme: die UI-↔-State-Holder-Grenze

UI-Frameworks sind nicht-suspendierend. Der `onClick` eines Composables, das `onKeyEvent` eines Fragments, das `onNewIntent` einer Activity — keines kann `suspend`en. Der State-Holder (ViewModel, Decompose-Component, Feature-Model usw. — alles, dessen Rolle es ist, UI-Events aufzunehmen und UI-State zu halten) **ist** die Grenze, die einmalige UI-Events in asynchrone, an den UI-Lifecycle gebundene Arbeit übersetzt. Das ist seine Aufgabe.

```kotlin
// ✅ GUT — State-Holder nimmt ein nicht-suspendierendes UI-Event auf seinen Scope
class FavouritesViewModel(private val repo: FavouritesRepository) : ViewModel() {
    fun onToggleFavourite(item: Item) {
        viewModelScope.launch { repo.toggleFavourite(item) }
    }
}

// in Compose:
ListItem(onClick = { viewModel.onToggleFavourite(item) })
```

Das ist **nicht** das Fire-and-forget-Anti-Pattern. Alle drei Bedingungen müssen gelten:

1. **State-Holder für eine UI-Fläche** — ein ViewModel, eine Decompose-Component, ein Feature-Model oder ein äquivalenter UI-State-Holder. Kein Repository, Manager, Use Case oder Datenquelle.
2. **Lifecycle-gebundener Scope** — `viewModelScope`, der `coroutineScope` einer Component, der beim Destroy gecancelt wird, das `rememberCoroutineScope()` eines Composables. Nicht `AppScope`, kein injizierter langlebiger Scope, kein ad-hoc `CoroutineScope(...)`.
3. **Der Aufrufer ist wirklich ein UI-Event** — Composable-Callback, Key-Handler, Lifecycle-Hook. Keine andere Business-Logik-Klasse, die durch den State-Holder ruft.

Die Repository-/Use-Case-/Datenquellen-Schichten darunter exponieren weiterhin `suspend`-APIs. Der State-Holder ist die *einzige* Schicht, in die die Nicht-suspendierend-→-suspendierend-Übersetzung gehört.

„Fühlt sich wie ein State-Holder an" genügt nicht. Die Frage ist „bindet die UI direkt daran?" Wenn nein, gilt die Ausnahme nicht.

### 4. Gespeicherte Scopes, die nicht injiziert sind

Dasselbe Anti-Pattern, ohne injizierten Scope:

```kotlin
// ❌ SCHLECHT — gleiches Problem, Scope wird in-class konstruiert statt injiziert
class FooManager {
    private val scope = MainScope()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
}
```

Der Lifecycle wird jetzt von nichts besessen und lebt für immer. Durch `suspend`-APIs ersetzen.

Dasselbe gilt, wenn die Instanziierung in einem Funktions-Body verschachtelt ist — `fun foo() { CoroutineScope(...).launch { … } }` ist nur ein gespeicherter Scope mit Extraschritten. Jeder Aufruf leakt einen neuen nicht-cancelbaren Scope; ihn in eine `by lazy`-Property zu bündeln behebt das zugrunde liegende Problem nicht (der Scope sollte gar nicht existieren).

### 5. DI-gebundene Singletons / Initializer, die launchen

Ein spezifisches, schwer zu erkennendes Muster: Eine DI-gebundene Klasse (`@SingleIn(AppScope)`, `@Singleton`, ein `Initializer.initialize()`) launcht eine Coroutine aus ihrem Konstruktor / `init`-Block / `initialize()`. Die gelaunchte Arbeit hat dann:

- **Eine nicht-deterministische Startzeit** — wann immer der Graph das Binding realisiert. Die Cold-Start-Reihenfolge ist unsichtbar.
- **Keinen beobachtbaren Lifecycle.** Nichts sonst im Codebase kann sehen, ob sie läuft oder gecrasht ist.
- **Keinen `stop()`- / Restart-Pfad.** Gerät Upstream in einen schlechten Zustand, ist die Loop nicht-cancelbar.
- **Keinen aufrufenden Code zum Greppen.** Leser können nicht finden, „wer startet das und wann".

§1 sagt, Scopes sollten an den Lifecycle des Aufrufers gebunden sein. Die DI-gebundene Variante verletzt das indirekt: Der *Scope* mag injiziert sein, aber das *Launch* ist in der Konstruktion versteckt — gleicher Effekt, schwerer zu sehen.

```kotlin
// ❌ SCHLECHT — Singleton bootet Arbeit als Side Effect des Konstruiertwerdens
@SingleIn(AppScope::class)
@Inject
class TokenRefresher(
    @ForScope(AppScope::class) private val scope: CoroutineScope,
    private val auth: AuthService,
) {
    init {
        scope.launch {
            while (isActive) {
                delay(5.minutes)
                auth.refreshIfNeeded()
            }
        }
    }
}

// ❌ EBENFALLS SCHLECHT — Initializer.initialize(), das *launcht*, nicht nur registriert
class TokenInvalidatorInitializer @Inject constructor(
    @ForScope(AppScope::class) private val scope: CoroutineScope,
    private val store: AuthStore,
    private val invalidator: TokenInvalidator,
) : Initializer {
    override fun initialize() {
        scope.launch { store.tokenChanges.collect { invalidator.invalidate() } }
    }
}
```

Beide sehen aus wie „application-scoped Singletons", aber die Ausnahme unter **Wann NICHT anwenden** ist *keine* Erlaubnis, aus `init` / `initialize()` zu launchen. Sie ist Erlaubnis für ein Singleton, einen Scope zu besitzen, wenn seine API suspendierend ist.

#### Frage zuerst: muss diese Background-Loop-Klasse überhaupt existieren?

Die meisten Background-Loop-Klassen existieren nur, weil niemand die Beobachtung invertiert hat. Drei Antworten, in Reihenfolge der Präferenz:

**Muster 1 — in den Consumer invertieren.** Die Klasse beobachtet State für immer, um zu reagieren, wenn er sich ändert. Aber *jemand* mutiert den State — Sign-out-Flow, Profilwechsel, Flag-Update-Handler. Diese Mutationsstelle ist bereits in einem Coroutine-Kontext und ist der natürliche Ort, die Arbeit direkt zu tun.

```kotlin
// ✅ GUT — keine Background-Loop, kein Scope, keine Klasse. Die Mutationsstelle tut die Arbeit.
class Authenticator(
    private val authStore: AuthStore,
    private val tokenInvalidator: TokenInvalidator,
) {
    suspend fun signOut() {
        authStore.clearTokens()
        tokenInvalidator.invalidate()   // direkter Aufruf an der Mutationsstelle
    }
}
```

Die Background-Loop-Klasse wird **gelöscht**. Die Arbeit passiert, wo sich der State ändert.

Wann das gilt: Der Consumer des States hat einen klaren Lifecycle (ein Use Case, ein Authenticator, ein Service-Handler) und kann die Reaktion inline ausführen.

**Muster 2 — geplante Arbeit.** Echt periodisch oder verzögert. Nutze WorkManager / BGTaskScheduler. Das Enqueue ist einmalig; mache es suspendierend und rufe es einmal aus einem Orchestrator auf, der bereits beim Start läuft.

**Muster 3 — explizite benannte Launch-Stelle.** Manchmal ist der Consumer eine synchrone API ohne beobachtbaren Lifecycle (z. B. OpenTelemetrys `Sampler.shouldSample(...)`, ein AIDL-Stub-Fanout, eine Broadcast-Receiver-Bridge). Die Beobachtung muss irgendwo coroutine-bewusst leben, aber sie muss an einer *expliziten benannten Aufrufstelle* leben — nicht im eigenen `init` der Klasse.

```kotlin
// ✅ GUT — Arbeit ist benannt; eine explizite Aufrufstelle besitzt das Launch
@SingleIn(AppScope::class)
class OtelConfigurableSampler(...) : Sampler {
    @Volatile private var delegate: Sampler = ...

    suspend fun observeRate(featureFlags: FeatureFlags) {
        featureFlags.observe(OTEL_SAMPLING_RATE).collect { rate ->
            delegate = Sampler.traceIdRatioBased(rate.coerceIn(0.0, 1.0))
        }
    }

    override fun shouldSample(...) = delegate.shouldSample(...)
}

// explizit im OTel-SDK-Init-Modul verdrahtet:
applicationScope.launch { otelSampler.observeRate(featureFlags) }
```

Wann das gilt: Der Consumer ist eine synchrone API, die *in* dich hineinruft, ohne beobachtbaren Lifecycle. Das Launch kann nicht invertierbar sein, muss aber trotzdem an einer benannten Aufrufstelle sichtbar sein.

#### Test, welches Muster passt

„Ist der Lifecycle des Consumers für mich beobachtbar?"

- **Ja, und sie sind bereits in einem Coroutine-Kontext** → Muster 1. Schiebe die Subscription in sie; lösche die Background-Loop-Klasse.
- **Die Arbeit ist periodisch / verzögert** → Muster 2. Suspend-Enqueue, einmal aufgerufen.
- **Nein, sie sind eine synchrone API ohne beobachtbaren Lifecycle** → Muster 3. Explizite Launch-Stelle, nicht `init`.

Scheint eine vierte Antwort zu passen — z. B. „Ich will ein `Bootable`-Interface, das alles für mich launcht" — ist das dasselbe Anti-Pattern mit einer Extra-Abstraktionsschicht. Der ganze Sinn ist, dass Launches *sichtbar* sind; Auto-Discovery per Interface vereitelt das.

#### Initializer sind weiterhin in Ordnung — *wenn sie nur registrieren*

Das `Initializer`-Muster ist korrekt, wenn `initialize()` einen Listener oder Hook *registriert*. Der Bug ist, wenn `initialize()` eine Coroutine *launcht*.

```kotlin
// ✅ GUTER Initializer — registriert einen Contributor, launcht nicht
class FavouritesContributorInitializer @Inject constructor(
    private val registry: ContributorRegistry,
    private val favouritesContributor: FavouritesContributor,
) : Initializer {
    override fun initialize() {
        registry.register(favouritesContributor)
    }
}
```

**`Initializer.initialize()` darf keine Coroutine `launch`en.** Wenn deins es tut, ist es ein Kandidat für Muster 1/2/3.

#### Diagnose fürs Review

- Wo ist der Startmoment definiert? Wenn „wo auch immer DI mich realisiert", schlecht.
- Wer kann beobachten, ob die Arbeit läuft? Wenn „niemand", schlecht.
- Wer kann sie stoppen oder neu starten? Wenn „niemand", schlecht.
- Kann ein Leser nach der Launch-Stelle greppen? Wenn nein, schlecht.

Sind die Antworten „der Consumer / der Orchestrator / die benannte Aufrufstelle" — bist du gut.

### 6. `CancellationException` schlucken

Ein `catch`-Block um einen `suspend`-Aufruf, der `CancellationException` matcht — direkt oder über `Exception` / `Throwable` — und nicht rethrowt, verwandelt Cancellation meist in stillen Erfolg. Die Parent-Coroutine denkt, das Kind sei fertig; das Kind läuft weiter (oder seine Side Effects tun es); der Cancellation-Contract ist gebrochen.

Gleiche Fehlerform wie der Gespeicherter-Scope-Bug aus §1, vom anderen Ende betrachtet: §1 versteckt die Arbeit *vor* dem Lifecycle des Aufrufers; das hier versteckt Cancellation *vor* der Arbeit.

```kotlin
// ❌ SCHLECHT — fängt CancellationException, rethrowt nie
suspend fun fetch() {
    try {
        api.load()
    } catch (e: Exception) {           // matcht auch CancellationException
        logger.warn("load failed", e)
    }
}

// ❌ EBENFALLS SCHLECHT — runCatching hat dasselbe Problem
suspend fun fetch() {
    runCatching { api.load() }
        .onFailure { logger.warn("load failed", it) }
}
```

Die akzeptablen Formen:

```kotlin
// ✅ Separater catch zuerst
try { api.load() }
catch (e: CancellationException) { throw e }
catch (e: Exception) { logger.warn("load failed", e) }

// ✅ Bedingter Rethrow im breiten catch
try { api.load() }
catch (e: Exception) {
    if (e is CancellationException) throw e
    logger.warn("load failed", e)
}

// ✅ ensureActive() — gut, wenn der catch gewöhnliche Fehler behandelt und du nur
// rethrowen musst, falls die aktuelle Coroutine gecancelt ist
try { api.load() }
catch (e: Exception) {
    currentCoroutineContext().ensureActive()
    logger.warn("load failed", e)
}

// ✅ runCatching mit explizitem Guard
runCatching { api.load() }
    .onFailure {
        if (it is CancellationException) throw it
        logger.warn("load failed", it)
    }

// ✅ runCatching mit getOrThrow terminiert (Cancellation fließt wieder heraus)
runCatching { api.load() }.getOrThrow()
```

Der Auslöser ist „ein suspend-Aufruf im `try`", nicht „die umgebende Funktion ist als `suspend` deklariert". Das gilt in jedem suspendierenden Body — `suspend fun`, ein `launch { … }`-Lambda, ein Flow-`collect { … }` usw.

Die häufige Ausnahme ist ein bewusst lokales Timeout: Ein `TimeoutCancellationException` aus deinem eigenen `withTimeout` zu fangen und in ein Domänen-Ergebnis umzuwandeln kann korrekt sein. Halte diesen catch eng und nah am Timeout. Nutze ihn nicht als Erlaubnis, beliebige Cancellation zu schlucken.

Einen Nicht-Cancellation-Subtyp zu fangen (`IOException`, eigene Exception-Typen) ist in Ordnung — sie erweitern `CancellationException` nicht.

### 7. `runBlocking`

`runBlocking` parkt den aktuellen Thread, bis das Lambda fertig ist. In suspend-fähigen oder lifecycle-scoped Anwendungspfaden ist es falsch: Ein Thread, der async sein sollte, ist jetzt blockiert, Structured Concurrency ist gebrochen, und jede Cancellation von Upstream hat keine Wirkung. Es ist das „Aufgerufener trifft eine strukturelle Entscheidung für den Aufrufer"-Anti-Pattern in seiner direktesten Form.

```kotlin
// ❌ SCHLECHT — zu suspend brücken, indem der aufrufende Thread blockiert wird
fun saveUser(user: User) {
    runBlocking { repository.save(user) }
}
```

Drei Fixes, je nach Kontext:

**Suspend-fähiger Anwendungscode** — mache die Funktion `suspend`:

```kotlin
// ✅ GUT
suspend fun saveUser(user: User) = repository.save(user)
```

Kann auch der unmittelbare Aufrufer nicht suspenden (ein nicht-suspendierender UI-Callback, ein `BroadcastReceiver`-Hook), nutze den vorhandenen lifecycle-gebundenen Scope an der Grenze — siehe die UI-↔-State-Holder-Ausnahme in §3. Der Fix ist an der Grenze, nicht innerhalb von `saveUser`.

Legitime Blocking-Grenzen existieren: `main` in einem CLI-Tool, Java-Interop-APIs, die synchron zurückgeben müssen, Framework-Callbacks ohne suspendierende Alternative und Migrations-Shims. Halte `runBlocking` an dieser äußeren Grenze, halte den Body klein und rufe sofort suspendierenden Code auf.

**Tests** — nutze `runTest`:

```kotlin
// ❌ SCHLECHT — Echtzeit, langsame Tests, kein virtuelles delay
@Test fun loadsUser() = runBlocking {
    assertThat(repository.load().name).isEqualTo("Alice")
}

// ✅ GUT
@Test fun loadsUser() = runTest {
    assertThat(repository.load().name).isEqualTo("Alice")
}
```

`runTest` gibt dir virtuelle Zeit (`delay()` kehrt sofort zurück), `TestDispatcher`-Integration und ordentliches Coroutine-Cleanup. Echtzeit-`runBlocking` in Tests macht sie langsam und flaky.

**`ContentProvider`-Ausnahme** — Androids `ContentProvider`-Methoden (`query`, `insert`, `update`, `delete`, `onCreate`, `call`) sind von außerhalb des Prozesses synchron. Es gibt keine Möglichkeit, sie zu suspenden. Innerhalb von *Member-Funktionen* einer `ContentProvider`-Subklasse (direkt oder indirekt — keine Companion-Objekte) ist `runBlocking` die unvermeidbare Brücke. Halte den Body so kurz wie möglich und rufe sofort suspendierenden Code auf:

```kotlin
// ✅ Nur in ContentProvider-Membern akzeptabel
class MyProvider : ContentProvider() {
    override fun query(...): Cursor? = runBlocking { dao.query(...) }
}
```

Diese Ausnahme gilt *nur* für `android.content.ContentProvider`-Subklassen. „Es ist wie ein `ContentProvider`" gilt nicht, und ein `runBlocking` im Companion-Objekt eines `ContentProvider` ist weiterhin eine reguläre Verletzung — der Helfer ist nicht Teil der synchronen Oberfläche des Frameworks.

## Kurzreferenz

| Symptom | Anti-Pattern | Fix |
|---|---|---|
| Klasse hat `private val scope: CoroutineScope` | Gespeicherter Scope auf dem Aufgerufenen | Entfernen. Öffentliche APIs `suspend` machen. |
| `init { scope.launch { ... } }` | Launch zur Konstruktionszeit | Nach `suspend fun init()` / `login()` verschieben |
| `fun foo() { scope.launch { ... } }` auf einem Repository/Manager/Use Case | Fire-and-forget aus Nicht-UI-Klasse | `suspend fun foo()`, UI-State-Holder wählt den Scope |
| `fun onClick() { viewModelScope.launch { ... } }` auf einem State-Holder, von UI aufgerufen | UI-↔-State-Holder-Grenze — in Ordnung | So lassen (siehe §3-Ausnahme) |
| `private val scope = MainScope()` | Intern konstruierter gespeicherter Scope | Dasselbe — entfernen, APIs `suspend` machen |
| `@SingleIn(AppScope) class X(scope) { init { scope.launch { … } } }` | DI-gebundenes undurchsichtiges Launch (§5) | `suspend fun run()` exponieren, aus Startup-Orchestrator launchen |
| `class Y : Initializer { override fun initialize() { scope.launch { … } } }` | Initializer, der launcht statt registriert (§5) | Dasselbe — `suspend fun run()`, Orchestrator besitzt den Lifecycle |
| `try { suspendCall() } catch (e: Exception\|Throwable\|CancellationException) { … }` ohne Rethrow | Geschluckte Cancellation (§6) | `catch (e: CancellationException) { throw e }` bevorzugen; `ensureActive()` nur, wenn es zur Absicht passt |
| `runCatching { suspendCall() }.onFailure { … }` ohne Cancellation-Guard | Gleiche Form wie oben (§6) | `if (it is CancellationException) throw it` hinzufügen oder mit `.getOrThrow()` terminieren |
| `runBlocking { … }` in suspend-fähigem App-Code | Thread-blockierende Brücke (§7) | Aufrufer `suspend` machen; oder Lifecycle-Scope an der Grenze nutzen |
| `runBlocking { … }` in einem Test | Dasselbe — Echtzeit-Brücken (§7) | `runTest { … }` nutzen |
| `runBlocking { … }` in einem `ContentProvider.query`/`insert`/…-Member | Ausnahme (§7) | Akzeptabel; Body minimal halten |

## Refactoring-Guidance

Einen bestehenden Verstoß entfernen:

1. **Beginne am Leaf.** Wähle die Klasse, die am weitesten von jeder UI entfernt ist — meist ein Repository oder eine Datenquelle. Ihre öffentliche Oberfläche sollte am leichtesten zu konvertieren sein.
2. **Konvertiere öffentliche Funktionen zu `suspend`**, eine nach der anderen. Der Compiler bringt jeden Aufrufer zum Vorschein.
3. **Wähle an jeder Aufrufstelle den Scope bewusst:** `viewModelScope`, `lifecycleScope`, `coroutineScope { }` oder einen expliziten Job. Das ist die Entscheidung, die vorher fehlte.
4. **Lösche den `CoroutineScope`-Konstruktor-Parameter**, sobald ihn nichts mehr nutzt. Entferne das Injection-Binding.

Versuche nicht, jede Klasse in einer MR zu fixen. Ein Anti-Pattern zu entfernen ist inkrementelle Arbeit.

## Wann NICHT anwenden

- **UI-State-Holder, die UI-Events aufnehmen.** Ein ViewModel/Component/Feature-Model mit `fun onClick(...) { viewModelScope.launch { ... } }` ist korrekt — das ist die Grenze, die das Framework braucht. Siehe §3-Ausnahme.
- **Lifecycle-Owner mit expliziter Cancellation- und Fehler-Policy.** Actors/Services, App-Infrastruktur oder application-scoped Singletons dürfen einen Scope besitzen, wenn sie klares `close`/`cancel`/Restart-Verhalten exponieren oder anderweitig direkt auf einen Application-Lifecycle abbilden. Injiziere `Application.applicationScope` explizit, statt einen ad-hoc zu erzeugen. **Das ist keine Erlaubnis, aus `init` / `initialize()` zu launchen** — siehe §5.
- **Bereits suspendierende APIs** brauchen nichts von dieser Arbeit.
- **Tests** nutzen manchmal `TestScope` als bewussten Ambient-Scope — das ist ein anderes Muster mit expliziter virtueller Zeitkontrolle.

## Warnzeichen im Review

Diese Gedanken bedeuten, dass das Anti-Pattern zurück ist:

| Gedanke | Realität |
|---|---|
| „Ich füge einfach einen `CoroutineExceptionHandler` zum Scope hinzu" | Das Problem ist nicht die Fehlerbehandlung. Das Problem ist, dass der Scope nicht existieren sollte. |
| „Ich muss aus `init` launchen, damit die Daten bereit sind, wenn Consumer ankommen" | Consumer, die nicht bereiten State lesen, sind der Bug. Nutze Phasing. |
| „Der Aufrufer will sich nicht mit `suspend` befassen" | Dann wählt der Aufrufer Fire-and-forget an seinem Scope. Entscheide nicht für ihn. |
| „Es ist nur ein kleiner Fire-and-forget-Aufruf" | Silent Cancellation macht jedes Fire-and-forget zu einem potenziellen stillen Fehlschlag. |
| „Wir haben die Exception gefangen und geloggt, also ist alles gut" | Hat der catch `CancellationException` rethrowt? Wenn nein, ist die Coroutine still un-gecancelt. (§6) |
| „Es ist nur ein `runBlocking`, in einem nicht-kritischen Pfad" | Jedes `runBlocking` behauptet, der Aufrufer habe keine Async-Option. Wenn doch, ist es das falsche Primitive. (§7) |
| „Tests sind mit `runBlocking` einfacher" | Sie laufen in Echtzeit, können `delay` nicht vorspulen und verlieren `TestDispatcher`-Semantik. Nutze `runTest`. (§7) |

## Verwandt

- [`kotlin-flow-state-event-modeling`](../kotlin-flow-state-event-modeling/SKILL.md) — `StateFlow`, `SharedFlow`, `Channel`, `stateIn`, Einmal-Events und verwandte Modellierung.
