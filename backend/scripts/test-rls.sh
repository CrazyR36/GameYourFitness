#!/usr/bin/env bash
# RLS-Negativtest fuer public.profiles (Definition of Done, CLAUDE.md Abschnitt 2/8).
# Beweist mit ZWEI echten GoTrue-Nutzern ueber PostgREST, dass ein fremder Nutzer die
# Daten eines anderen weder lesen noch schreiben kann, dass Client-Insert/-Delete
# generell verboten sind und — seit Slice #3 — dass die Progression-Spalten
# (total_xp/rank/Stats) NICHT client-schreibbar sind (Anti-Cheat).
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

# 1) A sieht genau die eigene Zeile inkl. der neuen Charakter-Spalten mit Startwerten.
rows="$(auth_get "$TOKEN_A" "?select=user_id,total_xp,rank,strength,vitality,agility,perception")"
count="$(echo "$rows" | array_len)"
[ "$count" = "1" ] || fail "A sollte genau 1 Profil sehen, sah '$count': $rows"
seen="$(echo "$rows" | jq -r '.[0].user_id')"
[ "$seen" = "$UID_A" ] || fail "A sieht fremdes Profil ($seen statt $UID_A)"
xp="$(echo "$rows" | jq -r '.[0].total_xp')"
rank="$(echo "$rows" | jq -r '.[0].rank')"
str="$(echo "$rows" | jq -r '.[0].strength')"
[ "$xp" = "0" ] && [ "$rank" = "E" ] && [ "$str" = "0" ] ||
    fail "Startwerte falsch (total_xp=$xp rank=$rank strength=$str, erwartet 0/E/0)"
pass "A sieht ausschliesslich die eigene Zeile mit Startwerten (0 EP, Rang E, Stats 0)"

# 2) A kann die Zeile von B nicht lesen (gezielter Filter → leer).
rows="$(auth_get "$TOKEN_A" "?user_id=eq.$UID_B")"
count="$(echo "$rows" | array_len)"
[ "$count" = "0" ] || fail "A konnte B's Zeile lesen: $rows"
pass "A kann B's Profil nicht lesen (RLS-select blockiert)"

# 3) Anti-Cheat: Client-UPDATE ist generell verboten (update-Grant in #3 entzogen).
#    Schon ein Selbst-PATCH auf total_xp wird abgelehnt — damit erst recht auf fremde
#    Zeilen. So kann kein Nutzer seine EP/Stats/Rang manipulieren.
code="$(status PATCH "$BASE_URL/rest/v1/profiles?user_id=eq.$UID_A" \
    -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" \
    -d '{"total_xp":999999}')"
case "$code" in
    401 | 403) pass "Client-UPDATE der eigenen Progression ist verboten (HTTP $code)" ;;
    *) fail "Client-UPDATE nicht verboten (HTTP $code) — total_xp waere manipulierbar" ;;
esac

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
