# Architektur- und Technikentscheidungen

Gedächtnis über Sessions hinweg (CLAUDE.md Abschnitt 10). Jede nennenswerte Entscheidung
wird **sofort beim Treffen** hier eingetragen: Datum, Entscheidung, Alternativen, Begründung,
Konsequenzen, Issue-Referenz.

---

## 2026-07-19 — Android nativ (Kotlin + Jetpack Compose)

- **Entscheidung:** Die App wird nativ für Android gebaut: Kotlin, Jetpack Compose, Material 3.
- **Alternativen:** Flutter, React Native, Kotlin Multiplatform, PWA.
- **Begründung:** Health Connect, Foreground-GPS-Services und WorkManager sind Android-Kernthemen
  des Produkts; native APIs vermeiden Wrapper-Reibung. Es gibt genau eine Zielplattform
  (kein iOS geplant), damit entfällt der Hauptvorteil von Cross-Platform.
- **Konsequenzen:** Kein Code-Sharing mit anderen Plattformen; dafür voller Zugriff auf
  Compose-Testwerkzeuge (UI-Test, Roborazzi) und Android-APIs ohne Bridge.
- **Issue:** #1

## 2026-07-19 — Supabase self-hosted (Postgres) statt BaaS-Cloud oder PocketBase

- **Entscheidung:** Backend ist der self-hosted Supabase-Stack (Postgres, GoTrue, PostgREST,
  Storage, Realtime, Studio) auf eigenem Ubuntu-Server.
- **Alternativen:** Firebase, Supabase-Cloud, PocketBase, eigenes REST-Backend.
- **Begründung:** Volle Datenhoheit und keine laufenden Cloud-Kosten; Postgres mit Row Level
  Security erlaubt Autorisierung in der Datenbank statt in App-Code; GoTrue bringt Google-SSO
  mit; PostgREST erspart eine eigene API-Schicht. PocketBase wäre leichter, hat aber kein
  echtes RLS und eine schwächere Migrationsgeschichte; Firebase widerspricht Datenhoheit
  und koppelt an proprietäre APIs.
