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
