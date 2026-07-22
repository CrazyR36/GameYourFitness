---
name: kotlin-multiplatform-expect-actual
description: "Nutze diesen Skill beim Entwurf von Kotlin-Multiplatform-expect/actual- oder Interface-Grenzen für Plattform-Services, native SDKs, Source Sets, Compose-Multiplatform-UI, Berechtigungen, Dateien, Einstellungen, Sensoren oder Plattform-Interop."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Kotlin Multiplatform: expect/actual-Grenzen

## Grundprinzip

Halte gemeinsame APIs semantisch und stabil. Lege Plattform-Mechanik hinter kleine `expect`/`actual`-Deklarationen oder Interfaces und halte Android-/iOS-/Desktop-Details aus `commonMain` heraus.

## Vorgehen für Grenzen

1. Benenne die Produktfähigkeit in gemeinsamen Begriffen: Text teilen, Zwischenablage lesen, haptisches Feedback anfordern, aktuelle Region ermitteln.
2. Prüfe, ob gemeinsame Aufrufer Fakes, injizierte Abhängigkeiten, Lifecycle-Ownership oder eine Laufzeit-Implementierungswahl brauchen.
3. Wähle die kleinste Grenze aus der Tabelle unten.
4. Halte die gemeinsame Signatur frei von Plattformtypen und Plattform-Vokabular.
5. Lege Business-Verzweigungen in gemeinsamen Code; halte actuals/Plattform-Bindings als Übersetzungsschichten.
6. Validiere, indem du jedes betroffene Source Set kompilierst und gemeinsamen Code wo möglich mit einem Fake testest.

## Wähle die Grenze

| Situation | Bevorzuge |
|---|---|
| Einfache Compile-Zeit-Plattformspezialisierung | `expect`/`actual`-Funktion, -Wert, -typealias oder Leaf-Composable |
| Implementierung braucht injizierte Abhängigkeiten, Lifecycle-Ownership, Laufzeitwahl oder Test-Fakes | Gemeinsames Interface plus Plattform-Binding |
| UI ist größtenteils geteilt, ein Leaf unterscheidet sich | Gemeinsames Composable, das ein `expect`-Leaf aufruft |
| Ganzer Screen unterscheidet sich je Plattform | Getrennte Plattform-Screens hinter einem gemeinsamen Navigations-Contract |
| Nur Konstanten/Ressourcen unterscheiden sich | Gemeinsame API mit semantischen Werten, actual-Werte je Plattform |

## Halte gemeinsame APIs semantisch

Schreibe gemeinsame APIs so, dass Aufrufer Absicht beschreiben, nicht Plattform-Mechanik:

```kotlin
// GUT: gemeinsame API ist semantisch
expect fun currentRegion(): Region
```

```kotlin
// SCHLECHT: gemeinsame API leakt Android-Implementierung
expect fun currentRegionFromAndroidLocale(context: Context): Region
```

Das Android-actual darf `Locale`-APIs nutzen. Das iOS-actual darf Foundation-APIs nutzen. Gemeinsame Aufrufer sollen es nicht wissen.

## Halte actuals dünn

Actual-Implementierungen sollen die semantische API in Plattformaufrufe übersetzen. Braucht die Operation eine Activity, einen View Controller, einen Lifecycle Owner, DI oder Fakes, halte an und nutze ein von Plattformcode geliefertes Interface statt einer `expect class`:

```kotlin
// commonMain
interface ShareSheet {
    suspend fun shareText(text: String)
}
```

```kotlin
// androidMain
class AndroidShareSheet(
    private val activity: Activity,
) : ShareSheet {
    override suspend fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        activity.startActivity(Intent.createChooser(intent, null))
    }
}
```

Die Android-Implementierung ist ausdrücklich Activity-gebunden. Ein generischer `Context` verbirgt oft die UI-Lifecycle-Anforderung. Definiere, was `suspend` bedeutet: für viele Plattform-UI-Aktionen heißt es „das Sheet wurde gestartet", nicht „der Nutzer hat das Teilen abgeschlossen".

Beginnt das actual, Business-Regeln anzusammeln, verschiebe diese Regeln zurück in gemeinsamen Code und lasse im actual nur die Plattformübersetzung.