- **Konsequenzen:** Eigener Betriebsaufwand (Deployment, Backups — Issue #13); RLS-Policies
  sind Pflichtbestandteil jedes Schema-Slices; serverseitige Spiellogik als Postgres-Funktionen.
- **Issue:** #1

## 2026-07-19 — Docker Compose statt Kubernetes

- **Entscheidung:** Der Backend-Stack läuft als Docker Compose auf einem einzelnen
  Ubuntu-24.04-Host, Caddy als Reverse Proxy davor.
- **Alternativen:** Kubernetes (k3s/k8s), Nomad, systemd-Dienste ohne Container.
- **Begründung:** Ein Host, eine Handvoll Dienste, ein Entwickler — Kubernetes-Komplexität
  (Cluster-Betrieb, Manifest-Pflege) bringt hier keinen Nutzen. Compose ist reproduzierbar,
  in CI startbar und auf dem Server identisch verwendbar.
- **Konsequenzen:** Keine Hochverfügbarkeit/Autoskalierung — bewusst akzeptiert.
- **Issue:** #1

## 2026-07-19 — Google SSO über Credential Manager API → GoTrue `signInWithIdToken`

- **Entscheidung:** Anmeldung ausschließlich per Google SSO: Credential Manager API holt das
  ID-Token, GoTrue validiert es (`signInWithIdToken`).
- **Alternativen:** E-Mail/Passwort in GoTrue, OAuth-Browser-Flow (Custom Tabs), Firebase Auth.
- **Begründung:** Credential Manager ist der von Google empfohlene, zukunftssichere Weg
  (One-Tap-UX, keine Browser-Weiterleitung); kein Passwort-Handling und kein Mailer nötig.
- **Konsequenzen:** Zwei OAuth-Client-IDs erforderlich (Android mit SHA-1 + Web für GoTrue) —
  häufigste Fehlerquelle, im README dokumentieren (Slice #2). Google-SSO wird in E2E-Tests
  gemockt, GoTrue selbst wird echt getestet.
- **Issue:** #1, #2

## 2026-07-19 — Caddy statt Kong als Gateway vor dem Supabase-Stack

- **Entscheidung:** Als API-Gateway dient Caddy (Pfad-Routing auf GoTrue/PostgREST/Storage/
  Realtime), nicht das im offiziellen Supabase-Compose enthaltene Kong.
- **Alternativen:** Kong (Supabase-Standard), nginx, Traefik.
- **Begründung:** CLAUDE.md setzt Caddy ohnehin als Reverse Proxy mit automatischem TLS.
  Ein zweites Gateway (Kong) davor wäre doppelte Infrastruktur; die Kong-Features
  (Key-Auth-Plugins, Konsumenten) werden nicht gebraucht, weil die Schlüsselprüfung in
  GoTrue/PostgREST selbst stattfindet.
- **Konsequenzen:** Routing-Konventionen (`/auth/v1`, `/rest/v1`, `/storage/v1`, `/realtime/v1`)
  liegen in `backend/caddy/Caddyfile`; Realtime-Websocket-Routing wird im ersten
  Realtime-Slice (#12) real verifiziert.
- **Issue:** #1

## 2026-07-19 — JUnit-5-Plattform mit Vintage-Engine für Robolectric/Roborazzi

- **Entscheidung:** Unit-Tests laufen auf der JUnit-Plattform (JUnit 5). Robolectric-basierte
  Tests (Roborazzi-Screenshots) bleiben JUnit 4 und laufen im selben Lauf über die
  Vintage-Engine.
- **Alternativen:** Alles JUnit 4; getrennte Test-Tasks je Framework.
- **Begründung:** CLAUDE.md setzt JUnit 5 für Unit-Tests; Robolectric unterstützt kein
  natives JUnit 5. Die Vintage-Engine vereint beides ohne Doppel-Infrastruktur.
- **Konsequenzen:** Neue Screenshot-Tests verwenden JUnit-4-Annotationen
  (`org.junit.Test` + `@RunWith(RobolectricTestRunner)`), reine Domänentests JUnit 5.
- **Issue:** #1

## 2026-07-21 — Handgeschriebener GoTrue-Client statt supabase-kt-SDK

- **Entscheidung:** Der Backend-Zugriff für Auth erfolgt über einen schlanken, selbst
  geschriebenen GoTrue-Client (OkHttp + kotlinx.serialization) hinter dem
  `AuthRepository`-Interface im `domain`-Layer.
- **Alternativen:** `supabase-kt` (Community-SDK, Ktor-basiert), Retrofit.
- **Begründung:** Der Login-Slice braucht genau drei GoTrue-Endpunkte (`token?grant_type=
  id_token`, `token?grant_type=refresh_token`, `logout`). Das SDK zieht die komplette
  Ktor-Client-Kette plus eigene Modellwelt herein — unverhältnismäßig für drei Requests und
  gegen die Slice-Regel „nur so viel bauen wie nötig" (CLAUDE.md Abschnitt 1). OkHttp ist
  klein und battle-tested.
- **Konsequenzen:** Endpunkte/DTOs werden pro Slice erweitert. Das `AuthRepository`-Interface
  hält die Wahl austauschbar — falls später breite Supabase-Nutzung (Realtime, Storage-SDK)
  kommt, kann die Implementierung ohne UI-Änderung auf das SDK wechseln.
- **Issue:** #2

## 2026-07-21 — Mock-Grenze für Google-SSO liegt in der data-Schicht

- **Entscheidung:** In E2E-Tests werden nur zwei Dinge ersetzt: die ID-Token-Beschaffung
  (`GoogleIdTokenClient` → Fake) und der Token-Tausch (`GoTrueApi.signInWithIdToken` →
  echter GoTrue-Signup per E-Mail/Passwort). Alles andere — Session, Refresh, Logout,
  Persistenz, Profil-Trigger, RLS — läuft echt gegen den Stack.
- **Alternativen:** Kompletten Auth-Flow faken; echtes Google-ID-Token in CI erzeugen.
- **Begründung:** GoTrue validiert Google-ID-Tokens kryptografisch gegen Googles JWKS; ein
  lokal erzeugtes Token kann ein echtes GoTrue nicht akzeptieren. Die einzige testbare
  Grenze ist deshalb die data-Schicht. So bleibt „Google-SSO gemockt, GoTrue echt"
  (CLAUDE.md/DECISIONS #4) maximal ehrlich.
- **Konsequenzen:** `GoogleAuthModule` und `GoTrueApiModule` sind eigene Hilt-Module, damit
  `@TestInstallIn` genau diese Bindings ersetzt. GoTrue braucht in der Testumgebung
  E-Mail-Signup mit Autoconfirm (bereits aktiv).
- **Issue:** #2

## 2026-07-21 — Session-Refresh: Netzfehler ≠ Logout

- **Entscheidung:** Beim App-Start wird eine abgelaufene Session per Refresh-Token erneuert.
  Lehnt GoTrue den Refresh-Token ab (HTTP-Fehler), wird abgemeldet und die Session gelöscht.
  Bei reinen Netzwerkfehlern bleibt der Nutzer angemeldet (Session behalten, Refresh beim
  nächsten Anlauf).
- **Alternativen:** Bei jedem Refresh-Fehler abmelden.
- **Begründung:** „Kein Netz" ist kein „ungültige Sitzung". Abmelden bei jedem Offline-Start
  wäre nutzerfeindlich und widerspricht dem Anti-Frust-Prinzip (CLAUDE.md Abschnitt 6).
- **Konsequenzen:** Access-Token gilt inkl. 60-s-Sicherheitsfenster als abgelaufen, damit es
  nie „auf den letzten Drücker" verwendet wird. Durch Unit-Tests abgesichert.
- **Issue:** #2

## 2026-07-21 — Navigation ohne Navigation-Library (vorerst)

- **Entscheidung:** Zwischen Login- und Startbildschirm wird im Root-Composable anhand des
  Auth-Zustands (`AuthState`) umgeschaltet, ohne Navigation-Compose.
- **Alternativen:** Navigation-Compose von Anfang an.
- **Begründung:** Es gibt genau zwei Zustände und kein echtes Navigationsziel/Backstack.
  Eine Navigations-Library wäre Vorratsarbeit (CLAUDE.md Abschnitt 1).
- **Konsequenzen:** Navigation-Compose kommt, sobald mehrere echte Ziele existieren
  (voraussichtlich Slice #3/#4). Der Schalter in `AppRoot` ist dann leicht zu ersetzen.
- **Issue:** #2

## 2026-07-21 — RLS-Tabellen: Default-Grants explizit zurücknehmen

- **Entscheidung:** Jede neue Tabelle nimmt zuerst `revoke all ... from anon, authenticated`
  und vergibt danach nur die tatsächlich benötigten Rechte. RLS-Policies allein genügen nicht.
- **Alternativen:** Sich auf „keine Policy ⇒ kein Zugriff" verlassen.
- **Begründung:** Das self-hosted Supabase-Postgres-Image vergibt `anon`/`authenticated`
  per `ALTER DEFAULT PRIVILEGES IN SCHEMA public` breite Tabellenrechte (u. a. INSERT/DELETE).
  Eine Tabelle „ohne Policy" ist damit **nicht** automatisch dicht: Ein DELETE ohne passende
  Policy trifft nur RLS, löscht 0 Zeilen und liefert **HTTP 204 „erfolgreich"** statt eines
  Verbots — im Slice #2 durch den RLS-Negativtest aufgedeckt (DELETE lieferte 204). Erst das
  Entziehen des Grants führt zu echtem „permission denied" (403).
- **Konsequenzen:** Muster für alle künftigen Schema-Slices; der RLS-Negativtest prüft neben
  Fremd-Lese-/Schreibzugriff explizit die HTTP-Codes von Client-INSERT/DELETE. Als Regel in
  `CLAUDE.md` (Abschnitt 8) vorgeschlagen.
- **Issue:** #2

## 2026-07-21 — `allowBackup=false` wegen unverschlüsselter Session-Token

- **Entscheidung:** Die App setzt `android:allowBackup="false"`.
- **Alternativen:** Backup an lassen und die `auth_session`-Datei per
  `dataExtractionRules` (API 31+) / `fullBackupContent` (API ≤30) ausschließen; Token
  verschlüsselt ablegen (z. B. EncryptedSharedPreferences/Tink).
- **Begründung:** Die Session (Access-/Refresh-Token) liegt im DataStore Preferences
  **unverschlüsselt**. Mit `allowBackup=true` sichert Android Auto Backup sie in die Google-
  Cloud; sie ließe sich auf einem Fremdgerät wiederherstellen (Session-Hijacking) und könnte
  eine Session **nach dem Logout** wieder einspielen. In einem Auth-Slice ist das genau die
  Datenschutz-/Secrets-Lücke aus CLAUDE.md Abschnitt 8. Da noch kein Backup-Feature gebraucht
  wird, ist die vollständige Abschaltung die einfachste dichte Lösung (PR #15-Review).
- **Konsequenzen:** Kein App-Datentransfer/Cloud-Restore. Sobald Backup gewünscht ist (z. B.
  für nicht-sensible Präferenzen), muss es gezielt aktiviert werden **mit** Ausschluss der
  `auth_session`-Datei oder verschlüsselter Token-Ablage — nicht pauschal `allowBackup=true`.
- **Issue:** #2

## 2026-07-22 — Agentische Projekt-Infrastruktur unter `.claude/`

- **Entscheidung:** Das Repo bekommt versioniertes Agent-Tooling unter `.claude/`:
  (a) eigener Workflow-Skill `next-slice` (operationalisiert CLAUDE.md Abschnitt 0),
  (b) eigener `design-system`-Skill (Abschnitt 7 mit den echten Theme-Tokens + System-Fenster),
  (c) vendored & gepinnte **Handwerks**-Skills aus `chrisbanes/skills` (Compose/Kotlin).
- **Alternativen:** Keine Skills (Regeln nur als gelesener Text); externe Skills per
  Live-Install (`npx skills add`) statt vendored; breite Adoption mehrerer Skill-Repos.
- **Begründung:** CLAUDE.md ist exzellent, wurde aber jede Session neu von Hand befolgt —
  Skills machen den Ablauf wiederholbar. Universelles Compose/Kotlin-Handwerk ist bei
  Experten-Skills (Chris Banes) besser aufgehoben als im Eigenbau; Projektwissen
  (Slice-Prozess, System-Fenster, RLS-Fallen) gibt es extern nicht → bleibt eigen.
  Vendored+gepinnt (Tag `2026.7.21`, Commit `289eb24`) statt Live-Install passt zur
  Reproduzierbarkeits-Kultur (gepinnte `libs.versions.toml`, idempotente CI) und erlaubt
  Review vor Vertrauen (SKILL.md = Anweisungen, die der Agent befolgt).
- **Konsequenzen:** Nur reputable Quellen (Autor-Glaubwürdigkeit/Verbreitung). Die
  Workflow-Skills aus chrisbanes (`implement-issue`, `shepherd`, Router) **nicht** übernommen
  — sie würden mit `next-slice` um Trigger konkurrieren; eigener Prozess + CLAUDE.md regeln
  das. Unter reputable-only abgelehnt: rcosteira79 (Koin ↔ Hilt), aihip (Firebase verboten),
  claude-android-ninja (monolithisch). Update der vendored Skills: neuen Tag ziehen,
  compose-*/kotlin-* ersetzen, Commit/Tag in `.claude/skills/NOTICE-chrisbanes-skills.md`
  pflegen, Inhalt sichten. **Zurückgestellt** (Nutzer-Entscheidung, 2026-07-22):
  SessionStart-Hook und `settings.json`-Permission-Allowlist.
- **Issue:** Session „improve-agentic-work"

## 2026-07-22 — Vendored Handwerks-Skills ins Deutsche übersetzt

- **Entscheidung:** Alle 18 vendored `compose-*`/`kotlin-*`-Skills (chrisbanes) wurden ins
  Deutsche übersetzt (Prosa, Tabellen, Code-Kommentare; Code, `name:`-IDs, API-Bezeichner
  und URLs unverändert).
- **Alternativen:** Englisch belassen (funktional identisch); nur die `description:`-Felder
  übersetzen.
- **Begründung:** Konsistenz mit der durchgehend deutschen Projektsprache (CLAUDE.md,
  DECISIONS.md, eigene Skills) und leichtere Lesbarkeit/Review durch den Betreiber
  (ausdrücklicher Wunsch). Funktional ändert die Sprache nichts — Skills steuern das
  Verhalten des Agenten, nicht seine Antwortsprache.
- **Konsequenzen:** Die Skills sind jetzt ein **übersetzter Fork**; „Update = neuen Tag
  ziehen" ist nicht mehr verlustfrei (überschreibt die Übersetzung). Update-Vorgehen steht in
  `.claude/skills/NOTICE-chrisbanes-skills.md`; jede Datei trägt einen Apache-2.0-
  Änderungshinweis. Rückbau auf die englischen Originale jederzeit möglich.
- **Issue:** Session „improve-agentic-work"

## 2026-07-21 — Charakterdaten erweitern `profiles`, Level wird aus EP abgeleitet

- **Entscheidung:** Der Charakter-Spielstand liegt als zusätzliche Spalten auf `public.profiles`
  (`total_xp`, `rank`, `strength`, `vitality`, `agility`, `perception`), nicht in einer eigenen
  `characters`-Tabelle. Das **Level** wird NICHT gespeichert, sondern per reiner Funktion aus
  `total_xp` abgeleitet (`domain/progression/Progression.levelForXp`).
- **Alternativen:** Separate `characters`-Tabelle (1:1 zu `profiles`); Level als eigene Spalte speichern.
- **Begründung:** In #2 wurde `profiles` bewusst minimal gehalten (nur `user_id`, `created_at`),
  damit #3 sie erweitert statt Vorratsfelder mitzuschleppen (so auch die #2-Retro). Eine 1:1-Tabelle
  wäre reine Zeremonie. Ein gespeichertes Level könnte von `total_xp` abweichen — eine Quelle der
  Wahrheit (CLAUDE.md 6) verlangt die Ableitung.
- **Konsequenzen:** `total_xp` ist die materialisierte EP-Summe; der Audit-Trail (EP-Events) entsteht
  in #4, wenn EP tatsächlich vergeben werden. Ab #5 berechnet der Server das Level für die
  Level-Up-Erkennung — dann muss die EP-Kurve serverseitig (SQL) gespiegelt und per Test gegen die
  Kotlin-Kurve abgeglichen werden. Für #3 reicht die clientseitige Ableitung (nur Anzeige).
- **Issue:** #3

## 2026-07-21 — EP-Kurve und Rang-Schwellen: erste Version (bestätigungspflichtig)

- **Entscheidung:** Balancing zentral in `domain/progression/` als reine Funktionen/Konstanten.
  EP für L→L+1 = `100 · L`; kumulativ bis Level L = `50 · (L-1) · L` (L2=100, L3=300, L5=1000).
  Rang-Mindestlevel: E=1, D=5, C=10, B=20, A=35, S=50. Start-Stats **je 0** (Nutzer-Entscheidung
  2026-07-22: „jeder startet bei 0"; zuvor 10), Start-Rang E.
- **Alternativen:** Lineare EP-Kurve, exponentielle Kurve (`BASE·q^L`), Rang direkt aus Level ableiten.
- **Begründung:** Quadratisch wächst spürbar, aber nicht erdrückend, und die Umkehrfunktion ist in
  ganzzahliger Arithmetik exakt lösbar. Der Rang ist bewusst KEIN Level-Derivat, sondern wird ab #10
  über einen Aufstiegstest verdient (Solo-Leveling-Gefühl) — die Schwellen gaten nur die Eignung.
- **Konsequenzen:** Spielregeln (CLAUDE.md 6/10) → dem Nutzer vorgelegt (Issue #3). Der Nutzer hat
  Start-Stats **0** bestätigt; EP-Kurve und Rang-Schwellen bleiben **provisorisch** und werden am Ende
  gesammelt final abgestimmt (dediziertes Tracking-Issue). Alle Werte sind Konstanten an einer Stelle
  und ohne Code-Umbau änderbar. Initiale Charakterwerte liegen als **Migration-Defaults** serverseitig
  (Server ist Quelle der Wahrheit für Anfangswerte) — bewusst getrennt von den Kurven-Konstanten
  (Berechnung ≠ Seeding).
- **Issue:** #3 (Feinbalancing: Tracking-Issue #16)

## 2026-07-21 — `profiles` ist client-read-only (Anti-Cheat)

- **Entscheidung:** In #3 werden das `update`-Grant und die `profiles_update_own`-Policy aus #2
  zurückgenommen. `profiles` ist für Clients nur noch lesbar; `total_xp`/`rank`/Stats sind
  serverseitig verwaltet (Trigger jetzt; EP-Funktionen ab #4).
- **Alternativen:** Update erlauben und nur einzelne Spalten per Trigger schützen; Update erst in #4 sperren.
- **Begründung:** EP/Stats/Rang dürfen nie client-schreibbar sein (CLAUDE.md 6). Die #2-Update-Policy
  war ungenutzt; ein offener Schreibpfad auf `total_xp` wäre genau die Cheat-Fläche, die #3 mit den
  Progression-Spalten erst einführt — also hier schließen, nicht später. Der RLS-Negativtest beweist:
  Selbst-`PATCH` auf `total_xp` → 403.
- **Konsequenzen:** Serverseitige Änderungen laufen über `security definer`-Funktionen/Trigger.
  Wird künftig ein nutzer-editierbares Feld nötig (z. B. Anzeigename), bekommt es eine gezielte,
  spaltenbeschränkte Update-Policy — kein pauschales Tabellen-Update.
- **Issue:** #3

## 2026-07-21 — E2E-Seeding von Charakterwerten über `service_role`

- **Entscheidung:** Der „gefüllter EP-Balken"-E2E-Test seedet EP/Stats per PostgREST-`PATCH` mit dem
  `service_role`-Key (bypasst RLS). Der Screen liest danach echt mit dem Nutzer-JWT.
- **Alternativen:** Client-Update erlauben (widerspricht Anti-Cheat); `service_role`-Key per
  Instrumentation-Argument aus der CI reichen; eine test-gegatete SQL-RPC.
- **Begründung:** Die Startwerte-Defaults reichen für „neuer Nutzer", aber nicht für einen sichtbar
  gefüllten Balken (Level 1, 0 %). Da der Client nicht schreiben darf, ist der Admin-Key der einzige
  saubere Seed-Weg. Er ist der **öffentliche** Beispiel-Key des lokalen/CI-Stacks und liegt
  ausschließlich im `androidTest`-Quellcode (Test-APK) — nie in der App (CLAUDE.md 8); auf einem echten
  Server wird er abgelehnt (#13). So bleibt „gegen echtes Backend" gewahrt.
- **Konsequenzen:** Muster für künftige Slices, die serverseitig verwaltete Werte anzeigen. Der
  Offline-Fall wird separat über ein austauschbares `CharacterApi`-Binding (`@TestInstallIn`) erzwungen.
- **Issue:** #3

## 2026-07-21 — Post-Login-Screen ist der Charakterbildschirm; eigener PostgREST-Client

- **Entscheidung:** Der bisherige Home-Screen wird durch den Charakterbildschirm ersetzt (eigenes
  `CharacterViewModel`, genau ein `StateFlow<UiState>`). Der Backend-Zugriff nutzt einen schlanken,
  handgeschriebenen PostgREST-Client (OkHttp) hinter dem `CharacterRepository`-Interface.
- **Alternativen:** Home und Charakter als getrennte Ziele (Navigation-Library); `supabase-kt`/PostgREST-SDK.
- **Begründung:** Es gibt weiterhin nur zwei App-Zustände (an-/abgemeldet) → keine Navigation-Library
  nötig (wie #2). Der Slice braucht genau einen PostgREST-`GET` — ein SDK wäre unverhältnismäßig
  (analog zum GoTrue-Client, DECISIONS #2). Die zwei `home_*`-TestTags wurden auf `character_*` migriert
  (LoginE2ETest zog mit; gleiche Zusicherung).
- **Konsequenzen:** `CharacterHttpApi` liest den Access-Token aus dem `SessionStore` (data→data). Ein
  proaktiver Token-Refresh pro Request ist bewusst nicht Teil von #3 (Refresh beim App-Start deckt den
  Normalfall; abgelaufener Token → Fehlerzustand mit Retry). Navigation-Compose kommt mit dem ersten
  echten Mehrfachziel.
- **Issue:** #3

## 2026-07-23 — EP-Vergabe fürs Krafttraining serverseitig als RPC + Audit-Trail

- **Entscheidung:** Das Erfassen eines Krafttrainings läuft über EINE `security definer`-Postgres-Funktion
  `log_strength_workout(exercise, sets, reps, weight)`. Sie validiert, schreibt die Trainingszeile
  (`strength_workouts`) **und** einen EP-Event-Eintrag (`xp_events`: `source`/`xp_amount`/`ref_id`) und
  erhöht `profiles.total_xp`/`strength` — alles in einer Transaktion. Beide Tabellen sind **client-read-only**
  (RLS select-own, Grants an `anon`/`authenticated` erst `revoke`, dann nur `select`). Der Client ruft die
  RPC per PostgREST auf und liest danach den Charakter neu.
- **Alternativen:** EP im Client berechnen und `total_xp` per Client-`PATCH` schreiben; Trigger auf einer
  client-beschreibbaren `workouts`-Tabelle; getrennte RPCs für Insert und EP.
- **Begründung:** EP/Stats dürfen nie client-schreibbar sein (CLAUDE.md 6/8); die einzige Schreibstelle ist
  die Funktion. Der generische `xp_events`-Audit-Trail (statt nur einer Summe) ist die Grundlage für die
  Diagramme (#11) und macht jede EP-Vergabe nachvollziehbar. Eine Funktion garantiert Atomarität
  (Training + Event + Summe konsistent). Handgeschriebener PostgREST-RPC-Client (kein SDK) wie bei
  GoTrue/Character (DECISIONS 2026-07-21).
- **Konsequenzen:** Muster für alle künftigen EP-Quellen (Quests #6, Schritte #7, Läufe #8): je eine
  `security definer`-RPC, die `xp_events` schreibt. `xp_events` ist bewusst generisch, damit neue Quellen
  keine neue Buchführung brauchen. Die Default-Grants-Falle (DECISIONS 2026-07-21) gilt auch hier; der
  RLS-Negativtest prüft Client-INSERT-Verbot, Fremd-Lese-Verbot und den RPC-Weg (EP/Audit/Ablehnung).
- **Issue:** #4

## 2026-07-23 — EP-Formel Krafttraining + STR-Zuwachs (erste Version, provisorisch)

- **Entscheidung:** EP je Krafttraining = `sätze · wdh · (100 + gewicht_kg) / 100` (Ganzzahl); 0 kg = reines
  Volumen. STR-Zuwachs = **+1 pro erfasstem Training** (flach). Beides liegt zentral in
  `domain/progression/Progression.kt` (`strengthWorkoutXp`, `STRENGTH_STAT_GAIN_PER_WORKOUT`) **und** ist in
  der SQL-Funktion gespiegelt. Der E2E-Test gleicht Kotlin- und SQL-Formel Ende-zu-Ende ab (40 EP für
  5×5 @ 60 kg).
- **Alternativen:** STR skaliert mit Volumen/EP; multiplikative Gewichtskurve; EP nur aus Volumen ohne
  Gewicht.
- **Begründung:** Quadratisch in „Arbeit" (Volumen×Gewicht), ganzzahlig exakt, transparent. STR bewusst
  flach: die Intensität steckt bereits in den EP (und damit im Level) — STR ist der langsame „du wirst
  stärker"-Zähler, kein zweites Volumenmaß. Provisorisch wie die Levelkurve (#3), an einer Stelle als
  Konstante, ohne Code-Umbau änderbar.
- **Konsequenzen:** Spielregel (CLAUDE.md 6/10) → provisorisch, dem Nutzer vorgelegt und in das
  Balancing-Tracking-Issue **#16** aufgenommen. Für #5 (Level-Up serverseitig) ist die EP-Kurve ohnehin in
  SQL zu spiegeln; die Workout-EP-Funktion ist bereits das erste SQL/Kotlin-Spiegelpaar. Anti-Cheat
  „maximale Steigerungsrate pro Woche" braucht Trainingshistorie → spätere Verfeinerung (Kandidat #11/#16);
  die absoluten Plausibilitätsgrenzen decken #4 ab.
- **Issue:** #4 (Feinbalancing: #16)

## 2026-07-23 — Krafttraining-Erfassung als Popup (kein Navigation-Library), zwei ViewModels

- **Entscheidung:** Das Erfassen ist ein „System-Fenster"-Popup (`Dialog`) über dem Charakterbildschirm,
  kein eigenes Navigationsziel. Eigener `WorkoutLogViewModel` (genau ein `StateFlow`) für den Absende-Status;
  `CharacterRoute` hostet beide ViewModels und lädt nach `Success` denselben Charakter neu. Die Feldwerte
  sind lokaler Compose-State im Formular; die Feldvalidierung ist eine **reine Domänenfunktion**
  `StrengthWorkoutValidator` (framework-frei, ohne `R`), deren Grenzen die SQL-CHECK-Constraints spiegeln.
- **Alternativen:** Navigation-Compose einführen (in #2/#3 als „erstes Mehrfachziel" avisiert); den
  Workout-Status in `CharacterUiState` falten (eine VM); Validierung im ViewModel/Composable.
- **Begründung:** Es gibt weiterhin kein echtes Navigationsziel mit Backstack — ein Popup passt zum
  „System-Fenster"-Design und macht „EP-Balken aktualisiert sich" trivial (dieselbe `CharacterViewModel`-
  Instanz lädt neu; kein Cross-VM-Result-Passing/Nav-Reload nötig). Zwei kleine VMs mit je einem `StateFlow`
  halten die „ein StateFlow pro Screen/Popup"-Regel sauberer als eine überladene VM. Navigation-Compose
  bleibt Vorratsarbeit, bis ein echtes drittes Ziel entsteht.
- **Konsequenzen:** `WorkoutLogUiState.Success` ist ein kurzlebiger Zustand, den `CharacterRoute` per
  `LaunchedEffect` konsumiert (reload + schließen + `reset()`); nach `reset()` steht wieder `Idle`, daher
  kein Re-Trigger. Sollte später ein echter Screen-Wechsel nötig werden (Historie, Quests-Screen), wird
  Navigation-Compose eingeführt und das Popup-Muster bleibt für Formulare bestehen.
- **Issue:** #4

## 2026-07-23 — Level-Erkennung serverseitig; Level-Kurve in SQL gespiegelt

- **Entscheidung:** Die EP-Kurve aus `domain/progression/Progression` (Kotlin) wird als SQL-Funktionen
  `xp_to_reach_level`/`level_for_xp` gespiegelt (Migration `0004`). Die bestehende RPC `log_strength_workout`
  liefert zusätzlich `level_before`/`level_after` (aus `total_xp` vor/nach der Vergabe, Zeile per
  `for update` gesperrt). Der Client zeigt bei `level_after > level_before` das Level-Up-Popup. Die
  SQL↔Kotlin-Parität wird im `backend-stack`-Job (`test-rls.sh`) an denselben Grenzwerten wie
  `ProgressionTest` per `psql` bewiesen.
- **Alternativen:** Level-Up nur clientseitig aus `total_xp` ableiten; eine separate RPC nur für die
  Level-Berechnung; das Level als Spalte speichern.
- **Begründung:** „Level-Aufstieg wird serverseitig berechnet, nie nur im Client" (CLAUDE.md 6/8, Issue #5).
  Die Level-Erkennung dort zu machen, wo die EP entstehen (in der Vergabe-RPC), hält sie manipulationssicher
  und ist atomar mit der EP-Buchung. `level_for_xp` nutzt eine Float-Schätzung mit exakter Ganzzahl-Korrektur,
  damit es an den Schwellen bitgenau der Kotlin-Binärsuche entspricht — der `psql`-Test in `test-rls.sh` ist
  der in DECISIONS (2026-07-21, #3) angekündigte Abgleich. Level bleibt abgeleitet (nicht gespeichert) —
  eine Quelle der Wahrheit.
- **Konsequenzen:** Muster für alle künftigen EP-Quellen (#6/#7/#8): dieselbe RPC-Konvention
  (`level_before`/`level_after` zurückgeben), derselbe Client-Pfad zum Popup. Das animierte „System-Fenster"-
  Level-Up-Popup (`LevelUpOverlay` via `AnimatedVisibility`, stateless `LevelUpPopup`-Inhalt) ist über die
  auf dem CI-Emulator deaktivierten Animationen testbar; das Popup ist transient (nach App-Neustart wird nur
  das Level angezeigt, kein Popup). Die Level-**Kurve** selbst ist unverändert (aus #3, provisorisch #16).
- **Issue:** #5
