#!/usr/bin/env bash
# Wendet alle Migrationen aus backend/migrations/ in numerischer Reihenfolge an.
# Idempotent: die Migrationen selbst sind wiederholbar (IF NOT EXISTS / OR REPLACE),
# sodass ein erneuter Lauf auf derselben DB folgenlos bleibt (CI prueft das).
#
# Nutzung (aus dem Repo-Root, Stack laeuft via docker compose):
#   backend/scripts/migrate.sh
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-backend/docker-compose.yml}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-backend/migrations}"

shopt -s nullglob
files=("$MIGRATIONS_DIR"/*.sql)
shopt -u nullglob

if [ ${#files[@]} -eq 0 ]; then
    echo "Keine Migrationen in $MIGRATIONS_DIR gefunden."
    exit 0
fi

# Nach Dateiname sortieren (0001_, 0002_, …).
IFS=$'\n' files=($(sort <<<"${files[*]}")); unset IFS

for file in "${files[@]}"; do
    echo "→ Wende Migration an: $file"
    docker compose -f "$COMPOSE_FILE" exec -T db \
        psql -v ON_ERROR_STOP=1 -U postgres -d postgres < "$file"
done

echo "Alle Migrationen angewendet."
