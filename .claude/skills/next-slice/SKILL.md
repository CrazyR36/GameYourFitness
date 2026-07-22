---
name: next-slice
description: >-
  Verbindlicher Arbeitsablauf für einen Feature-Slice in GameYourFitness — von der
  Orientierung über Test-First bis zum Abschluss mit Retrospektive. Nutze diesen Skill
  immer, wenn ein neuer Slice/Feature begonnen oder ein laufender fortgesetzt werden
  soll: bei „implementiere das nächste Feature", „nächster Slice", „mach weiter",
  „was ist als Nächstes dran", „arbeite am nächsten Issue", oder nach Abschluss eines
  Issues. Stellt sicher, dass Slice-Reihenfolge, Test-First-Disziplin, die Pflicht-
  Statuskommentare am Issue und die Definition of Done eingehalten werden — auch wenn
  der Nutzer nur knapp „weiter" sagt.
---

# next-slice — ein Feature-Slice von Anfang bis Ende

Dieser Skill operationalisiert CLAUDE.md Abschnitt 0. Er ist der Runbook für „baue das
nächste Feature". Halte die fünf Phasen der Reihe nach ein. **Ein Slice pro Zeit** — erst
wenn ein Issue geschlossen ist, beginnt das nächste (CLAUDE.md Abschnitt 1).

> **Warum so streng:** Weil Tests und Code gemeinsam committet werden, ist der
> Issue-Verlauf der **einzige** Nachweis für Test-First. Fehlt er, gilt der Slice als nicht
> sauber gebaut. Die Disziplin ersetzt hier die fehlende zeitliche Trennung im Git-Verlauf.

## Werkzeuge je Umgebung — zuerst lesen

CLAUDE.md nennt `gh …`-Kommandos. Die gelten für **lokale CLI-Sessions**. In
**Web-/Remote-Sessions gibt es kein `gh` CLI** — nutze dort die GitHub-MCP-Tools. Der
Ablauf ist identisch, nur das Werkzeug wechselt:

| Zweck | lokal (CLI) | Web/Remote (MCP) |
|---|---|---|
| Issues auflisten | `gh issue list --label slice` | `list_issues` (state, labels) |
| Issue lesen | `gh issue view <nr>` | `issue_read` |
| Kommentieren | `gh issue comment` | `add_issue_comment` |
| PR öffnen | `gh pr create` | `create_pull_request` |

Wenn ein MCP-GitHub-Tool nicht geladen ist: über `ToolSearch` nachladen (`+github issues`).

---

## Phase 1 — Orientieren (immer, ohne Rückfrage)

1. Offene `slice`-Issues holen (Status offen) — das ist der Backlog.
2. Die letzten geschlossenen `slice`-Issues ansehen und die **Retrospektiven-Kommentare**
   der letzten zwei lesen (CLAUDE.md Abschnitt 10) — dort steht, was zuletzt gelernt wurde
   und ob der Plan angepasst ist.
3. `docs/DECISIONS.md` lesen — getroffene Architekturentscheidungen und ihre Gründe. Nicht
   gegen eine dort begründete Entscheidung arbeiten, ohne sie bewusst zu revidieren.
4. `git log --oneline -15`, aktuellen Branch und Arbeitsverzeichnis prüfen. Liegt
   unfertige Arbeit herum?

