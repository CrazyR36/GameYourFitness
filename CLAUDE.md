# CLAUDE.md — Arbeitsregeln für dieses Repository

Diese Datei ist verbindlich. Sie gilt für **jede** Session, jeden Task und jeden Commit.
Wenn eine Anweisung des Nutzers diesen Regeln widerspricht, weise darauf hin, bevor du sie umsetzt.

---

## 0. Session-Start — lies das zuerst

Diese Datei ist so geschrieben, dass sie **allein genügt**. Du brauchst keinen Kontext aus
früheren Sessions und keine Erklärung von mir. Wenn ich sage „implementiere das nächste Feature",
läufst du dieses Protokoll ab:

**1. Orientieren (immer, ohne Rückfrage)**
- `gh issue list --state open --label slice` — was ist offen?
- `gh issue list --state closed --label slice --limit 5` — was ist zuletzt passiert?
- Lies die Retrospektiven-Kommentare der letzten zwei geschlossenen Issues (Abschnitt 10).
  Dort steht, was zuletzt gelernt wurde und ob der Plan angepasst wurde.
- `docs/DECISIONS.md` — getroffene Architekturentscheidungen und ihre Gründe.
- `git log --oneline -15` und Branch-Status prüfen. Liegt eine unfertige Arbeit herum?

**2. Nächsten Slice wählen**
Nimm das oberste offene Issue mit Label `slice`, **außer** du hast einen guten Grund für eine
andere Wahl (Abhängigkeit, neue Erkenntnis aus der letzten Retrospektive). Dann nennst du mir
kurz deine Wahl und die Begründung.

**3. Issue schärfen**
Das Issue ist absichtlich grob angelegt. Bevor du baust, konkretisierst du es:
Akzeptanzkriterien präzisieren, E2E-Testfälle mit echten Testnamen ausformulieren, Abgrenzung
schärfen. Kommentiere die Schärfung am Issue.

**4. Bauen**
Nach Abschnitt 2 (Test First), Abschnitt 4 (Branch, Statuspflege, PR) und Abschnitt 5 (Architektur).

**5. Abschließen**
Definition of Done prüfen (Abschnitt 2), Retrospektive schreiben (Abschnitt 10), PR mit
`Closes #<nr>`, dann **anhalten und berichten**. Beginne nie eigenmächtig den nächsten Slice.

**Fragen stellst du am Anfang, nicht am Ende.** Wenn nach diesem Protokoll noch etwas unklar ist,
frag — bevor du Code schreibst.

### Wo was steht

| Frage | Quelle |
|---|---|
| Was ist der aktuelle Stand? | GitHub Issues (offen/geschlossen) + Retrospektiven-Kommentare |
| Warum wurde X so gebaut? | `docs/DECISIONS.md` |
| Wie arbeite ich hier? | diese Datei |
| Wie starte ich Tests / das Backend? | `README.md` |
| Wie sieht das Schema aus? | `backend/migrations/` (fortlaufend nummeriert) |
| Was sind die Spielregeln? | `domain/progression/` (EP-Formel, Ränge, Stats — eine Quelle) |

---

## 0.1 Projektkontext

**Produkt:** Gamifizierte Fitness-App im RPG-Stil (Solo-Leveling-inspiriert).
Nutzer sammeln EP durch Training, steigen in Stats (STR/VIT/AGI/PER) und Rängen (E→S) auf,
erfüllen Daily Quests und zeitlich begrenzte Dungeons/Raids.

**Plattform:** Android nativ. Kein Web, kein iOS, kein Cross-Platform-Wrapper.

**Stack (gesetzt, nicht ohne Rückfrage ändern):**

