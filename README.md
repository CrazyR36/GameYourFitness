# GameYourFitness

Gamifizierte Fitness-App für Android im RPG-Stil: Training bringt EP, EP bringen Level,
Stats (STR/VIT/AGI/PER) und Ränge (E→S). Dunkles Theme mit Blau/Violett-Glow und
„System-Fenster"-Popups.

Arbeitsregeln und Architektur: siehe [`CLAUDE.md`](CLAUDE.md).
Getroffene Entscheidungen: siehe [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Projektstruktur

| Pfad | Inhalt |
|---|---|
| `app/src/main/java/com/gameyourfitness/app/ui` | Compose-UI, Theme (`ui/theme` — alle Farben/Typo/Abstände zentral) |
| `app/src/main/java/com/gameyourfitness/app/domain` | Domänenlogik, reine Kotlin-Funktionen (u. a. künftig `domain/progression`) |
| `app/src/main/java/com/gameyourfitness/app/data` | Repository-Implementierungen, Backend-Zugriff |
| `backend/` | Self-hosted Supabase-Stack (Docker Compose) + Caddy |
| `backend/migrations/` | Versionierte SQL-Migrationen (fortlaufend nummeriert) |

## Voraussetzungen

- JDK 17+ (CI verwendet 21)
- Android SDK (compileSdk 35); `local.properties` mit `sdk.dir` oder `ANDROID_HOME`
- Docker + Docker Compose (für den Backend-Stack)

## App bauen & Tests ausführen

```bash
./gradlew assembleDebug          # Build
./gradlew ktlintCheck detekt     # Statische Analyse
./gradlew testDebugUnitTest      # Unit-Tests (JUnit 5, inkl. Robolectric)
./gradlew verifyRoborazziDebug   # Screenshot-Tests gegen Goldens (app/src/test/screenshots/)
./gradlew recordRoborazziDebug   # Goldens neu aufnehmen (bei gewollten UI-Änderungen)
./gradlew connectedDebugAndroidTest  # E2E auf laufendem Emulator/Gerät
```

E2E-Tests (Compose UI Test) brauchen einen laufenden Emulator oder ein Gerät
(`adb devices` muss es listen). Ab Slice #2 laufen sie gegen den lokalen Backend-Stack.

## Backend-Stack lokal starten

```bash
cp backend/.env.example backend/.env   # Beispielwerte, nur für lokal/CI
docker compose -f backend/docker-compose.yml up -d
curl http://localhost:8000/health      # Caddy-Gateway
curl http://localhost:8000/auth/v1/health
```

- API-Gateway (Caddy): `http://localhost:8000` — Routen `/auth/v1`, `/rest/v1`, `/storage/v1`, `/realtime/v1`
- Studio: `http://127.0.0.1:54323` (nur localhost)
- Postgres: `127.0.0.1:54322` (nur localhost)

Secrets liegen ausschließlich in `backend/.env` (gitignored). Die Werte aus
`.env.example` sind öffentlich bekannte Demo-Schlüssel — niemals auf einem Server verwenden.

### Google-OAuth (ab Slice #2 relevant)

Es werden **zwei** OAuth-Client-IDs in der Google Cloud Console benötigt — das ist die
häufigste Fehlerquelle:

1. **Android-Client** mit Package `com.gameyourfitness.app` + SHA-1-Fingerprint des Signing-Keys
2. **Web-Client** — dessen Client-ID/Secret bekommt GoTrue

Details folgen im README, sobald Slice #2 umgesetzt wird.

## CI (GitHub Actions)

Läuft bei jedem Push (`.github/workflows/ci.yml`), drei Jobs:

1. **checks** — Build, ktlint, detekt, Unit-Tests, Screenshot-Verify.
   Fehlen die Goldens (Erstlauf), werden sie aufgenommen und auf den Branch committet.
2. **backend-stack** — startet den kompletten Supabase-Stack mit leerer Datenbank und
   prüft die Erreichbarkeit von Gateway, GoTrue und PostgREST (beweist: Stack + künftige
   Migrationen laufen auf einer leeren DB hoch).
3. **e2e** — startet einen Android-Emulator (API 34) und führt die Compose-E2E-Tests aus.

Ein roter Build wird nie gemergt.
