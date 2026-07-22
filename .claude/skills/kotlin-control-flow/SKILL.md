---
name: kotlin-control-flow
description: "Nutze diesen Skill beim Schreiben oder Review von Kotlin-Verzweigung und Control-Flow: when-Expressions, Guard-Bedingungen, Exhaustiveness sealed Typen, Smart Casts, nullable Verzweigung, Early Returns oder das Ersetzen komplexer if/else-Ketten."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Kotlin Control Flow

## Zweck

Nutze diesen Skill, um die Form von Kotlin-Verzweigungscode zu schreiben oder zu reviewen. Behandle ihn als Refactoring-Vorgehen, nicht als Stilpräferenz.

Der Zielzustand ist einfach: Der klassifizierte Wert ist offensichtlich, branch-lokale Prädikate bleiben bei ihrem Branch, Smart Casts bleiben nutzbar, und der Compiler beweist Exhaustiveness für geschlossene Domänen.

## Vorgehen

Wende diese Prüfungen der Reihe nach an.

### 1. Benenne das Subjekt

Finde den Wert, den der Code klassifiziert. Stellt jeder Branch eine Frage über denselben Wert, mache diesen Wert zum `when`-Subjekt.

```kotlin
// Wiederholte Checks gegen `state` durch ein Subjekt-`when` ersetzen.
val action = when (state) {
    State.SignedOut -> Action.ShowSignIn
    is State.SignedIn -> Action.ShowHome(state.user)
}
```

Gibt es kein einzelnes Subjekt, behalte ein subjektloses `when` oder eine `if`-Kette.

### 2. Wähle das Branch-Primitive

Nutze diese Entscheidungstabelle vor dem Editieren:

| Wenn der Code … hat | Nutze … |
|---|---|
| Einen Wert, der klassifiziert wird | `when (subject)` |
| Unverbundene boolesche Bedingungen | Subjektloses `when` oder `if`/`else` |
| Einen Primär-Match plus ein extra branch-lokales Prädikat | Guard-Bedingung |
| Ungültige Eingabe vor dem Hauptpfad | Early Return, `require` oder `check` |
| Ein geschlossenes Enum, Boolean, sealed Typ oder nullable geschlossener Typ, der einen Wert zurückgibt | Exhaustives `when`-Expression |
| Offene externe Eingabe oder einen echten Fallback | Explizites `else` |

### 3. Branch-lokale Prädikate in Guard-Bedingungen verschieben

Wenn ein Branch zuerst einen Typ/Wert matcht und dann ein extra Prädikat prüft, nutze eine Guard-Bedingung:

```kotlin
return when (event) {
    is Event.Message if event.isUnread -> Row.Highlighted(event.message)
    is Event.Message -> Row.Normal(event.message)
    Event.Empty -> Row.Empty
}
```

Wende Guards nur an, wenn all das gilt:

- Das `when` hat ein Subjekt.
- Der Branch hat eine Primärbedingung (`is Type`, Enum-Eintrag, Objekt, Wert, Range usw.).
- Die extra Bedingung gehört nur zu diesem Branch.
- Ein späterer Branch behandelt weiterhin dieselbe Primärbedingung, oder das Expression bleibt anders exhaustiv.

Setze geguardete Branches vor ihren ungeguardeten Fallback für dieselbe Primärbedingung.

### 4. Exhaustiveness erhalten

Für ein `when`-Expression über eine geschlossene Domäne behandle jeden Fall explizit. Füge `else` nicht nur hinzu, um den Compiler ruhigzustellen.

```kotlin
val action = when (state) {
    SessionState.SignedOut -> Action.ShowSignIn
    is SessionState.SignedIn -> Action.ShowHome(state.user)
    is SessionState.Expired if state.canRefresh -> Action.Refresh
    is SessionState.Expired -> Action.ShowSignIn
}
```

Nutze `else`, wenn die Domäne offen ist: Strings vom Server, ganzzahlige Statuscodes, unbekannte Plattformwerte oder ein bewusster Fallback-/Logging-Pfad.

### 5. Nicht unterstützte geguardete Branches aufteilen

Guard-Bedingungen gelten nicht für komma-getrennte Branch-Bedingungen. Braucht nur ein Fall ein extra Prädikat, teile den Branch:

