---
name: compose-slot-api-pattern
description: "Nutze diesen Skill beim Entwurf oder Review einer wiederverwendbaren Jetpack-Compose-Komponente, deren visuelle Bereiche je Aufrufer variieren, oder wenn sich primitive Content-Parameter und boolesche Shape-Flags häufen."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Compose: Slot-API-Muster

## Grundprinzip

Eine wiederverwendbare Compose-Komponente beschreibt die Layout-Struktur. Aufrufer liefern variablen visuellen Content über Slots.

## API-Review-Vorgehen

1. Bestätige, dass die Komponente wiederverwendbar ist. Für ein echtes Einweg-Composable keine Slot-Zeremonie hinzufügen.
2. Markiere, welche Bereiche je Aufrufer variieren: Headline, Supporting Text, Leading Visual, Trailing Visual, Actions, Body.
3. Ersetze aufrufer-kontrollierten primitiven Content und Shape-Flags durch Slots.
4. Füge Receiver-Scopes nur hinzu, wenn der Slot innerhalb eines Layouts emittiert wird, dessen Scope-APIs Aufrufer nutzen sollen.
5. Mache abwesende optionale Bereiche nullable (`null`), damit die Komponente ihre Container und Abstände weglassen kann.
6. Lege wiederholten Default-Content oder Tokens in `XxxDefaults`.
7. Kombiniere das mit den `modifier`-Regeln in `compose-modifier-and-layout-style`.

## 1. Primitiven Content durch `@Composable`-Slots ersetzen

Wo die Komponente nach aufrufer-kontrolliertem *Content* fragt, bevorzuge einen `@Composable () -> Unit`-Slot. Wo der Slot strukturell erforderlich ist, lass ihn non-nullable ohne Default. Wo er optional ist, mach ihn nullable mit `null`-Default.

```kotlin
// ❌ SCHLECHT — primitive Parameter; der Trailing-Bereich ist der einzige Slot; alles andere ist festgelegt
@Composable
fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) { … }
```

```kotlin
// ✅ GUT — jeder visuelle Bereich ist ein Slot; die Row beschreibt Struktur, nicht Content
@Composable
fun SettingsRow(
    headlineContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) { … }
```

Aufrufstellen bleiben kurz, wenn der typische Content ein Einzeiler ist:

```kotlin
SettingsRow(
    headlineContent = { Text("Account") },
    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
    trailingContent = { SettingsRowDefaults.Chevron() },
    onClick = { … },
)
```

Die ungewöhnlichen Fälle brauchen keine neuen Komponenten-Parameter mehr:

```kotlin
SettingsRow(
    headlineContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Inbox")
            Spacer(Modifier.width(8.dp))
            Badge { Text("3") }
        }
    },
    onClick = { … },
)
```

### Slot-Benennung

- Nutze `xxxContent` für freie `@Composable () -> Unit`-Slots (`headlineContent`, `supportingContent`, `trailingContent`) — passt zu Material 3.
- Nutze ein Substantiv im Singular (`title`, `icon`, `actions`), wenn der Slot semantisch eingeschränkt ist und der Komponentenname disambiguiert (`Scaffold(topBar = { … }, bottomBar = { … }, floatingActionButton = { … })`).
- Nutze nicht `content` *und* andere `xxxContent`-Slots zusammen — wähle eine Konvention pro Komponente.

## 2. Scope-Receiver, wenn der Slot in ein Layout emittiert

Sitzt der Content des Slots in einer `Row`/`Column`/`Box`, deren Layout-Features (`Modifier.weight`, `BoxScope.matchParentSize`, Alignment) dem Aufrufer verfügbar sein sollen, deklariere den Slot als Receiver-Lambda: `@Composable RowScope.() -> Unit`.

```kotlin
// ❌ SCHLECHT — actions rendern in einer Row, aber Aufrufer können RowScope.weight() nicht nutzen
@Composable
fun MyTopBar(
    title: @Composable () -> Unit,
    actions: @Composable () -> Unit = {},   // ← Aufrufer hat keinen Row-Scope
)
```

