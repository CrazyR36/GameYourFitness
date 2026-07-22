---
name: kotlin-functions
description: "Nutze diesen Skill bei der Wahl zwischen Kotlin-Member-, Top-Level-, Extension-, Factory- oder Service-Funktionen für Receiver wie String, Primitive, Collections, Flow, Framework- oder Drittanbieter-Typen."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Ownership von Kotlin-Funktionen

## Grundprinzip

Lege eine Funktion auf den kleinsten zutreffenden semantischen Owner. Extension-Syntax ändert die Aufrufform, nicht die Ownership.

Lehne Extensions auf Primitiven, Allerwelts- und bibliothekseigenen Typen standardmäßig ab: Sie erzeugen falsche Ownership, Domänen-Verschmutzung, lautes Completion/Imports und Kollisionen.

## Vorgehen

In dieser Reihenfolge anwenden.

### 1. Benenne den semantischen Owner

Benenne die Operation und das Konzept, dem sie gehört. Ist die Ownership unklar, halte an, bevor du die Syntax wählst.

### 2. Verwirf einen irreführenden Receiver früh

Für `String`, Primitive, Collections, `Flow`, Framework- oder Drittanbieter-Receiver müssen **alle** gelten:

- Enger, kohäsiver `private`/`internal`-Scope.
- Gültig für jeden Receiver-Wert.
- Keine Policy, kein State, kein I/O, keine Abhängigkeit.
- Wesentlich klarere Receiver-Syntax.
- Kein besserer projekteigener Owner.

Jeder Fehlschlag verbietet eine Extension auf diesem Receiver; wähle in Schritt 3 eine Nicht-Extension-Form. `private fun <T> MutableList<T>.swap(...)` kann bestehen: list-nativ, policy-frei und algorithmus-lokal.

### 3. Wähle die Funktionsform

| Bedeutung | Bevorzuge |
|---|---|
| Projekteigenes, intrinsisches Verhalten | Member |
| Typübergreifende, zustandslose Operation | Top-Level-Funktion |
| Konstruktion oder Parsing | Ziel-Factory oder benannte Top-Level-Funktion |
| Gehaltene Policy, State, I/O, Clock, Locale oder Abhängigkeiten | Injizierter Service/Kollaborator |
| Typ-native Operation mit klarerem Receiver und allen bestandenen Gates aus Schritt 2 | Extension |

Nutze einen Service/Kollaborator nur, wenn das Verhalten Policy, State, I/O, Clock/Locale oder Abhängigkeiten hält; sonst nimm explizite Parameter auf einer zustandslosen Funktion.

### 4. Verschiebe Verhalten und Aufrufer

Verschiebe die Implementierung, aktualisiere dann Aufrufe, Imports und Funktionsreferenzen. Erhalte oder deprecate öffentliche Einstiegspunkte, außer dies ist bewusst ein Breaking Release; füge nicht-öffentliche Migrationshilfen nur für konkrete Konsumenten hinzu.

```kotlin
// Vorher: String besitzt fälschlich die UserId-Konstruktion.
fun String.toUserId(): UserId = UserId(this)

// Nachher: UserId besitzt die Konstruktion.
@JvmInline
value class UserId private constructor(val value: String) {
    companion object {
        fun parse(raw: String): UserId = UserId(raw)
    }
}

val id = UserId.parse(raw)
```

### 5. Prüfe und schließe ab

Prüfe bei jeder Form Sichtbarkeit, Imports, Kollisionen und Kompatibilität. Bei Extensions zusätzlich nullable Receiver, Generics und den Vorrang künftiger Member. Kompiliere und teste; bei Fehlschlag die API verengen oder zurück zu Schritt 1.

## Rechtfertigungen

| „Aber…" | Konter |
|---|---|
| Flüssige Syntax | Lesbarkeit schafft keine Ownership. |
| Kotlin nutzt Extensions | Idiom verlangt trotzdem korrekte Semantik. |
| Es ist private/internal | Scope hilft nur, wenn jedes Gate besteht. |
| Utility-Objekte sind schlechter | Nimm eine Top-Level-Funktion oder Ziel-Factory. |
| Die Default-Policy ist offensichtlich | Zeitzonen-/Locale-Defaults sind Policy; halte sie explizit. |
| Steht schon im PR | Bestehender Code beweist keine Ownership. |

## Warnzeichen

- Domänenbedeutung auf `String`, Zahlen, Collections, `Flow` oder Fremdtypen.
- Clock, Locale, I/O, Policy oder Abhängigkeiten in einer Extension versteckt.

## Häufige Fehler

| Fehler | Fix |
|---|---|
| `Long.toDisplayDate()` | Ein Formatter besitzt die Zeitzonen-/Locale-Policy. |
| Extension versteckt Parsing | Nutze `Type.parse(raw)` oder einen benannten Parser. |
| Öffentliche Extension auf Bibliothekstyp | Klassifiziere sie mit den Schritten 1–3 neu. |

## Verwandt

- [`kotlin-types-value-class`](../kotlin-types-value-class/SKILL.md)
