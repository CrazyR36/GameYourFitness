---
name: compose-ui-testing-patterns
description: "Nutze diesen Skill beim Schreiben oder Review von Jetpack-Compose-UI-Tests, Screenshot-Tests, Previews, Semantics-Assertions, Fake-Image-Loading, Tastatureingabe, Fokus-Assertions, Interaktions-State (hover/pressed/focused) oder Tests für schlichte, state-getriebene UI-Composables."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Compose: UI-Test-Muster

## Grundprinzip

Teste den kleinsten UI-Contract, der das Verhalten beweist. Bevorzuge schlichte, state-getriebene UI-Tests mit Callbacks. Füge Integration nur hinzu, wenn Lifecycle, Navigation, DI oder Plattformverhalten das Getestete ist.

## Wahl des Test-Targets

| Was du beweisen musst | Testform |
|---|---|
| Text, Button, Lade-/Fehlerzweig, bedingter Content | Schlichter UI-Compose-Test |
| Callback-Verdrahtung aus Klick/Eingabe | Schlichter UI-Compose-Test |
| Fokus-Navigation oder Tastaturverhalten | Compose-Test mit Key-Eingabe |
| Visuelles Layout, Clipping, Elevation, Typografie, Bildkomposition | Screenshot-Test |
| State-Holder aktualisiert UI korrekt | State-Holder-/Unit-Test plus ein Verdrahtungs-Smoke-Test |
| Hover-, Pressed-, Focused-, Dragged-Interaktions-State | Schlichter UI-Test mit MutableInteractionSource |
| Navigation, Lifecycle, DI-Integration | Integrationstest |

## Bevorzuge schlichte UI-Tests

Hat der Screen eine State-Holder-/UI-Trennung, teste das schlichte UI-Composable:

```kotlin
composeTestRule.setContent {
    ProfileScreen(
        state = ProfileUiState(name = "Ada", canSave = true),
        onNameChange = {},
        onSaveClick = { saved = true },
        onBackClick = {},
    )
}

composeTestRule.onNodeWithText("Ada").assertIsDisplayed()
composeTestRule.onNodeWithText("Save").performClick()

assertThat(saved).isTrue()
```

Das vermeidet den Aufbau von ViewModels, Components, Repositories, Navigation und Dependency-Graphen für Layout-Verhalten.

## Semantics zuerst

Prüfe Semantics, wenn Verhalten semantisch ist:

- Text existiert: `onNodeWithText`.
- Button ist enabled/disabled: `assertIsEnabled`, `assertIsNotEnabled`.
- Content ist selektiert/fokussiert/getoggelt: Semantics-Assertions nutzen.
- Content fehlt: `assertDoesNotExist`.

Nutze Test-Tags für Knoten ohne stabilen, für Nutzer sichtbaren Text oder wo mehrere Knoten sich Text teilen. Nutze Tags nicht als erste Wahl für alle Assertions; für Nutzer sichtbare Semantics sind meist stärker.

## Callback-Tests

Nutze einfache Zähler oder erfasste Werte:

```kotlin
var selectedId: String? = null

composeTestRule.setContent {
    ItemList(
        items = listOf(ItemUi("movie-1", "Movie")),
        onItemClick = { selectedId = it },
    )
}

composeTestRule.onNodeWithText("Movie").performClick()

assertThat(selectedId).isEqualTo("movie-1")
```

Für schlicht erfasste Callback-Werte genügt meist eine direkte Assertion nach der Aktion. Nutze `runOnIdle`, wenn die Assertion braucht, dass Compose das Anwenden von Snapshot-State, Recomposition oder eingereihte UI-Arbeit abschließt, bevor das Ergebnis gelesen wird.

## Interaktions-State mit MutableInteractionSource

Wenn Aussehen oder Verhalten eines Composables vom Interaktions-State abhängt (hover, focus, press, drag), injiziere eine `MutableInteractionSource` und emittiere den gewünschten State direkt. Versuche nicht, Pointer-/Maus-Events zu simulieren, um Interaktions-States auszulösen — das ist fragil, umgebungsabhängig und erzeugt flaky Tests.

```kotlin
val interactionSource = MutableInteractionSource()

composeTestRule.setContent {
    OutlinedButton(
        onClick = {},
        interactionSource = interactionSource,
    )
}

// Default-Zustand (nicht gehovert) prüfen
composeTestRule.onNodeWithText("OutlinedButton").assertIsDisplayed()

// Hover emittieren — interactionSource.emit ist eine suspend-Funktion,
// also aus einem Test-Coroutine-Scope aufrufen.
TestScope().launch {
    interactionSource.emit(HoverInteraction.Enter())
}

composeTestRule.waitForIdle()

// Die visuelle/semantische Änderung prüfen, die Hover erzeugt
// (z. B. Rahmenfarbe, Elevation oder Capture für Screenshot-Test)
composeTestRule.onNodeWithText("OutlinedButton").assertIsDisplayed()
```