```kotlin
// ✅ GUT — Aufrufer bekommt RowScope; .weight() und alignment-by funktionieren darin
@Composable
fun MyTopBar(
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
)
```

Das macht `TopAppBar(actions = { IconButton(…); IconButton(…) })` möglich — der Aufrufer ist implizit in einem `RowScope`.

Schraube nicht reflexartig einen Scope-Receiver an jeden Slot. Der Receiver soll zum tatsächlichen Eltern-Layout passen, in das der Slot emittiert. Wird der Slot in einer `Box` gerendert, nutze `BoxScope`. In einer `Column`, `ColumnScope`. Ist das Elternteil kein Standard-Layout (oder ist keine seiner Scope-APIs im Slot-Content nützlich), kein Receiver.

## 3. Optionale Slots — nullable mit `null`-Default

Für Slots, die abwesend sein können, bevorzuge `(@Composable () -> Unit)? = null` gegenüber `@Composable () -> Unit = {}`:

```kotlin
// ❌ SCHLECHT — leerer Default; „kein Leading-Content" ist das leere Lambda
leadingContent: @Composable () -> Unit = {}

// ✅ GUT — null heißt „kein Slot"; die Komponente kann bei Abwesenheit Platz/Padding weglassen
leadingContent: (@Composable () -> Unit)? = null
```

Mit einem nullable Slot kann die Komponente auf `leadingContent != null` verzweigen und Container, Abstände und Padding des Slots ganz überspringen. Mit einem leeren Default allokiert das Layout oft trotzdem Platz für abwesenden Content.

## 4. Defaults leben in `XxxDefaults`

Wenn du dabei bist zu dokumentieren „der Trailing-Slot sollte meist ein Chevron sein" oder „übergib `MaterialTheme.colorScheme.surface` als Default-Hintergrund", lege die Helfer in ein `XxxDefaults`-Objekt neben die Komponente:

