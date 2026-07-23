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

# Wie auth_get, aber fuer beliebige Tabellen (Slice #4: strength_workouts, xp_events).
auth_get_table() {
    local token="$1" table="$2" query="$3"
    curl -s "$BASE_URL/rest/v1/$table$query" \
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

# 7) Slice #4 — Anti-Cheat: Client-INSERT in die neuen Tabellen ist verboten.
code="$(status POST "$BASE_URL/rest/v1/strength_workouts" \
    -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" \
    -d "{\"user_id\":\"$UID_A\",\"exercise\":\"Cheat\",\"sets\":1,\"reps\":1,\"weight_kg\":0,\"xp_awarded\":999999}")"
case "$code" in
    401 | 403) pass "Client-INSERT in strength_workouts ist verboten (HTTP $code)" ;;
    *) fail "Client-INSERT in strength_workouts nicht verboten (HTTP $code) — Trainings waeren faelschbar" ;;
esac

code="$(status POST "$BASE_URL/rest/v1/xp_events" \
    -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" \
    -d "{\"user_id\":\"$UID_A\",\"source\":\"cheat\",\"xp_amount\":999999}")"
case "$code" in
    401 | 403) pass "Client-INSERT in xp_events ist verboten (HTTP $code)" ;;
    *) fail "Client-INSERT in xp_events nicht verboten (HTTP $code) — EP-Audit waere faelschbar" ;;
esac

# 8) Serverseitige EP-Vergabe: A erfasst ueber die RPC ein Krafttraining (5x5 @ 60 kg).
rpc_resp="$(curl -s -X POST "$BASE_URL/rest/v1/rpc/log_strength_workout" \
    -H "apikey: $ANON_KEY" -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" \
    -d '{"p_exercise":"Kniebeuge","p_sets":5,"p_reps":5,"p_weight_kg":60}')"
awarded="$(echo "$rpc_resp" | jq -r '.xp_awarded')"
[ "$awarded" = "40" ] || fail "RPC vergab nicht 40 EP (5*5*(100+60)/100): $rpc_resp"
pass "RPC log_strength_workout vergibt serverseitig 40 EP"

rows="$(auth_get "$TOKEN_A" "?select=total_xp,strength")"
xp="$(echo "$rows" | jq -r '.[0].total_xp')"
str="$(echo "$rows" | jq -r '.[0].strength')"
[ "$xp" = "40" ] && [ "$str" = "1" ] || fail "total_xp/strength falsch (xp=$xp str=$str, erwartet 40/1)"
pass "total_xp=40 und strength=1 wurden serverseitig gesetzt"

# Audit-Trail: genau eine Trainings- und eine EP-Event-Zeile fuer A.
w="$(auth_get_table "$TOKEN_A" strength_workouts "?select=xp_awarded")"
[ "$(echo "$w" | array_len)" = "1" ] || fail "A sollte genau 1 Training sehen: $w"
[ "$(echo "$w" | jq -r '.[0].xp_awarded')" = "40" ] || fail "Trainingszeile hat falsche xp_awarded: $w"
e="$(auth_get_table "$TOKEN_A" xp_events "?select=source,xp_amount")"
[ "$(echo "$e" | array_len)" = "1" ] || fail "A sollte genau 1 EP-Event sehen: $e"
[ "$(echo "$e" | jq -r '.[0].source')" = "strength_workout" ] || fail "EP-Event hat falsche source: $e"
pass "Audit-Trail: je eine Zeile in strength_workouts (xp_awarded=40) und xp_events (strength_workout)"

# 9) Unplausible Eingabe wird serverseitig abgelehnt; total_xp bleibt unveraendert.
code="$(status POST "$BASE_URL/rest/v1/rpc/log_strength_workout" \
    -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" \
    -d '{"p_exercise":"Kniebeuge","p_sets":5,"p_reps":0,"p_weight_kg":60}')"
case "$code" in
    2*) fail "Unplausibles Training (0 Wdh.) wurde NICHT abgelehnt (HTTP $code)" ;;
    *) pass "Unplausibles Training (0 Wdh.) wird serverseitig abgelehnt (HTTP $code)" ;;
esac
xp="$(auth_get "$TOKEN_A" "?select=total_xp" | jq -r '.[0].total_xp')"
[ "$xp" = "40" ] || fail "total_xp nach abgelehntem Training veraendert (=$xp, erwartet 40)"
pass "Abgelehntes Training liess total_xp unveraendert (40)"

# 10) B kann A's Trainings/EP-Events nicht lesen (RLS-select-own).
w="$(auth_get_table "$TOKEN_B" strength_workouts "?user_id=eq.$UID_A")"
[ "$(echo "$w" | array_len)" = "0" ] || fail "B konnte A's Trainings lesen: $w"
e="$(auth_get_table "$TOKEN_B" xp_events "?user_id=eq.$UID_A")"
[ "$(echo "$e" | array_len)" = "0" ] || fail "B konnte A's EP-Events lesen: $e"
pass "B kann A's Trainings/EP-Events nicht lesen (RLS-select blockiert)"

echo "✅ Alle RLS-Negativtests bestanden."