| Bereich | Technologie |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose (Material 3, eigenes Theme) |
| Architektur | MVVM + Clean-Layer (data / domain / ui) |
| DI | Hilt |
| Lokale DB | Room |
| Preferences / Spielzustand | DataStore (Proto oder Preferences) |
| Health-Daten | Health Connect API |
| GPS | FusedLocationProviderClient + Foreground Service |
| Hintergrundjobs | WorkManager |
| Charts | Vico |
| Datenbank | PostgreSQL (self-hosted, via Supabase-Stack) |
| Backend | Supabase self-hosted (GoTrue, PostgREST, Storage, Realtime) |
| Auth | Google SSO via Credential Manager API → `signInWithIdToken` (GoTrue) |
| Autorisierung | Postgres Row Level Security — kein Datenzugriff ohne RLS-Policy |
| Hosting | Ubuntu 24.04 LTS + Docker Compose + Caddy |
| Unit-Tests | JUnit5 + Turbine + MockK |
| UI-/E2E-Tests | Compose UI Test + Espresso, auf echtem Emulator |
| Screenshot-Tests | Paparazzi oder Roborazzi |

**Ausdrücklich NICHT verwenden:** Kubernetes, Keycloak, Firebase, Supabase-Cloud (nur self-hosted),
PocketBase, XML-Layouts, Fragmente/Activities über die eine `MainActivity` hinaus,
Namen/Logos/Charaktere aus „Solo Leveling".

---

## 1. Oberste Regel: Feature-Slices, niemals technische Schichten

> **Ein Issue = eine für den Nutzer sichtbare, vollständig benutzbare Funktion.**

Ein Slice ist erst fertig, wenn er **von der UI bis zur Datenbank durchgängig** funktioniert
und ein Mensch ihn auf einem Gerät benutzen kann.

**Erlaubte Issue-Titel:**
- „Nutzer kann sich mit Google anmelden"
- „Nutzer kann ein Krafttraining erfassen und erhält dafür EP"
- „Nutzer sieht seine Tagesquests und kann sie abschließen"
- „Nutzer steigt beim Erreichen der EP-Schwelle im Level auf und sieht ein Level-Up-Popup"

**Verbotene Issue-Titel:**
- „Datenmodell erstellen"
- „Room-Setup"
- „Repository-Layer implementieren"
- „API-Client bauen"
- „Theme aufsetzen" *(einzige Ausnahme: Issue #1, siehe Abschnitt 3)*

Technische Bausteine (Entities, DAOs, Repositories, DTOs) werden **immer nur so weit gebaut,
wie der aktuelle Slice sie braucht**. Keine Vorratsarbeit, keine „das brauchen wir später eh"-Klassen.
Wenn Slice 4 ein Feld ergänzt, wird das Datenmodell in Slice 4 erweitert — nicht vorher.

**Ein Slice pro Zeit.** Erst wenn ein Issue geschlossen ist, beginnt das nächste.
Niemals zwei Features parallel anfangen.

---

## 2. Test First — nicht verhandelbar

Reihenfolge innerhalb jedes Slices, strikt einzuhalten:

1. **E2E-Test schreiben** (Compose UI Test) — beschreibt, was der Nutzer sieht und tut. Er ist **rot**.
2. **Unit-Tests schreiben** für die Domänenlogik des Slices. Ebenfalls **rot**.
3. **Implementieren**, bis alle Tests grün sind. Nur so viel Code wie nötig.
4. **Refactoring** bei grünen Tests.
5. **Screenshot-Test** für neue oder geänderte Screens ergänzen.

**Es wird nie Produktionscode geschrieben, bevor ein fehlschlagender Test existiert.**
Wenn du dich dabei ertappst, Code ohne Test zu schreiben: abbrechen, Test nachziehen, neu starten.

### E2E-Tests haben Vorrang

E2E-Tests sind der wichtigste Testtyp in diesem Projekt. Sie müssen:

- den **kompletten Weg** abbilden: Klick → ViewModel → Repository → DB/Backend → UI-Aktualisierung
- **nur über sichtbare UI-Elemente** interagieren (Text, ContentDescription, TestTag) — nie ViewModels direkt aufrufen
- **das tatsächliche Aussehen prüfen**: Ist der EP-Balken gefüllt? Erscheint das Level-Up-Popup? Ist der Button nach dem Absenden deaktiviert?
- **Fehlerfälle abdecken**: kein Netz, abgelehnte Berechtigung, ungültige Eingabe, leerer Zustand
- gegen ein **echtes Test-Backend** (Supabase-Stack per Docker/Testcontainers oder Compose-Profil,
  frisch migriert und geseedet) laufen, nicht gegen Fakes — außer für Google-SSO, das wird gemockt