```kotlin
when (status) {
    Status.Pending if canRetry -> retry()
    Status.Pending -> showPending()
    Status.Queued -> showQueued()
}
```

### 6. Ungültige Preconditions flach machen

Nutze Early Returns, wenn sie nullable oder ungültigen State aus dem Hauptpfad entfernen:

```kotlin
fun render(user: User?): UiModel {
    user ?: return UiModel.SignedOut

    return UiModel.SignedIn(
        name = user.name,
        avatar = user.avatar,
    )
}
```

Mache nicht flach, wenn die Verschachtelung Cleanup-, Transaktions- oder Fehlerbehandlungs-Struktur trägt.

### 7. Smart Casts prüfen

Verifiziere nach dem Umformen, dass jeder Branch den verengten Typ dort noch verfügbar hat, wo er genutzt wird. Erzwingt das Rewrite `as`, `!!`, temporäre veränderliche vars oder duplizierte Casts, behalte die ursprüngliche Form oder wähle ein kleineres Refactoring.

## Rewrite-Rezepte

### Verschachtelter Branch in `when`

Wenn der verschachtelte Branch nur einen Primärfall verfeinert, wandle ihn in geguardete Branches:

```kotlin
// Vorher
return when (event) {
    is Event.Message -> {
        if (event.isUnread) Row.Highlighted(event.message) else Row.Normal(event.message)
    }
    Event.Empty -> Row.Empty
}

// Nachher
return when (event) {
    is Event.Message if event.isUnread -> Row.Highlighted(event.message)
    is Event.Message -> Row.Normal(event.message)
    Event.Empty -> Row.Empty
}
```

### Wiederholte Checks gegen einen Wert

Wenn jede Bedingung denselben Wert klassifiziert, mache ihn zum Subjekt:

```kotlin
// Vorher
return when {
    result is Result.Success -> Ui.Success(result.value)
    result is Result.Failure && result.canRetry -> Ui.Retry(result.error)
    result is Result.Failure -> Ui.Error(result.error)
    else -> Ui.Loading
}

// Nachher
return when (result) {
    is Result.Success -> Ui.Success(result.value)
    is Result.Failure if result.canRetry -> Ui.Retry(result.error)
    is Result.Failure -> Ui.Error(result.error)
    Result.Loading -> Ui.Loading
}
```

### Null als ein Fall unter mehreren

Nutze `when (value)`, wenn null ein Branch in einer größeren Klassifikation ist:

```kotlin
return when (val selected = selection) {
    null -> SelectionUi.None
    is Selection.Single if selected.item.isArchived -> SelectionUi.Archived(selected.item)
    is Selection.Single -> SelectionUi.Active(selected.item)
    is Selection.Multiple -> SelectionUi.Count(selected.items.size)
}
```

## Review-Checkliste

Bevor du eine Control-Flow-Änderung abschließt, verifiziere:

- Der Code hat ein offensichtliches Subjekt oder bewusst keines.
- Geguardete Branches kommen vor dem passenden ungeguardeten Branch.
- Komma-getrennte Branches nutzen keine Guard-Bedingungen.
- `when`-Expressions über geschlossene Domänen bleiben exhaustiv ohne unnötiges `else`.
- Offene-Domänen-Fallbacks sind weiterhin explizit.
- Smart Casts funktionieren weiter ohne `as`, `!!` oder duplizierte Casts.
- Die neue Form ist leichter zu scannen als die alte.

## Wann NICHT anwenden

- Führe keine Guard-Bedingungen ein, wenn die Kotlin-Version des Projekts sie nicht unterstützt.
- Mache keine unverbundenen booleschen Checks zu einem umständlichen Subjekt-`when`.
- Entferne kein bewusstes `else` für Open-World-externe Eingaben.
- Mache Code nicht flach, wenn es Cleanup, Transaktionsgrenzen oder Fehlerbehandlung weniger offensichtlich macht.

## Verwandt

- [`kotlin-flow-state-event-modeling`](../kotlin-flow-state-event-modeling/SKILL.md) — Wahl von Flow-State- und Event-Primitiven.
- [`kotlin-multiplatform-expect-actual`](../kotlin-multiplatform-expect-actual/SKILL.md) — Business-Verzweigung im gemeinsamen Code halten und Plattform-actuals dünn.
