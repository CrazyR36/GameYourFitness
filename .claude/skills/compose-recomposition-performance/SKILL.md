---
name: compose-recomposition-performance
description: "Nutze diesen Skill bei der Untersuchung von Jetpack-Compose-Recomposition-Performance — skippable/restartable Composables, composables.txt oder Compiler-Reports, Recomposition-Zähler im Layout Inspector, phasenübergreifendes Zurückschreiben von Snapshot-State oder das Lesen von frame-rate-State in der Composition- statt der Layout-/Draw-Phase — und noch unklar ist, ob die Ursache Parameter-Stabilität, Deferred Reads oder phasenübergreifendes Back-Writing ist."
---
<!-- Deutsche Übersetzung (2026-07-22) von chrisbanes/skills@2026.7.21 (Apache-2.0). Diese Datei wurde geändert: Prosa/Kommentare übersetzt, Code- und API-Bezeichner unverändert. -->

# Compose-Recomposition-Performance

Nur Router — die eigentlichen Fixes liegen in den fokussierten Skills unten.

## Drei Achsen

1. **Parameter-Stabilität / Skipping** — kann Compose dieses restartable Composable überspringen; sind die Argumente stabil und vergleichbar?
2. **Wo `State` gelesen wird** — wird frame-rate-`State` während der Composition statt in Layout/Draw gelesen?
3. **Back-Writing über Phasen hinweg** — schreibt eine spätere Phase Snapshot-State, der eine frühere Phase invalidiert? Beispiele: Map-/List-Mutation während der Composition, die dieselbe Composition erneut invalidiert; `onSizeChanged` (Layout-Phase) schreibt State, den ein Geschwister-Composable in der Composition liest.

Achsen 2 und 3 überschneiden sich oft (ein Geschwister-Composable, das die gemessene Größe in der Composition liest, ist zugleich ein Deferred-Read-Verstoß und ein Layout-→-Composition-Back-Write). Achse 1 ist unabhängig.

## Von hier weiter → fokussierter Skill

| Hauptverdacht | Nächster Skill |
|---|---|
| Skipping, instabile Parameter, Compiler-/`composables.txt`-Churn | [`compose-stability-diagnostics`](../compose-stability-diagnostics/SKILL.md) |
| Lesephase von frame-rate-`State` (Composition vs. Layout/Draw) | [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md) |
| `putAll` / Map-Neuaufbau / phasenübergreifendes `height(state)` während der Composition | [`compose-state-deferred-reads`](../compose-state-deferred-reads/SKILL.md) — § Back-Writing |
| Fokusgetriebene Nebenarbeit im Composable-Body | [`compose-side-effects`](../compose-side-effects/SKILL.md) — `snapshotFlow` |
| Hinweise auf mehrere Achsen | passende Skills parallel anwenden |

## Prüfreihenfolge

1. Reproduziere einen Übergang (Fokuswechsel, Einfügen, Scroll) und notiere, welche Composables neu komponieren.
2. Wenn die Zähler bei unveränderten Lazy-Items hochschnellen, prüfe Back-Writing (Composition-Mutationen und phasenübergreifende Messung), bevor du die Stabilität verantwortlich machst.
3. Wenn die Zähler bei Scroll/Animation in jedem Frame steigen, prüfe Deferred Reads.
4. Wenn Skipping trotz stabiler Daten fehlschlägt, prüfe Parameter-Stabilität und Compiler-Reports.
5. Miss nach jedem Fix erneut.

## Falsche Fährten

Diese Änderungen senken den Recomposition-Zähler oft **nicht**:

| Versuch | Warum es scheitert |
|---|---|
| `remember(index) { isFirstRow(index) }` statt inline `when (index)` | Gleiche Eingaben; kein Skipping-Vorteil |
| Identitäts-Cache für read-only abgeleitete Maps | Kann veraltete Overlays liefern; `remember(keys)` genügt |
| `mutableIntStateOf` + Layout-Modifier auf **beiden**, gemessener und Geschwister-Zeile | Geschwister liest die Größe weiterhin in der Composition, außer sie ist measure-only |
| `Exactly(1)` auf beiden Zeilen in Fokuswechsel-Tests erzwingen | Eine Zeile komponiert oft korrekterweise 0-mal neu |
| Hoisting ohne Stabilisieren der Lambda-Captures | Neue Lambda-Instanz pro Frame vereitelt das Skipping weiterhin |

## Wann NICHT anwenden

- Die Recomposition folgt echten Datenänderungen, oder der Bug ist Korrektheit, nicht Kosten.
- Kein Profiler-/Compiler-Signal deutet auf ein Problem hin.

## Verwandt

- [`compose-state-authoring`](../compose-state-authoring/SKILL.md) — `mutableState*` sicher schreiben.