## Bevorzuge Interfaces, wenn Tests oder DI zählen

Nutze `expect/actual` für einfache Compile-Zeit-Plattform-APIs. Nutze Interfaces, wenn gemeinsamer Code Fakes, mehrere Implementierungen, Laufzeitauswahl oder Lifecycle-Ownership braucht:

```kotlin
interface Clipboard {
    suspend fun setText(text: String)
}
```

Plattformmodule binden `Clipboard` an Android-/iOS-Implementierungen. Gemeinsame Tests nutzen ein Fake.

## Compose-spezifische Hinweise

Wenn geteilte UI ein Plattform-Leaf erreicht:

1. Halte plattformspezifische Composables an Leaf-Knoten.
2. Reiche `Modifier` durch jedes erwartete Composable, das UI emittiert.
3. Lehne Plattformtypen in `commonMain`-Signaturen ab (`Context`, `Activity`, Android-Ressourcen-IDs, `Uri`, `Bundle`, `UIViewController`, `NSBundle`, Plattform-Permission-Enums usw.).
4. Verbirg den nativen View-Lifecycle im Plattform-actual und nutze den richtigen Interop-Container (`AndroidView`, `UIKitView` usw.).
5. Starte Plattformarbeit nicht direkt aus einem Composable-Body. Nutze `remember`, `LaunchedEffect`, `DisposableEffect` und stabile Keys in actual-Composables — genau wie im gemeinsamen Compose-Code.
6. Preview/teste das gemeinsame, schlichte UI-Composable wo möglich mit Fake-Plattform-Services.

## Häufige Fehler

| Fehler | Fix |
|---|---|
| `commonMain`-API legt Android-/iOS-Typen offen | Durch semantische gemeinsame Typen ersetzen |
| `expect`-Funktion hat Parameter für nur eine Plattform | Diese Details ins actual verschieben |
| Business-Verzweigung in jedem actual dupliziert | Business-Regeln in gemeinsamen Code verschieben |
| Ein riesiges `Platform`-expect-Objekt | Nach Fähigkeit aufteilen: `Clipboard`, `ShareSheet`, `Haptics` |
| Plattform-UI leakt hoch im Baum | Plattformspezifisches Composable an ein Leaf schieben |
| Keine fakebare Grenze für gemeinsame Tests | Interface statt direktem `expect`-Aufruf nutzen |
| Nur ein Target kompiliert nach der Änderung | Vor dem Abschluss alle betroffenen Source Sets kompilieren |

## Warnzeichen im Review

- Gemeinsamer Code importiert Plattformpakete.
- Eine actual-Implementierung kennt Produkt-State, Navigationsentscheidungen oder Domänenregeln.
- Ein Plattform-API-Name taucht in einem gemeinsamen Funktionsnamen auf.
- Eine dritte Plattform hinzuzufügen würde Änderungen an gemeinsamen Aufrufern erfordern.
- Tests brauchen Android-/iOS-Laufzeit nur, um gemeinsames Business-Verhalten zu verifizieren.

## Verwandt (Compose / geteilte UI)

Bleib in diesem Skill auf Plattformgrenzen fokussiert; verdrahte geteilte UI wie jedes andere Compose-Target:

- [`kotlin-control-flow`](../kotlin-control-flow/SKILL.md) — Business-Verzweigung im gemeinsamen Code mit `when`, Guard-Bedingungen, Exhaustiveness und Smart Casts explizit halten.
- [`compose-state-holder-ui-split`](../compose-state-holder-ui-split/SKILL.md) — geteilte schlichte UI-Composables vs. State-Holder-Verdrahtung.
- [`compose-side-effects`](../compose-side-effects/SKILL.md) — Effect-Keys und Cleanup in actual-Composables (`LaunchedEffect`, `DisposableEffect` usw.).
- [`compose-modifier-and-layout-style`](../compose-modifier-and-layout-style/SKILL.md) und [`compose-slot-api-pattern`](../compose-slot-api-pattern/SKILL.md) — wiederverwendbare geteilte Compose-APIs (Modifier, Slots).
