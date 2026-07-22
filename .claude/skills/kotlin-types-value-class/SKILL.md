---
name: kotlin-types-value-class
description: "Nutze diesen Skill beim Schreiben oder Review von Kotlin-Typdeklarationen, um @JvmInline value class gegenüber data class dort zu wählen, wo es passt — inklusive der Auswirkungen auf die Compose-Stabilität."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Kotlin: value class vs. data class

## Grundprinzip

Bevorzuge `@JvmInline value class` für einfeldrige Typen mit Domänenbedeutung. Data-Klassen dienen der Aggregation mehrerer Felder.

## Review-Vorgehen

1. Finde einfeldrige Wrapper, primitivlastige APIs und `@Immutable`-Wrapper im UI-State.
2. Entscheide, ob der einzelne Wert eine echte Domänen-Unterscheidung ist. Wenn nicht, behalte das Primitive oder nutze einen typealias.
3. Prüfe, ob der Typ-Ersatz Gleichheit, Serialisierung, Java-Interop oder Boxing auf dem Hot Path ändert.
4. Konvertiere nur, wenn die Domänenbedeutung klar und die Vertragsänderungen akzeptabel sind.
5. Führe die betroffenen Compiler/Tests erneut aus; bei Compose-Performance-Arbeit Compiler-Reports oder Recomposition-Evidenz erneut prüfen.

## Entscheidungsfluss

| Situation | Bevorzuge |
|---|---|
| Einzelfeld + domänenrelevant (`UserId`, `EmailAddress`, `Percentage`) | `@JvmInline value class` |
| Einzelfeld + keine Domänenbedeutung (nur Gruppierung) | Typealias oder Primitive behalten |
| Mehrere Felder | Data class |
| Braucht eigenes `equals`/`hashCode` über den gewrappten Wert hinaus | Data class (value classes delegieren an den zugrunde liegenden Typ) |
| Als generisches Typargument oder nullable auf nachgewiesenem Hot Path | Data class oder Primitive |

```kotlin
// GUT: domänenrelevantes Einzelfeld
@JvmInline value class UserId(val value: String)
@JvmInline value class EmailAddress(val value: String)
@JvmInline value class Percentage(val value: Float)

// SCHLECHT: data class, die ein einzelnes Domänenfeld wrappt
data class UserId(val value: String)

// SCHLECHT: value class ohne Domänenbedeutung
@JvmInline value class Wrapper(val value: String) // nimm einfach den String oder einen typealias

// SCHLECHT: value class, die eigene Gleichheit braucht
@JvmInline value class CaseInsensitiveString(val value: String)
// value-class-equals delegiert an String-equals, das case-sensitive IST
// Nutze eine data class, wenn du andere Gleichheitssemantik brauchst
```

## Vorgehen bei Compose-Stabilität

Wenn ein Compose-Report auf einen einfeldrigen Wrapper zeigt:

1. Bestätige, dass der zugrunde liegende Typ stabil ist (`String`, Primitive oder ein anderer stabiler Typ).
2. Bevorzuge eine value class gegenüber `@Immutable` auf einem Wrapper, dessen einzige Aufgabe die Typ-Unterscheidung ist.
3. Ändere keine öffentlichen Serialisierungs-/API-Verträge nur, um einen Report stummzuschalten.

```kotlin
// Vorher: primitiver Wert kann mit anderen Strings verwechselt werden
data class UiState(val userId: String)

// Nachher: Domänentyp ist an der Compose-Grenze stabil
@JvmInline value class UserId(val value: String)
data class UiState(val userId: UserId)
```

## Refactoring-Prüfungen

Bevor du einen bestehenden Wrapper ersetzt, prüfe den Vertrag, den Aufrufer beobachten:

| Prüfung | Aktion |
|---|---|
| JSON-/API-Format ist relevant | Serialisierung verifizieren. `@Serializable data class A(val value: String)` kodiert als Objekt; eine value class kodiert als der gewrappte Wert. |
| Eigene Gleichheit oder Hashing nötig | Data class behalten. Value-class-Gleichheit folgt dem gewrappten Wert. |
| Aufrufer nutzen `copy()` oder Destrukturierung | Data class behalten oder Aufrufer bewusst anpassen. Value classes bieten keine Data-class-Annehmlichkeiten. |
| Java- oder reflection-lastige Framework-Grenze | Interop verifizieren. Java-Aufrufer sehen den zugrunde liegenden Typ; generische/`Any`-Nutzung boxt. |
| Nullable/generischer/vararg Hot Path | Vor der Konvertierung messen; diese Nutzungen boxen. |
| Konstruktor-Body, `lateinit`, delegierte Properties, Backing Fields | Data class behalten oder neu entwerfen; value classes speichern nur den Konstruktorwert. |

## Mehrere Werte packen — erst nach Evidenz

Ersetze eine klare mehrfeldrige data class nicht durch Bit-Packing, außer Profiling zeigt Allokationskosten auf einem Hot Path. Falls nötig, bietet Compose `packFloats`, `packInts` und passende `unpack*`-Funktionen in `androidx.compose.ui.util`:

```kotlin
@JvmInline value class Offset(val packedValue: Long)

fun Offset(x: Float, y: Float): Offset = Offset(packFloats(x, y))
val Offset.x: Float get() = unpackFloat1(packedValue)
val Offset.y: Float get() = unpackFloat2(packedValue)
```

## Häufige Fehler

| Fehler | Fix |
|---|---|
| Data class, die ein einzelnes Domänenfeld wrappt | Durch `@JvmInline value class` ersetzen |
| Value class ohne Domänenbedeutung (nur Wrapper) | Typealias oder Primitive direkt nutzen |
| Value class, die eigene Gleichheit braucht | Stattdessen eine data class nutzen |
| Value class als generisches Typargument auf einem Hot Path | Boxing-Kosten messen; Primitive/data class behalten, wenn relevant |
| `@Immutable`-Annotation auf einem Typ, der eine value class sein könnte | Durch value class ersetzen, wenn der zugrunde liegende Typ stabil ist |
| Vergessene `@JvmInline`-Annotation | `value class` bei Einzelfeld immer mit `@JvmInline` paaren |

## Warnzeichen im Review

- Eine data class mit genau einer Property
- Ein `String`, `Long` oder `Int` dort, wo verschiedene Werte nicht austauschbar sein sollten (z. B. `fun transfer(from: String, to: String, amount: Long)`)
- Eine `@Immutable`-Annotation auf einem einfeldrigen Wrapper
- Ein typealias zur Domänen-Unterscheidung, wo value-class-Semantik nötig ist (Typealiases sind typ-erased, kein Laufzeitschutz)

## Wann NICHT anwenden

- Der Typ braucht mehrere Felder → data class
- Der Typ braucht eigenes `equals`/`hashCode` → data class
- Der Typ wird stark als nullable oder generisch in performancekritischem Code genutzt → zuerst Autoboxing-Kosten messen
- Der Ersatz würde JSON-, Java-, Reflection- oder Framework-Verhalten still ändern

## Verwandt

- [`compose-stability-diagnostics`](../compose-stability-diagnostics/SKILL.md) — instabile Compose-Parameter diagnostizieren; value classes sind ein Fix
