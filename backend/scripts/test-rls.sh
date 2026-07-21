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

# 1) A sieht genau die eigene Zeile.
rows="$(auth_get "$TOKEN_A" "?select=user_id")"
count="$(echo "$rows" | jq 'length')"
[ "$count" = "1" ] || fail "A sollte genau 1 Profil sehen, sah $count: $rows"
seen="$(echo "$rows" | jq -r '.[0].user_id')"
[ "$seen" = "$UID_A" ] || fail "A sieht fremdes Profil ($seen statt $UID_A)"
pass "A sieht ausschliesslich die eigene Profilzeile"

# 2) A kann die Zeile von B nicht lesen (gezielter Filter → leer).
rows="$(auth_get "$TOKEN_A" "?user_id=eq.$UID_B")"
count="$(echo "$rows" | jq 'length')"
[ "$count" = "0" ] || fail "A konnte B's Zeile lesen: $rows"
pass "A kann B's Profil nicht lesen (RLS-select blockiert)"

# 3) A kann B's Zeile nicht aendern (0 betroffene Zeilen; Return=representation → leer).
resp="$(curl -s -X PATCH "$BASE_URL/rest/v1/profiles?user_id=eq.$UID_B" \
    -H "apikey: $ANON_KEY" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" -H "Prefer: return=representation" \
    -d '{}')"
count="$(echo "$resp" | jq 'if type=="array" then length else 1 end')"
[ "$count" = "0" ] || fail "A konnte B's Zeile aendern: $resp"
pass "A kann B's Profil nicht aendern (RLS-update blockiert)"

# 4) Client-INSERT ist generell verboten (keine insert-Policy, kein Grant).
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/rest/v1/profiles" \
    -H "apikey: $ANON_KEY" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d "{\"user_id\":\"$UID_A\"}")"
[ "$code" = "401" ] || [ "$code" = "403" ] || fail "Client-INSERT nicht verboten (HTTP $code)"
pass "Client-INSERT ist verboten (HTTP $code)"

# 5) Client-DELETE ist generell verboten.
code="$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE_URL/rest/v1/profiles?user_id=eq.$UID_A" \
    -H "apikey: $ANON_KEY" -H "Authorization: Bearer $TOKEN_A")"
[ "$code" = "401" ] || [ "$code" = "403" ] || fail "Client-DELETE nicht verboten (HTTP $code)"
pass "Client-DELETE ist verboten (HTTP $code)"

# 6) Anonymer Zugriff (nur Anon-Key, kein Nutzer-JWT) sieht nichts.
rows="$(curl -s "$BASE_URL/rest/v1/profiles?select=user_id" -H "apikey: $ANON_KEY")"
count="$(echo "$rows" | jq 'if type=="array" then length else -1 end')"
[ "$count" = "0" ] || fail "Anonymer Zugriff sah Profile: $rows"
pass "Anonymer Zugriff sieht keine Profile"

echo "✅ Alle RLS-Negativtests bestanden."