Dasselbe Muster funktioniert für `PressInteraction.Press` / `Release` / `Cancel`, `FocusInteraction.Focus` / `Unfocus` und `DragInteraction.Start` / `Stop` / `Cancel`. Emittiere die Eintritts-Interaktion, `waitForIdle`, dann prüfe das Ergebnis.

Kernpunkte:

- **Injiziere immer `MutableInteractionSource`**, statt dich auf die interne Default-Source zu verlassen. Das gibt dir volle Kontrolle über State-Übergänge.
- **Emittiere Interaktionen aus einem Coroutine-Scope** (z. B. `TestScope().launch { }`), da `emit` eine suspend-Funktion ist. Nutze kein `LaunchedEffect` — das ist ein Produktions-Compose-Effect, kein Testwerkzeug.
- **Prüfe das *Ergebnis* der Interaktion** (visuelle Änderung, semantische Änderung, enabled-State), nicht die Interaktion selbst. Die Interaction Source ist ein Test-*Treiber*, kein Assertion-Ziel.
- **Nutze das auch für Screenshot-Tests** — emittiere den Interaktions-State, dann nimm den Screenshot für ein deterministisches Hover-/Press-/Focus-Bild auf.

## Tastatur und Fokus

Für Tastatur-, TV- und Desktop-UI treibe Navigation mit demselben Eingabemodell, das Nutzer verwenden (Keys/D-Pad), nicht mit Klicks allein. Prüfe fokussierte Semantics, nicht Farben oder Skalierung; reserviere Screenshots für die visuelle Fokus-Behandlung.

Details — Fokusgraph, `FocusRequester`, Wiederherstellung, Key-Handler und Testmuster: [`compose-focus-navigation`](../compose-focus-navigation/SKILL.md).

## Screenshot-Tests

Nutze Screenshots für visuelle Contracts, die Semantics nicht beweisen können:

- Layout-Abstände/-Ausrichtung.
- Themed Farben, Typografie, Elevation, Schatten.
- Bildkomposition, Farbverläufe, Overlays.
- Aussehen des Fokus-Highlights.
- Lade-Skelette oder dichte visuelle Zustände.

Halte den Screenshot-State deterministisch:

- Nutze feste State-Daten.
- Friere Clocks oder Animationsfortschritt wo möglich ein.
- Ersetze Netzwerk-/Image-Loading durch Fake- oder Preview-Handler.
- Vermeide das Prüfen dynamischen Texts wie der aktuellen Uhrzeit, außer kontrolliert.

## Fake-Bilder und Plattform-Services

Wenn der Bildinhalt irrelevant ist, fake den Loader und prüfe das angeforderte Model, falls das das Verhalten ist. Der genaue Hook hängt von deiner Image-Bibliothek ab; ein Projekt-Helper könnte so aussehen:

```kotlin
val requestedModels = mutableListOf<Any?>()

// Beispiel-Helper, keine Compose-API.
setContentWithFakeImageLoader { request ->
    requestedModels += request.data
    errorPainter()
}
```

Wenn das Bild-Aussehen zählt, liefere einen deterministischen lokalen Painter/Bitmap statt Netzwerkdaten.

## Häufige Fehler

| Fehler | Fix |
|---|---|
| Vollen App-Graphen aufbauen, um eine Fehler-Zeile zu testen | Schlichte UI mit `state = Error` testen |
| Klick-Verhalten über einen ViewModel-Mock testen | Callback übergeben und prüfen, dass er aufgerufen wurde |
| Screenshot-Test für einfache Textpräsenz | Semantics-Assertion nutzen |
| Semantics-Test für Padding/Farbe/Fokus-Ring | Screenshot-Test nutzen |
| Überall Test-Tags | Text/Content-Description/Role bevorzugen, wenn stabil |
| UI-Test hängt an echtem Image-Loading/Netzwerk/Zeit | Quelle faken oder einfrieren |
| Hover/Press/Fokus mit Maus- oder Touch-Events simulieren | `MutableInteractionSource` injizieren und die Interaktion emittieren |
| Sich auf die Default-`InteractionSource` in Tests verlassen | `MutableInteractionSource` übergeben, um State zu kontrollieren |
| TV-/Tastatur-UI nur mit `performClick` getestet | Key-Eingabe und Fokus-Assertions nutzen; siehe [compose-focus-navigation](../compose-focus-navigation/SKILL.md) |

## Warnzeichen im Review

- „Dieser UI-Test ist flaky, weil Bilder langsam laden."
- Ein Test nutzt Produktions-DI für einfaches Rendering.
- Ein Screenshot hat zufällige Daten, Clocks, Remote-Bilder oder Live-Daten.
- Assertions prüfen nur, dass ein Knoten nach einer Aktion existiert, nicht dass die Callback-/State-Änderung passiert ist.
- Fokusverhalten wird visuell inspiziert, aber nicht per Assertion geprüft.
- Ein Test nutzt `performMouseInput` oder Touch-Injection, um Hover-/Press-States auszulösen, statt `MutableInteractionSource.emit`.
- Ein Composable nimmt `interactionSource`, aber Tests injizieren keine `MutableInteractionSource`.
