---
name: compose-state-hoisting
description: "Nutze diesen Skill bei der Entscheidung, wo Jetpack-Compose-UI-Element-State oder UI-Logik leben soll: lokaler remember-State, hochgezogene Composable-Parameter, eine einfache State-Holder-Klasse oder ein ViewModel/Component auf Screen-Ebene."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Compose-State-Hoisting

## Grundprinzip

Ziehe State nur so weit hoch, wie die Logik es braucht. Halte einfachen UI-Element-State lokal, verschiebe geteilten UI-Element-State zum niedrigsten gemeinsamen Composable-Owner, extrahiere einen einfachen State-Holder, wenn reines UI-Verhalten zum Konzept wird, und nutze einen Screen-State-Holder, wenn Business-Logik oder App-Daten im Spiel sind.

## Entscheidungshilfe

| Situation | Owner |
|---|---|
| Ein Composable liest/schreibt einfachen State | Lokal halten mit `remember` / `rememberSaveable` |
| Geschwister- oder Eltern-Composables müssen ihn lesen/schreiben | State und Events zum niedrigsten gemeinsamen Composable-Vorfahren hochziehen |
| Zusammengehöriger UI-Element-State plus UI-Logik macht ein Composable schwer lesbar, previewbar oder testbar | Einfache State-Holder-Klasse extrahieren, in der Composition remembert |
| Repository-Aufrufe, Persistenz, Business-Regeln oder Erzeugung von Screen-UI-State sind beteiligt | Screen-State-Holder wie ein `ViewModel` oder Component nutzen |

UI-Element-State umfasst Dinge wie Ausklappen, Sheet-Sichtbarkeit, Scroll-Position, Fokus, Textfeld-Editier-State, Auswahl sowie Animations-/Interaktions-State. Screen-UI-State sind App-Daten, die für die Anzeige aufbereitet sind.

Ist UI-Element-State eine Eingabe für Business-Logik, muss er ggf. ebenfalls im Screen-State-Holder leben. Beispiel: Text, mit dem repository-gestützte Vorschläge abgefragt werden, gehört zu dem State-Holder, der diese Vorschläge erzeugt.

## Auslöser für einen einfachen State-Holder

Extrahiere einen einfachen State-Holder, wenn mehreres davon zutrifft:

- Mehrere zusammengehörige `remember`-Werte werden von denselben Callbacks koordiniert.
- Scroll-, Fokus-, Text-, Auswahl- oder Sheet-State braucht benannte Operationen wie `clear`, `submit`, `jumpToTop` oder `openFilters`.
- Abgeleitete UI-Flags sind über das Composable verstreut.
- Kind-Composables erhalten Mechanik, die ihnen konzeptionell nicht gehört.
- Previews oder Tests müssen eine lange Folge von UI-Details durchspielen, um ein Verhalten zu prüfen.
- Hilfsfunktionen brauchen viele State-Parameter, nur um das Composable lesbar zu halten.

Extrahiere nicht für ein Boolean, ein Textfeld oder triviale Show/Hide-Logik. Zeremonie ist keine Separation of Concerns.

## Muster

Nutze eine einfache Klasse für UI-Element-State und UI-Logik plus eine `remember...State`-Funktion für composition-eigene Objekte:

```kotlin
@Stable
class ProductSearchState(
    query: String,
    private val listState: LazyListState,
    private val focusRequester: FocusRequester,
) {
    var query by mutableStateOf(query)
        private set

    var filtersOpen by mutableStateOf(false)
        private set

    val canClear: Boolean
        get() = query.isNotEmpty()

    fun updateQuery(value: String) {
        query = value
    }

    fun clear() {
        query = ""
        focusRequester.requestFocus()
    }

    suspend fun jumpToTop() {
        listState.animateScrollToItem(0)
    }
}

@Composable
fun rememberProductSearchState(
    initialQuery: String = "",
    listState: LazyListState = rememberLazyListState(),
    focusRequester: FocusRequester = remember { FocusRequester() },
): ProductSearchState {
    return remember(listState, focusRequester) {
        ProductSearchState(initialQuery, listState, focusRequester)
    }
}
```