Jedes Composable, das getestet wird, bekommt einen stabilen `Modifier.testTag("...")`.
TestTags sind Teil des Produktionscodes und werden gepflegt wie öffentliche API.

### Definition of Done

Ein Slice gilt nur als fertig, wenn **alle** Punkte erfüllt sind:

- [ ] Mindestens ein E2E-Test deckt den Happy Path vollständig ab
- [ ] Mindestens ein E2E-Test deckt einen relevanten Fehlerfall ab
- [ ] Unit-Tests für die Domänenlogik, sinnvolle Abdeckung (keine Zahlenfetischismus, aber keine Lücken bei Regeln/Formeln)
- [ ] Screenshot-Test für neue/geänderte Screens (Light + Dark)
- [ ] Bei neuen Tabellen/Spalten: RLS-Policy vorhanden **und** durch einen Test belegt, dass
      ein fremder Nutzer die Daten nicht lesen oder schreiben kann
- [ ] Alle Tests grün, Build ohne Warnungen
- [ ] `ktlint`/`detekt` sauber
- [ ] Manuell auf Emulator geprüft und im Issue dokumentiert
- [ ] Issue-Beschreibung mit finalem Status aktualisiert
- [ ] PR mit `Closes #<nr>` gemergt

---

## 3. Slice-Reihenfolge — ein lebender Plan, keine feste Liste

**Diese Reihenfolge ist ein Vorschlag, kein Vertrag.** Sie darf und soll sich ändern, wenn du
beim Bauen etwas lernst. Nach jedem abgeschlossenen Slice prüfst du aktiv:

- Ist die geplante Reihenfolge noch die sinnvollste? Hat sich etwas als Vorbedingung entpuppt?
- Ist ein Issue zu groß und muss geteilt werden? Sind zwei Issues faktisch dasselbe?
- Ist ein geplanter Umsetzungsweg durch das Gelernte überholt?
- Ist ein Issue überflüssig geworden oder ein neues nötig?

Änderungen sind erwünscht — aber **nie stillschweigend**. Du kommentierst am betroffenen Issue,
was du änderst und warum, und meldest es mir in deinem Abschlussbericht. Größere Umplanungen
(Slice streichen, Reihenfolge deutlich umstellen, Technologie tauschen) legst du mir vorher
zur Bestätigung vor.

Auch die **Art der Umsetzung** steht nicht fest. Die Architekturregeln in Abschnitt 5 sind
verbindlich, alles darunter ist deine Entscheidung. Wenn ein in einem Issue skizzierter
Lösungsweg sich beim Bauen als schlechter erweist als eine Alternative: nimm die Alternative
und begründe sie im Issue.

Issue #1 ist die einzige erlaubte technische Ausnahme von der Feature-Slice-Regel,
weil ohne sie kein Test laufen kann:

