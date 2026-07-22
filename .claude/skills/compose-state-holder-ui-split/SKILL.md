---
name: compose-state-holder-ui-split
description: "Nutze diesen Skill, wenn ein Jetpack-Compose-Composable auf Screen-Ebene ein ViewModel/Component/Controller entgegennimmt, State oder Effects sammelt, Navigation/Snackbars behandelt oder Callbacks verdrahtet und dabei zugleich Layout rendert."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Compose: State-Holder-/UI-Trennung

## Grundprinzip

Trenne State-Holder-Verdrahtung vom UI-Rendering. Das State-Holder-Composable spricht mit ViewModels, Components, Flows, Navigation und Side Effects. Das UI-Composable nimmt schlichten, unveränderlichen UI-State plus Callbacks und beschreibt Layout.

Das hält Screens previewbar, testbar und leichter über Android-, Desktop-, TV- und KMP/CMP-Targets wiederverwendbar.

## Wann diesen Skill nutzen

Nutze ihn, wenn ein Compose-Screen:

- ein ViewModel, Component, Controller, Navigator, Repository oder Service direkt entgegennimmt.
- App-/Business-State oder Side Effects in derselben Funktion sammelt, die den Großteil der UI layoutet.
- einen ganzen State-Holder in Kind-Composables reicht statt expliziten State und Callbacks.
- schwer previewbar ist, weil er Dependency Injection, Navigation, Lifecycle oder Fake-Services braucht.
- UI-Tests hat, die einen vollen App-Stack aufbauen müssen, um einen einfachen Layout-Zweig zu prüfen.

## Das Muster

Nutze ein kleines, öffentliches State-Holder-Composable:

```kotlin
@Composable
fun ProfileScreen(component: ProfileComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsStateWithLifecycle()

    ProfileScreen(
        state = state,
        onNameChange = component::onNameChange,
        onSaveClick = component::save,
        onBackClick = component::back,
        modifier = modifier,
    )
}
```

Lege die UI dann in ein schlichtes Composable, das nichts vom State-Holder weiß:

```kotlin
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onNameChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileContent(
        name = state.name,
        isSaving = state.isSaving,
        canSave = state.canSave,
        onNameChange = onNameChange,
        onSaveClick = onSaveClick,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}
```

Private Content-Funktionen können das Layout aufteilen:

```kotlin
@Composable
private fun ProfileContent(
    name: String,
    isSaving: Boolean,
    canSave: Boolean,
    onNameChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nur Layout.
}
```

## Faustregeln

| Belang | State-Holder-Composable | UI-Composable |
|---|---|---|
| ViewModel-/Component-State sammeln | Ja | Nein |
| Einmalige Effects sammeln | Ja, oder ein winziger Geschwister-Effect-Handler | Meist nein |
| Dependency-injizierte Objekte halten | Ja | Nein |
| Unveränderlichen UI-State entgegennehmen | Reicht ihn meist durch | Ja |
| Lambdas für Nutzer-Events entgegennehmen | Verdrahtet sie | Ruft sie auf |
| Layout, Modifier, Semantics, Test-Tags besitzen | Nein/minimal | Ja |
| UI-lokalen State wie Scroll, Fokus, Texteingabe, Animation, Interaktion besitzen | Sät ihn manchmal | Ja |
| Preview-/Screenshot-freundlich | Nicht unbedingt | Ja |

Die Regel „keine Collection in UI-Composables" betrifft App-/Business-State und Side-Effect-Streams. Schlichte UI-Composables dürfen weiterhin UI-lokalen Framework-State besitzen: `rememberScrollState`, `rememberLazyListState`, `FocusRequester`, Fokus-State, Animations-State, `TextFieldState`, `MutableInteractionSource.collectIsPressedAsState()` und ähnliches Verhalten, das zum gerenderten Widget gehört.

Wächst dieser UI-lokale State zu koordiniertem Verhalten mit mehreren zusammengehörigen Feldern und Operationen, nutze [`compose-state-hoisting`](../compose-state-hoisting/SKILL.md), um zu entscheiden, ob daraus eine einfache, in der Composition gerememberte State-Holder-Klasse werden soll.

## Was übergeben

