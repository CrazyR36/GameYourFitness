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