Das Composable rendert aus dem State-Holder und ruft intent-artige Methoden auf. Muss ein Elternteil dasselbe UI-Verhalten koordinieren, nimm den State-Holder als Parameter mit Default:

```kotlin
@Composable
fun ProductSearchPanel(
    state: ProductSearchState = rememberProductSearchState(),
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    SearchField(
        query = state.query,
        onQueryChange = state::updateQuery,
        onClear = state::clear,
    )

    JumpToTopButton(onClick = {
        scope.launch { state.jumpToTop() }
    })
}
```

## Composition-Ownership

Einfache State-Holder, die mit `remember` erzeugt werden, folgen dem Composable-Lifecycle. Das macht sie zu einem guten Zuhause für Compose-UI-Objekte wie `LazyListState`, `FocusRequester`, `PagerState`, `DrawerState` und `TextFieldState`.

Halte suspend-UI-Operationen, die eine Frame-Clock brauchen (z. B. Scroll- oder Drawer-Animationen), in einer composition-scoped Coroutine (`rememberCoroutineScope`, `LaunchedEffect` oder ein anderer composition-eigener Scope). Verschiebe diese Aufrufe nicht in den `viewModelScope`.

## State speichern

Nutze `rememberSaveable` oder einen eigenen `Saver` nur für Werte, die eine Activity- oder Prozess-Neuerstellung überleben sollen, etwa ein Query-String, ausgewählte Filter-IDs oder ein aktueller Tab-Key.

Versuche nicht, Laufzeitobjekte wie `LazyListState`, `FocusRequester`, Coroutine-Scopes oder Callbacks direkt zu speichern. Speichere die minimalen serialisierbaren Werte, die nötig sind, um das Verhalten wieder aufzubauen.

## Häufige Fehler

| Fehler | Fix |
|---|---|
| Jeden lokalen State-Wert „vorsichtshalber" zum Elternteil hochziehen | Zum niedrigsten Owner hochziehen, der ihn wirklich liest/schreibt |
| Einen einfachen State-Holder für ein Boolean extrahieren | Einfachen privaten UI-State lokal halten |
| Repository-Aufrufe oder Produktregeln in einen Compose-State-Holder legen | Diese Logik in einen Screen-State-Holder wie `ViewModel` oder Component verschieben |
| Text oder Auswahl lokal halten, obwohl sie repository-gestützten Screen-State treiben | Diese Eingabe zum Screen-State-Holder mit der Business-Logik verschieben |
| Einen State-Holder tief in unbeteiligte Kinder reichen | Schlichte Werte und Callbacks reichen, außer das Kind koordiniert wirklich das Verhalten des Holders |
| Den Holder als Abladeplatz für einen ganzen Screen behandeln | Nach kohäsivem UI-Verhalten aufteilen, z. B. Sucheingabe, Sheet-Koordination oder Listensteuerung |
| Animations-suspend-Funktionen aus `viewModelScope` aufrufen | Composition-scoped Coroutine nutzen |

## Verwandt

- [`compose-state-authoring`](../compose-state-authoring/SKILL.md) — korrektes lokales `remember` und Authoring von Mutable State.
- [`compose-state-holder-ui-split`](../compose-state-holder-ui-split/SKILL.md) — Screen-State-Holder-Verdrahtung vom schlichten, state-getriebenen UI-Rendering trennen.
- [`compose-side-effects`](../compose-side-effects/SKILL.md) — Effect-APIs und composition-scoped Coroutine-Grenzen wählen.
- [`compose-focus-navigation`](../compose-focus-navigation/SKILL.md) — Fokus-State, Requester und Tastatur-/D-Pad-Verhalten.
