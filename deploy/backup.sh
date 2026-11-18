#!/usr/bin/env bash
# Dumps the SmartFinance database to a compressed file and prunes old copies.
# Run from the deploy directory (or through smartfinance-backup.timer).
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
    echo "backup: arquivo .env não encontrado em $(pwd)" >&2
    exit 1
fi

set -a
# shellcheck disable=SC1091
. ./.env
set +a

backup_dir="${BACKUP_DIR:-/var/backups/smartfinance}"
retention_days="${BACKUP_RETENTION_DAYS:-14}"
database="${POSTGRES_DB:-smartfinance}"
user="${POSTGRES_USER:-smartfinance}"

umask 077
mkdir -p "$backup_dir"

file="$backup_dir/smartfinance-$(date -u +%Y%m%dT%H%M%SZ).dump"
partial="$file.part"
trap 'rm -f "$partial"' EXIT

docker compose exec -T db pg_dump -U "$user" -d "$database" --format=custom > "$partial"

if [[ ! -s "$partial" ]]; then
    echo "backup: dump vazio" >&2
    exit 1
fi

chmod 600 "$partial"
mv "$partial" "$file"
trap - EXIT
find "$backup_dir" -maxdepth 1 -type f -name 'smartfinance-*.dump' \
    -mtime "+$retention_days" -delete

echo "backup: $file"