**#1 — Projektgerüst & Testinfrastruktur**
Leeres Compose-Projekt, Hilt, CI-Pipeline (GitHub Actions), Emulator-Runner, ein trivialer
E2E-Test („App startet und zeigt Startbildschirm"), der grün ist. Damit ist bewiesen, dass die
Testkette funktioniert. Danach gilt Abschnitt 1 ausnahmslos.

Danach, in dieser Reihenfolge:

2. Nutzer kann sich mit Google anmelden und bleibt angemeldet
3. Nutzer sieht seinen Charakterbildschirm mit Level, Rang, EP-Balken und Stats
4. Nutzer kann ein Krafttraining erfassen und erhält dafür EP
5. Nutzer steigt bei Erreichen der EP-Schwelle auf und sieht ein Level-Up-Popup
6. Nutzer sieht seine Tagesquests und kann sie abschließen
7. Nutzer erhält automatisch Schritte aus Health Connect gutgeschrieben
8. Nutzer kann einen Lauf per GPS aufzeichnen
9. Nutzer sieht seine Streak, verliert sie bei verpassten Tagen
10. Nutzer kann einen Rang-Aufstiegstest ablegen
11. Nutzer sieht Verlauf und Statistiken als Diagramme
12. Nutzer kann an zeitlich begrenzten Dungeons/Raids teilnehmen

Der Startpunkt ist gesetzt: Issue #1, dann #2. Ab da entscheidest du nach jedem Slice neu,
was als Nächstes am meisten Sinn ergibt — und begründest es.

---

## 4. GitHub-Workflow

### Issues

Jeder Slice bekommt **vor Arbeitsbeginn** ein Issue nach dieser Vorlage:

```markdown
## Nutzersicht
Als Nutzer möchte ich …, damit …

## Akzeptanzkriterien
- [ ] Beobachtbares Verhalten 1
- [ ] Beobachtbares Verhalten 2
- [ ] Fehlerfall: …

## E2E-Testfälle (vor der Implementierung schreiben)
1. `test_…` — beschreibt Ablauf und Erwartung
2. `test_…` — Fehlerfall

## Nicht Teil dieses Slices
- …

## Status
_(wird laufend aktualisiert)_
```

### Statuspflege — verpflichtend

Du kommentierst am Issue **bei jedem dieser Ereignisse**:

- Beginn der Arbeit (mit geplantem Vorgehen)
- E2E-Tests geschrieben (rot) — Testnamen auflisten
- Implementierung begonnen
- Tests grün — Ergebnis nennen
- Jede Blockade, offene Frage oder getroffene Designentscheidung
- Abschluss mit Zusammenfassung: was gebaut, welche Tests, was bewusst ausgelassen

Weil Tests und Code gemeinsam committed werden, ist der Issue-Verlauf der **einzige Nachweis**
für Test-First. Die Kommentare „Tests geschrieben (rot)" und „Tests grün" sind deshalb Pflicht
und werden zum jeweiligen Zeitpunkt gesetzt — nicht rückwirkend am Ende nachgetragen.

Der Abschnitt `## Status` in der Issue-Beschreibung wird ebenfalls aktuell gehalten.
**Ein Issue ohne Statusverlauf gilt als nicht bearbeitet.** Niemand soll den Code lesen müssen,
um zu verstehen, was passiert ist.

### Branches & Commits

- Branch: `feature/<issue-nr>-<kurzbeschreibung>` (z. B. `feature/2-google-login`)
- **Tests und Implementierung werden immer im selben Commit abgegeben.** Nie ein Commit mit
  Tests ohne den zugehörigen Code, nie Code ohne die zugehörigen Tests. Jeder Commit auf dem
  Branch ist für sich lauffähig und grün.
  Test-First bleibt trotzdem verbindlich (Abschnitt 2) — es betrifft die Reihenfolge, in der
  du **schreibst**, nicht die, in der du committest. Rot → grün passiert im Arbeitsverzeichnis,
  committed wird der grüne Zustand.
- Conventional Commits: `feat:`, `fix:`, `refactor:`, `docs:`, `chore:`. Ein `feat:`-Commit
  enthält die Funktion **und** ihre Tests. `test:` nur für nachträglich ergänzte Tests zu
  bereits bestehendem Code.
- Kein Direktcommit auf `main`
- PR referenziert `Closes #<nr>` und enthält Screenshots/Aufnahme der neuen UI

### CI

GitHub Actions läuft bei jedem Push: Build, ktlint, detekt, Unit-Tests, E2E-Tests auf Emulator,
Screenshot-Tests. **Roter Build wird nie gemergt und nie ignoriert.**

> **detekt prüft auch die Testquellen.** Die Compose-Ausnahmen (`LongMethod`/`LongParameterList`
> via `ignoreAnnotated: Composable` in `config/detekt/detekt.yml`) greifen nur für
> `@Composable`-annotierte Funktionen. Test-Hilfsfunktionen mit vielen Parametern (z. B.
> `capture(...)` in Screenshot-Tests) müssen selbst schlank bleiben — Content als
> `@Composable`-Lambda übergeben, statt jeder Variante einen eigenen Parameter zu geben.

---

## 5. Architekturregeln

- Schichten: `ui` → `domain` → `data`. Abhängigkeiten zeigen **nur nach innen**.
- `ui` kennt **keine** Room-Entities, keine Supabase-DTOs, kein SDK. Nur Domain-Modelle.
- Backend-Zugriff ausschließlich über Repository-Interfaces im `domain`-Layer.
  Die Supabase-Implementierung liegt in `data` und ist austauschbar. **Diese Regel ist wichtig:**
  sie hält die App gegen jeden späteren Backend-Wechsel und macht Tests ohne Netz möglich.
- ViewModels geben genau einen `StateFlow<UiState>` aus. Kein `LiveData`, keine losen States.
- Keine Businesslogik in Composables. Composables sind reine Funktionen von State zu UI.
- Composables sind bevorzugt **stateless** und bekommen State + Callbacks als Parameter — damit
  sind sie einzeln testbar und screenshot-fähig.
- Alles, was EP, Level, Rang oder Quests berechnet, liegt als **reine Funktion** in `domain`
  und ist ohne Android-Framework testbar.

---

## 6. Spiellogik-Regeln

- EP-Kurve, Stat-Zuordnung und Rang-Schwellen liegen zentral in **einer** Datei
  (`domain/progression/`) — nicht über die App verstreut.
- Balancing-Werte sind Konstanten an einem Ort, keine Magic Numbers im Code.
- **Anti-Cheat ist Teil jedes relevanten Slices, nicht ein späteres Issue:**
  - Schritte, Distanz und Puls kommen aus Health Connect, nie aus manueller Eingabe
  - Manuelle Trainingseingaben werden plausibilisiert (Dauer, maximale Steigerungsrate pro Woche)
  - EP-Berechnung passiert nachvollziehbar und wird als Event gespeichert (Audit-Trail),
    nicht nur als Summe
- Kein „Bestrafungs"-Mechanismus, der Nutzer demotiviert. Verpasste Quests kosten Streak und
  Bonus-Multiplikator — niemals bereits erreichte Level oder Stats.

---

## 7. UI-/Design-Regeln

- Dunkles Theme als Standard, Blau/Violett-Akzente mit Glow-Effekten
- Kantige, technische Typografie; „System-Fenster"-Popups mit Ein-/Ausblende-Animation
- Alle Farben, Abstände und Textstile im zentralen Theme — **keine hartkodierten Werte in Composables**
- Jede Animation muss über `AnimationTestRule` bzw. deaktivierbare Animationen testbar bleiben
- Barrierefreiheit: jedes interaktive Element hat `contentDescription`; Kontraste geprüft
- Kein Screen ohne definierten Lade-, Leer- und Fehlerzustand — alle drei werden getestet

---

## 8. Backend & Deployment

- **Postgres-Schema ausschließlich über versionierte SQL-Migrationen** (`backend/migrations/`),
  fortlaufend nummeriert, niemals nur per Studio-UI geändert. Jede Migration ist idempotent
  anwendbar auf eine leere DB — die CI beweist das bei jedem Lauf.
- Migrationen werden **im Slice erstellt, der sie braucht** — nicht auf Vorrat (siehe Abschnitt 1).
- **Row Level Security ist auf jeder Tabelle aktiviert.** Eine Tabelle ohne Policy gilt als Bug.
  Standard: Nutzer sehen und ändern ausschließlich Zeilen mit ihrer eigenen `user_id`.
- Berechnungen, die nicht manipulierbar sein dürfen (EP-Vergabe, Level-Aufstieg, Rang), laufen
  serverseitig als Postgres-Funktion oder Edge Function — **nie ausschließlich im Client**.
- `backend/docker-compose.yml` mit dem Supabase-Stack (Postgres, GoTrue, PostgREST, Storage,
  Realtime, Studio) + Caddy davor liegt im Repo.
- Secrets ausschließlich in `.env` (gitignored) + `.env.example` mit Platzhaltern im Repo.
  JWT-Secret, Postgres-Passwort und Service-Role-Key werden **nie** ins Repo committed.
  Der Service-Role-Key gehört niemals in die Android-App — dort nur der Anon-Key.
- Studio und Postgres-Port sind **nicht öffentlich erreichbar** — nur über SSH-Tunnel oder
  Caddy mit Basic Auth.
- Zwei Google-OAuth-Client-IDs nötig: Android (mit SHA-1-Fingerprint) und Web (für GoTrue) —
  im README dokumentieren, das ist die häufigste Fehlerquelle.
- Backup: `pg_dump` täglich, verschlüsselt per `restic` auf externen Speicher, plus ein
  dokumentierter, **einmal tatsächlich getesteter** Restore. Teil des Deployment-Issues, nicht optional.

---

## 9. Arbeitsverhalten

- **Frag nach, statt zu raten.** Bei unklaren Anforderungen: Frage im Issue stellen, nicht annehmen.
- **Keine ungefragten Zusatzfeatures.** Was nicht im Issue steht, wird nicht gebaut.
  Gute Ideen werden als neues Issue angelegt.
- **Keine auskommentierten Codeleichen, keine TODOs ohne Issue-Referenz.**
- Bei fehlschlagenden Tests: Ursache verstehen und beheben. **Test niemals abschwächen,
  überspringen oder löschen, um grün zu werden.** Wenn ein Test falsch ist, im Issue begründen.
- Abhängigkeiten werden nur mit Begründung im Issue hinzugefügt.

---

## 10. Mitdenken und Lernen — nach jedem Slice verpflichtend

Du bist kein Abarbeiter einer Liste. Nach **jedem** abgeschlossenen Slice führst du eine kurze
Retrospektive durch und hältst sie als Kommentar am gerade geschlossenen Issue fest:

**1. Was war umständlich?**
Wo hast du gegen die Struktur gearbeitet statt mit ihr? Wo musstest du dieselbe Sache an drei
Stellen ändern? Das sind Hinweise auf ein Architekturproblem — benenne es, auch wenn du es
nicht sofort behebst.

**2. Was hat der Slice über die Domäne verraten?**
Oft zeigt sich erst beim Bauen, dass ein Modell falsch geschnitten war. Sag es. Ein früh
korrigiertes Datenmodell ist billig, ein spät korrigiertes teuer.

**3. Muss der Plan angepasst werden?**
Reihenfolge, Zuschnitt und Umsetzungsweg der offenen Issues sind ausdrücklich änderbar
(siehe Abschnitt 3). Aktualisiere betroffene Issues mit einem Kommentar, der die Änderung
und den Grund nennt.

**4. Muss `CLAUDE.md` oder `docs/DECISIONS.md` ergänzt werden?**

- **`docs/DECISIONS.md`** — hier landet **jede** nennenswerte Architektur- oder Technikentscheidung,
  sofort beim Treffen, nicht später. Format pro Eintrag: Datum, Entscheidung, Alternativen,
  Begründung, Konsequenzen, Issue-Referenz. Das ist das Gedächtnis über Sessions hinweg —
  ohne diese Datei wiederholst du in drei Wochen dieselbe Analyse oder widersprichst dir selbst.
- **`CLAUDE.md`** — nur für Regeln, die für **alle künftigen Slices** gelten: Namenskonvention,
  Testmuster, Fehlerbehandlungsstrategie, Stolperfallen mit Health Connect oder GoTrue.
  Schlag die Ergänzung als fertigen Textblock vor und ergänze sie nach meinem OK.

Faustregel: *Warum* eine Entscheidung fiel → `DECISIONS.md`. *Was künftig immer gilt* → `CLAUDE.md`.

Diese Dateien sollen mit dem Projekt wachsen. Sie sind am Anfang unvollständig; das ist gewollt.

**5. Was würdest du rückblickend anders machen?**
Ein Satz reicht. Wenn dieselbe Antwort dreimal auftaucht, ist sie ein Issue wert.

### Wann du mich fragen musst, statt selbst zu entscheiden

Selbst entscheiden darfst du: Umsetzungsweg innerhalb eines Slices, Reihenfolge zweier
gleichrangiger Issues, Zuschnitt und Aufteilung von Issues, Benennung, Teststruktur.

Vorher fragen musst du bei: Technologiewechsel, neuer Abhängigkeit mit großem Fußabdruck,
Streichen eines geplanten Features, Änderung an den Spielregeln (EP-Formel, Rang-Schwellen,
Stat-Zuordnung), allem, was Nutzerdaten löscht oder migriert, und allem, was Kosten verursacht.