```kotlin
object SettingsRowDefaults {
    @Composable
    fun Chevron() = Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
    )

    @Composable
    fun TrailingValue(text: String) = Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

Aufrufstellen bleiben für die häufigen Fälle deklarativ und der Slot ist weiterhin voll offen für Einzelfälle:

```kotlin
SettingsRow(
    headlineContent = { Text("Notifications") },
    trailingContent = { SettingsRowDefaults.Chevron() },
    onClick = { … },
)
```

Das passt zu Material 3s `ButtonDefaults`, `TopAppBarDefaults` usw. — Defaults, die selbst composable sind, gehören hierher, nicht als neue Komponenten-Parameter mit inline expandierten `MaterialTheme.x.y`-Defaults.

## Kurzreferenz

| Symptom | Diagnose | Fix |
|---|---|---|
| `title: String, subtitle: String?, leadingIcon: ImageVector?` an einer wiederverwendbaren Komponente | Primitive Content-Parameter (§1) | In `xxxContent: (@Composable () -> Unit)?`-Slots umwandeln |
| Mehrere boolesche Flags (`showChevron`, `showSwitch`), die Trailing-Shapes wählen | Shapes aufzählen (§1) | Ein `trailingContent: (@Composable () -> Unit)?`-Slot |
| Ein `mode: Mode.Sealed`-Parameter, der Varianten auflistet | Wie Flag-Suppe (§1) | Als Slot machen |
| `actions: @Composable () -> Unit = {}` in einem `Row`-Body | Fehlender Scope-Receiver (§2) | `actions: @Composable RowScope.() -> Unit = {}` |
| `slot: @Composable () -> Unit = {}` für einen optionalen Bereich | Leeres-Lambda-Default (§3) | `slot: (@Composable () -> Unit)? = null` und darauf verzweigen |
| Komponenten-Param `defaultColor: Color = MaterialTheme.colorScheme.surface` | Inline-Defaults (§4) | Nach `XxxDefaults.color` verschieben und referenzieren |
| Häufiger Trailing-Content wiederholt sich an jeder Aufrufstelle | Fehlender Default-Helfer (§4) | `XxxDefaults.Chevron()` usw. hinzufügen |

## Wann NICHT anwenden

- **Einweg-Komponenten.** Ein Composable, das an genau einer Stelle genutzt wird, ohne Wiederverwendungsplan, profitiert nicht von Slot-Flexibilität — und die Slot-Indirektion macht den Code für den einen Leser schwerer lesbar. Primitive Params + Inline-Content sind in Ordnung. (Sobald eine zweite Aufrufstelle auftaucht, als Slot machen.)
- **Design-System-Primitive, bei denen jeder Aufrufer identisch aussehen muss.** Ein `Heading2(text: String)` existiert, *weil* du willst, dass jede H2 gleich aussieht; es zu `headlineContent: @Composable () -> Unit` zu machen lädt Aufrufer ein, die Regel zu brechen. Halte es primitiv. (Umgekehrt: Braucht `Heading2` je einen Badge inline, mach es zum Slot.)
- **Semantische Parameter, die die Komponente bewusst besitzt.** Besitzt die Komponente Typografie, Ikonografie, Accessibility-Wording oder Produktkonsistenz, kann ein primitiver Parameter die gewünschte Einschränkung sein.
- **Constrained-Type-Parameter, die wirklich eingeschränkt sind.** Ein `Switch(checked: Boolean, onCheckedChange: ...)` braucht seinen Checked-Indikator nicht als Slot. Booleans-mit-Callbacks sind kein „Content".
- **Performancekritische Fast Paths** (selten in App-Code; häufig in Framework-Primitiven). Ein Slot ist ein allokiertes Lambda. In der tiefsten LazyList-Item-Schicht gewinnen manchmal Primitive. Schreibst du nicht das Framework, gilt das nicht.

## Warnzeichen im Review

| Gedanke | Realität |
|---|---|
| „Der Titel ist *immer* ein String — ihn zum Slot zu machen ist Over-Engineering" | „Immer heute" ist die Falle. Materials `ListItem.headlineContent` existiert, weil morgen jemand ein `Text + Badge` will. Der Slot ist `8` Zeichen extra Wrapping an jeder Aufrufstelle (`{ Text(…) }`); das spätere Refactoring, einen Slot hinzuzufügen, editiert jede bestehende Aufrufstelle. |
| „Lambdas sind schwerer als Strings" | Im Maßstab typischer Compose-UI ist das nicht messbar — und die eigenen Komponenten des Frameworks (`Button`, `ListItem`, `TopAppBar`, `Scaffold`) nutzen alle Slots. Ist deine Komponente im heißesten Hot Path, siehe „Wann NICHT anwenden". |
| „Ich füge später einen Slot hinzu, wenn jemand fragt" | Der Slot macht aus einem Parameter zwei (den Slot selbst + evtl. ein internes Flag) und editiert jede Aufrufstelle. Die Formänderung ist keine „spätere" Änderung. |
| „Ich modelliere die Varianten stattdessen mit einem sealed `Trailing`-Typ" | Sealed-Aufzählung ist bounded; Slots sind unbounded. Ein sealed Typ funktioniert, *bis* jemand eine nicht antizipierte Variante braucht — dann editierst du wieder die Komponente. Der Slot vermeidet den Kreislauf. |
| „Der Leading-Bereich ist *immer* ein Icon, der Trailing variiert — ich mache nur Trailing zum Slot" | Das ist die Partial-Slot-Falle. Die „immer-ein-Icon"-Annahme bricht beim ersten Mal, wenn eine Row einen Avatar oder ein Flaggen-Emoji oder eine farbige Shape braucht. Mach auch Leading zum Slot. |
| „Es gibt heute nur eine Aufrufstelle" | Gibt es nur eine Aufrufstelle, entwirfst du wahrscheinlich noch keine wiederverwendbare Komponente. Siehe „Wann NICHT anwenden" — Primitive sind für ein echtes Einweg in Ordnung. In dem Moment, in dem du es kopierst, mach es zum Slot. |

## Verwandt

- [`compose-modifier-and-layout-style`](../compose-modifier-and-layout-style/SKILL.md) — die Modifier-Parameter-Regel (dort §1–§3) reist mit Slot-APIs. Eine wiederverwendbare Komponente nimmt einen `modifier`-Parameter *und* macht ihren Content zum Slot; der Aufrufer besitzt Platzierung und Inhalt.
