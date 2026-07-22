---
name: compose-focus-navigation
description: "Nutze diesen Skill beim Schreiben oder Review von Jetpack-Compose-UI für TV, Tastatur, Desktop, Accessibility-Fokus, D-Pad-Navigation, FocusRequester, focusProperties, Key-Events oder initiales Fokusverhalten."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Compose: Fokus-Navigation

## Grundprinzip

Fokus ist zustandsbehaftetes UI-Verhalten. Mache Fokusziele explizit, fordere Fokus erst nach erfolgreicher Composition an und teste Navigation mit demselben Eingabemodell, das Nutzer verwenden: Tastatur, D-Pad oder Fernbedienungstasten.

## Wann diesen Skill nutzen

Nutze ihn, wenn UI:

- auf TV, Desktop, ChromeOS, tastaturzentriertem Android oder Fernbedienungsgeräten läuft.
- `FocusRequester`, `focusRequester`, `focusProperties`, `onFocusChanged` oder Key-Handler nutzt.
- initialen Fokus, wiederhergestellten Fokus, gerichtete Navigation oder Back-/Escape-Verhalten braucht.
- ein Carousel, Grid, eine Lazy List, ein Menü, einen Dialog oder ein Modal mit Fokus-Traps hat.
- Tests hat, die prüfen, welches Item fokussiert ist.

## Fokusziele bewusst aufbauen

Beginne mit Komponenten, die schon am Fokus teilnehmen, und füge nur die Fokus-Hooks hinzu, die das Verhalten braucht:

| Bedarf | Hinzufügen |
|---|---|
| Normaler Button-/Textfeld-/Clickable-Fokus | Nichts extra; die fokussierbare Komponente nutzen |
| Programmatischer initialer/wiederhergestellter Fokus | `FocusRequester` + `Modifier.focusRequester(...)` |
| Visuelle oder State-Reaktion auf Fokusänderungen | `Modifier.onFocusChanged { ... }` |
| Eigene interaktive Fläche, die noch nicht fokussierbar ist | `Modifier.focusable()` plus Role/Semantics nach Bedarf |

Fordere und beobachte Fokus zum Beispiel nur, wenn beide Verhalten nötig sind:

```kotlin
val requester = remember { FocusRequester() }

Button(
    onClick = onClick,
    modifier = Modifier
        .focusRequester(requester)
        .onFocusChanged { state -> isFocused = state.isFocused },
) {
    Text("Play")
}
```

Bevorzuge fokussierbare Komponenten (`Button`, `TextField`, clickable/selectable Flächen) gegenüber manuell hinzugefügtem `focusable()` auf passivem Layout. Füge manuellen Fokus nur hinzu, wenn das Element wirklich interaktiv ist oder an der Navigation teilnimmt.

## Fokus nach der Composition anfordern

Rufe Fokus-Anforderungen aus einem Effect auf, nicht aus dem Composable-Body:

```kotlin
val initialFocus = remember { FocusRequester() }

LaunchedEffect(initialFocus) {
    initialFocus.requestFocus()
}
```

Erscheint das Ziel nach dem Laden, keye die Anforderung auf die Bedingung:

```kotlin
LaunchedEffect(items.isNotEmpty()) {
    if (items.isNotEmpty()) {
        firstItemRequester.requestFocus()
    }
}
```

Fordere bei Lazy-Content Fokus erst an, nachdem das Item tatsächlich komponiert ist. Halte Requester in stabilem Item-State, gekeyt per Item-Id — nicht allein per Index, wenn die Liste umsortieren kann.

## Gerichtete Navigation

Nutze `focusProperties`, wenn die Standard-Raumsuche falsch ist:

```kotlin
Modifier.focusProperties {
    up = headerRequester
    down = firstRowRequester
    left = FocusRequester.Cancel
}
```

Nutze das sparsam. Zu viele hartkodierte Verknüpfungen erzeugen veraltete Fokusgraphen, wenn sich Layouts ändern. Bevorzuge die natürliche Fokusreihenfolge, außer das Design verlangt einen bestimmten Sprung oder Trap.

## Key-Events

Nutze Key-Handler für Verhalten, das keine normale Click-/Fokus-Traversierung ist:

```kotlin
Modifier.onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
        onBack()
        true
    } else {
        false
    }
}
```

Gib nur `true` zurück, wenn konsumiert. Zu breites `true` bricht Texteingabe, Accessibility-Shortcuts und Eltern-Navigation.

Drossle bei schneller D-Pad-Eingabe an der Grenze, die das teure Verhalten besitzt (z. B. Zeilen-Scroll oder Paging), nicht global über den ganzen Screen.

## Fokus-Wiederherstellung

Erhalte Fokus über semantische Identität:

- Verfolge die ausgewählte/fokussierte Item-Id, nicht nur den Index.
- Nutze stabile `key`-Werte in Lazy Lists und Grids.
- Wenn Content neu lädt, fordere Fokus für dieselbe Id erneut an, falls sie noch existiert.
- Existiert sie nicht mehr, wähle einen deterministischen Fallback: nächster Nachbar, erstes Item oder Eltern-Container.

## Häufige Fehler

| Fehler | Fix |
|---|---|
| `focusRequester` und `onFocusChanged` an jeden Button hängen | Nur hinzufügen, wenn Fokus angefordert oder beobachtet wird |
| `requestFocus()` im Composable-Body | In `LaunchedEffect` verschieben |
| Initialer Fokus auf `Unit` gekeyt, während das Ziel später erscheint | Auf geladene/sichtbare Bedingung keyen |
| Fokus-Requester per Lazy-List-Index gespeichert | Per stabiler Item-Id speichern |
| Alles bekommt eigene `focusProperties` | Raumsuche arbeiten lassen; nur kaputte Kanten überschreiben |
| Key-Handler gibt für alle Keys `true` zurück | Nur behandelte Keys konsumieren |
| Tests klicken Knoten in TV-/D-Pad-UI an | Key-Eingabe senden und Fokus prüfen |

## Testen

Teste Fokus über Nutzereingabe:

```kotlin
composeTestRule.onNodeWithTag("screen").performKeyInput {
    pressKey(Key.DirectionDown)
}

composeTestRule.onNodeWithTag("play-button").assertIsFocused()
```

Bevorzuge das Prüfen fokussierter Semantics gegenüber visuellem Styling. Nutze Screenshot-Tests nur für das Fokus-Aussehen, nicht für deterministische Fokus-Ownership.

Breitere Test-Form-Entscheidungen (schlichte UI vs. Integration, semantics-first): [`compose-ui-testing-patterns`](../compose-ui-testing-patterns/SKILL.md).

## Warnzeichen im Review

- „Es fokussiert korrekt, wenn ich es antippe" bei einer Tastatur-/TV-UI.
- Initialer Fokus funktioniert nur mit festen Daten und scheitert nach Laden/Refresh.
- Fokus-State wird aus dem Auswahl-Daten-State abgeleitet, obwohl Fokus und Auswahl verschiedene Konzepte sind.
- Der Fokusgraph ist in Kommentaren beschrieben, aber nicht kodiert oder getestet.