> **Subagenten hier (optional):** Für breite Recherche/Exploration dürfen read-only
> Subagenten (Explore) **parallel** laufen — Orchestrator-Worker-Muster, isolierter Kontext
> (vgl. Anthropic „Building Effective Agents"). **Nicht** zum parallelen Bauen: Slices sind
> sequenziell (ein Slice pro Zeit), Phase 4 bleibt einspurig.

## Phase 2 — Nächsten Slice wählen

Nimm das oberste offene `slice`-Issue. Eine andere Wahl ist erlaubt (Abhängigkeit, neue
Erkenntnis aus der letzten Retrospektive) — dann nenne dem Nutzer kurz Wahl **und**
Begründung. Ein Issue ist zu groß? Teile es (Abschnitt 3), kommentiere die Teilung.

## Phase 3 — Issue schärfen (vor jedem Code)

Die Issues sind bewusst grob. Vor dem Bauen konkretisieren:

- Akzeptanzkriterien präzisieren (beobachtbares Verhalten, keine Technik).
- E2E-Testfälle mit **echten** Testnamen ausformulieren (die `test_…`-Namen stehen meist
  schon im Issue — halte dich daran oder begründe eine Abweichung).
- Abgrenzung schärfen: „Nicht Teil dieses Slices".
- Die Schärfung **am Issue kommentieren** (`add_issue_comment`).

## Phase 4 — Bauen (Test First, nicht verhandelbar)

Strikte Reihenfolge im Arbeitsverzeichnis (CLAUDE.md Abschnitt 2):

1. **E2E-Test schreiben** (Compose UI Test) — nur über sichtbare UI (Text,
   `contentDescription`, `testTag`), nie ViewModels direkt. Er ist **rot**.
2. **Unit-Tests** für die Domänenlogik des Slices — ebenfalls **rot**.
3. **Implementieren**, bis alles grün ist. Nur so viel Code wie nötig — keine Vorratsarbeit.
4. **Refactoring** bei grünen Tests.
5. **Screenshot-Test** (Light + Dark) für neue/geänderte Screens — siehe Skill
   `design-system`.

**Regeln, die hier gelten:**

- Kein Produktionscode, bevor ein fehlschlagender Test existiert. Ertappst du dich dabei:
  abbrechen, Test nachziehen, neu starten.
- Test niemals abschwächen, überspringen oder löschen, um grün zu werden (Abschnitt 9).
- Branch: `feature/<issue-nr>-<kurzbeschreibung>` (z. B. `feature/3-charakterbildschirm`).
  Kein Direktcommit auf `main`.
- **Tests und Implementierung immer im selben Commit.** Committed wird der grüne Zustand;
  rot→grün passiert im Arbeitsverzeichnis. Conventional Commits (`feat:` enthält Funktion
  **und** Tests).
- Bei neuen Tabellen/Spalten: **RLS-Policy** + Negativtest (Fremdzugriff verboten). Beachte
  die Default-Grants-Stolperfalle in `docs/DECISIONS.md` (2026-07-21) — `revoke` explizit.
- Serverseitige Spiellogik (EP, Level, Rang) als Postgres-Funktion, nie nur im Client
  (Abschnitt 6/8). Balancing-Konstanten zentral in `domain/progression/`.

### Pflicht-Statuskommentare am Issue (zum jeweiligen Zeitpunkt, nicht rückwirkend)

Kommentiere am Issue bei **jedem** dieser Ereignisse (`add_issue_comment`):

- Beginn der Arbeit (mit geplantem Vorgehen)
- **E2E-Tests geschrieben (rot)** — Testnamen auflisten
- Implementierung begonnen
- **Tests grün** — Ergebnis nennen
- Jede Blockade, offene Frage oder Designentscheidung
- Abschluss (siehe Phase 5)

Halte zusätzlich den `## Status`-Abschnitt der Issue-Beschreibung aktuell.

### Lokale Kommandos (README)

```bash
./gradlew ktlintCheck detekt        # statische Analyse — muss sauber sein
./gradlew testDebugUnitTest         # Unit-Tests (JUnit 5 + Robolectric)
./gradlew verifyRoborazziDebug      # Screenshot-Tests gegen Goldens
./gradlew recordRoborazziDebug      # Goldens neu aufnehmen (nur bei gewollter UI-Änderung)
./gradlew connectedDebugAndroidTest # E2E auf laufendem Emulator/Gerät
# Backend für E2E:
cp backend/.env.example backend/.env
docker compose -f backend/docker-compose.yml up -d
backend/scripts/migrate.sh && backend/scripts/test-rls.sh
```

E2E braucht einen laufenden Emulator/ein Gerät (`adb devices`) — in Web-Sessions meist nur
über CI verifizierbar. Ist der Emulator lokal nicht verfügbar, sag es offen und stütze dich
auf die CI (`.github/workflows/ci.yml`, Job `e2e`).

## Phase 5 — Abschließen

Prüfe die **Definition of Done** (CLAUDE.md Abschnitt 2) Punkt für Punkt:

- [ ] ≥ 1 E2E-Test deckt den Happy Path vollständig ab
- [ ] ≥ 1 E2E-Test deckt einen relevanten Fehlerfall ab
- [ ] Unit-Tests für die Domänenlogik (Regeln/Formeln lückenlos)
- [ ] Screenshot-Test Light + Dark für neue/geänderte Screens
- [ ] Bei neuen Tabellen/Spalten: RLS-Policy + Test, dass Fremdzugriff scheitert
- [ ] Alle Tests grün, Build ohne Warnungen, `ktlint`/`detekt` sauber
- [ ] Manuell auf Emulator geprüft und im Issue dokumentiert (bzw. CI-Nachweis)
- [ ] Issue-Beschreibung mit finalem Status aktualisiert
- [ ] PR mit `Closes #<nr>` (Screenshots/Aufnahme der neuen UI)

Dann **Retrospektive** als Issue-Kommentar (CLAUDE.md Abschnitt 10): Was war umständlich?
Was hat der Slice über die Domäne verraten? Muss der Plan angepasst werden? Muss
`docs/DECISIONS.md` (Warum) oder `CLAUDE.md` (was künftig immer gilt) ergänzt werden? Was
würdest du rückblickend anders machen?

Zum Schluss: **anhalten und dem Nutzer berichten**. Beginne nie eigenmächtig den nächsten
Slice (CLAUDE.md Abschnitt 0/9).

## Gotchas (so geht es hier meist schief)

- **`gh` ≠ Web-Session:** In Web-/Remote-Sessions kein `gh` CLI → GitHub-MCP-Tools (Tabelle
  oben). Häufigste Stolperfalle beim Orientieren.
- **RLS-Default-Grants-Falle:** Eine neue Tabelle „ohne Policy" ist **nicht** dicht — das
  Supabase-Image vergibt `anon`/`authenticated` breite Rechte. Erst
  `revoke all … from anon, authenticated`, dann gezielt grants. Sonst liefert ein Fremd-
  DELETE HTTP **204** statt **403** (`docs/DECISIONS.md`, 2026-07-21). Der RLS-Negativtest
  prüft die HTTP-Codes.
- **Ein Commit = grün:** Tests + Implementierung zusammen; nie ein roter Commit. Rot→grün
  passiert im Arbeitsverzeichnis.
- **Statuskommentare zum Zeitpunkt setzen** („rot"/„grün"), nicht rückwirkend am Ende —
  sonst fehlt der einzige Test-First-Nachweis.
- **Screenshot-Goldens** für neue Screens nicht vergessen (Details im `design-system`-Skill).

## Wann den Nutzer fragen statt selbst entscheiden

Selbst entscheiden: Umsetzungsweg im Slice, Reihenfolge zweier gleichrangiger Issues,
Zuschnitt/Teilung, Benennung, Teststruktur. **Vorher fragen** bei: Technologiewechsel,
neuer Abhängigkeit mit großem Fußabdruck, Streichen eines Features, Änderung an den
Spielregeln (EP-Formel, Rang-Schwellen, Stat-Zuordnung), allem, was Nutzerdaten
löscht/migriert, und allem, was Kosten verursacht.