Übergib den kleinsten nützlichen UI-Contract:

- Bevorzuge ein dediziertes `UiState`/`State`-Objekt gegenüber vielen unverbundenen Primitiven, wenn der Screen echten State hat.
- Bevorzuge explizite Lambdas (`onRetryClick`, `onItemSelected`) gegenüber der Übergabe eines ganzen Components.
- Halte Domänenmodelle aus dem UI-Composable, wenn sie Business-Regeln in die UI zwingen. Mappe auf UI-Modelle, wenn die UI eine andere Form braucht.
- Halte Navigation als Callbacks. Das UI-Composable sagt „Nutzer hat Zurück geklickt", nicht „navigiere zu Route X".
- Frame-rate- oder UI-lokale Werte, die bei Änderung keine Recomposition des ganzen Baums erzwingen sollen: bevorzuge Provider-Lambdas und Deferred Reads gemäß [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md).

## Side Effects

[`compose-side-effects`](../compose-side-effects/SKILL.md) deckt Effect-APIs (`LaunchedEffect`, `DisposableEffect`, `SideEffect`), Keys, Cleanup und `rememberUpdatedState` ab.

Behandle Effects nahe am State-Holder, wo Effect-Quelle und imperatives Ziel beide verfügbar sind:

```kotlin
@Composable
fun ProfileScreen(component: ProfileComponent, snackbarHostState: SnackbarHostState) {
    val state by component.state.collectAsStateWithLifecycle()

    LaunchedEffect(component) {
        component.effects.collect { effect ->
            when (effect) {
                ProfileEffect.Saved -> snackbarHostState.showSnackbar("Saved")
            }
        }
    }

    ProfileScreen(state = state, onSaveClick = component::save)
}
```

Wächst die Effect-Behandlung, extrahiere `ProfileEffects(component, snackbarHostState)`, statt das Component ins UI-Composable zu schieben.

## Häufige Fehler

| Fehler | Warum es schadet | Fix |
|---|---|---|
| `fun Screen(viewModel: MyViewModel)` enthält das ganze Layout | Schwer preview-/testbar ohne Android-Lifecycle und DI | Ein schlichtes UI-Overload hinzufügen, das `state` und Callbacks nimmt |
| Kind-Composables nehmen `component` | Abhängigkeiten leaken durch den Baum | Nur den State/die Callbacks reichen, die das Kind braucht |
| UI-Composable startet Navigation | UI wird an das App-Routing gekoppelt | `onBackClick`, `onItemClick` usw. exponieren |
| UI-Composable sammelt App-/Business-Flows | Collection-Lifecycle versteckt sich im Layout | Nahe am State-Holder sammeln und Werte nach unten reichen |
| UI-lokaler State wird ohne Grund in den State-Holder hochgezogen | State-Holder beginnt, Layout-Mechanik zu besitzen | Scroll-/Fokus-/Animations-/Textfeld-Interaktions-State im UI-Composable halten, wenn es nur UI-Verhalten ist |
| Jedes winzige Composable bekommt ein State-Holder-Overload | Zu viel Zeremonie | An Screen-/Section-Grenzen aufteilen, nicht an jeder `Row` |

## Wann NICHT anwenden

- Winzige Einweg-Composables, die schon schlichte Werte und Callbacks nehmen.
- Design-System-Primitive wie `Button`, `Card` oder `ListItem`; die sollen Slots und Modifier exponieren, keine State-Holder.
- Fälle, in denen das State-Holder-Composable nur ein Primitive weiterreichen und keine Isolation bringen würde.

## Verwandt

- [`compose-ui-testing-patterns`](../compose-ui-testing-patterns/SKILL.md) — schlichte, state-getriebene UI-Composables ohne den vollen App-Graphen testen.
- [`compose-state-hoisting`](../compose-state-hoisting/SKILL.md) — entscheiden, wo UI-Element-State und UI-Logik leben sollen, inklusive einfacher State-Holder-Klassen.
- [`kotlin-multiplatform-expect-actual`](../kotlin-multiplatform-expect-actual/SKILL.md) — Plattform-Services, native Views und expect/Interface-Grenzen, wenn geteilte UI auf plattformspezifische Leaves trifft.
