#!/usr/bin/env bash
# RLS-Negativtest fuer public.profiles (Definition of Done, CLAUDE.md Abschnitt 2/8).
# Beweist mit ZWEI echten GoTrue-Nutzern ueber PostgREST, dass ein fremder Nutzer
# die Daten eines anderen weder lesen noch schreiben kann und dass Client-Insert/
# -Delete generell verboten sind.
#
# Voraussetzung: Stack laeuft und ist migriert. Nutzung aus dem Repo-Root:
#   backend/scripts/test-rls.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8000}"
ENV_FILE="${ENV_FILE:-backend/.env}"
ANON_KEY="$(grep '^ANON_KEY=' "$ENV_FILE" | cut -d= -f2-)"

fail() { echo "❌ RLS-Test fehlgeschlagen: $1"; exit 1; }
pass() { echo "✅ $1"; }

# Legt einen neuen Nutzer an und gibt "<access_token> <user_id>" zurueck.
signup() {
    local email="$1"
    local resp
    resp="$(curl -s -X POST "$BASE_URL/auth/v1/signup" \
        -H "apikey: $ANON_KEY" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$email\",\"password\":\"rls-test-password-123!\"}")"
    local token uid
    token="$(echo "$resp" | jq -r '.access_token')"
    uid="$(echo "$resp" | jq -r '.user.id')"
    if [ -z "$token" ] || [ "$token" = "null" ] || [ -z "$uid" ] || [ "$uid" = "null" ]; then
        fail "Signup von $email lieferte keine Session: $resp"
    fi
    echo "$token $uid"
}

# Anzahl Elemente, falls Array — sonst -1 (z. B. Fehlerobjekt bei Permission-Denied).
array_len() { jq 'if type=="array" then length else -1 end'; }

rand="$RANDOM$RANDOM"
read -r TOKEN_A UID_A <<<"$(signup "rls-a-$rand@example.com")"
read -r TOKEN_B UID_B <<<"$(signup "rls-b-$rand@example.com")"
pass "Zwei Nutzer angelegt (A=$UID_A, B=$UID_B)"

auth_get() {
    local token="$1" query="$2"
    curl -s "$BASE_URL/rest/v1/profiles$query" \
        -H "apikey: $ANON_KEY" \
        -H "Authorization: Bearer $token"
}

status() {
    # status <method> <url> [extra curl args...]
    local method="$1" url="$2"
    shift 2
    curl -s -o /dev/null -w '%{http_code}' -X "$method" "$url" \
        -H "apikey: $ANON_KEY" "$@"
}

# 1) A sieht genau die eigene Zeile (vom Signup-Trigger angelegt).
rows="$(auth_get "$TOKEN_A" "?select=user_id")"
count="$(echo "$rows" | array_len)"
[ "$count" = "1" ] || fail "A sollte genau 1 Profil sehen, sah '$count': $rows"
seen="$(echo "$rows" | jq -r '.[0].user_id')"
[ "$seen" = "$UID_A" ] || fail "A sieht fremdes Profil ($seen statt $UID_A)"
pass "A sieht ausschliesslich die eigene Profilzeile"

# 2) A kann die Zeile von B nicht lesen (gezielter Filter → leer).
rows="$(auth_get "$TOKEN_A" "?user_id=eq.$UID_B")"
count="$(echo "$rows" | array_len)"
[ "$count" = "0" ] || fail "A konnte B's Zeile lesen: $rows"
pass "A kann B's Profil nicht lesen (RLS-select blockiert)"

# 3) A kann B's Zeile nicht aendern: PATCH auf eine echte Spalte, RLS filtert die
#    Zeile weg → 0 betroffene Zeilen (Return=representation liefert leeres Array).
resp="$(curl -s -X PATCH "$BASE_URL/rest/v1/profiles?user_id=eq.$UID_B" \
    -H "apikey: $ANON_KEY" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" -H "Prefer: return=representation" \
    -d '{"created_at":"2020-01-01T00:00:00+00:00"}')"
count="$(echo "$resp" | array_len)"
[ "$count" = "0" ] || fail "A konnte B's Zeile aendern (erwartet 0 Zeilen): $resp"
pass "A kann B's Profil nicht aendern (RLS-update blockiert)"

# 4) Client-INSERT ist verboten (keine insert-Policy + Grant zurueckgenommen).
code="$(status POST "$BASE_URL/rest/v1/profiles" \
    -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" \
    -d "{\"user_id\":\"$UID_A\"}")"
case "$code" in
    401 | 403) pass "Client-INSERT ist verboten (HTTP $code)" ;;
    *) fail "Client-INSERT nicht verboten (HTTP $code)" ;;
esac

# 5) Client-DELETE ist verboten.
code="$(status DELETE "$BASE_URL/rest/v1/profiles?user_id=eq.$UID_A" \
    -H "Authorization: Bearer $TOKEN_A")"
case "$code" in
    401 | 403) pass "Client-DELETE ist verboten (HTTP $code)" ;;
    *) fail "Client-DELETE nicht verboten (HTTP $code)" ;;
esac

# 6) Anonymer Zugriff (nur Anon-Key, kein Nutzer-JWT) sieht nichts. Ohne Grant an
#    anon antwortet PostgREST mit Permission-Denied (Fehlerobjekt) — auch das ist
#    "sieht keine Profile". Ein leeres Array wird ebenfalls akzeptiert.
rows="$(curl -s "$BASE_URL/rest/v1/profiles?select=user_id" -H "apikey: $ANON_KEY")"
count="$(echo "$rows" | array_len)"
if [ "$count" = "0" ] || [ "$count" = "-1" ]; then
    pass "Anonymer Zugriff sieht keine Profile (Antwort: $rows)"
else
    fail "Anonymer Zugriff sah $count Profil(e): $rows"
fi

echo "✅ Alle RLS-Negativtests bestanden."
