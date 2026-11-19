#!/usr/bin/env bash
# Creates a logical PostgreSQL backup of the Railway service through the
# Railway CLI private tunnel, validates the archive, writes a checksum and
# prunes old copies. No database port is published.
#
# It expects a linked Railway project and an authenticated CLI:
#   railway login
#   railway link
#
# Optional environment variables:
#   RAILWAY_BIN             Railway CLI path (default: railway or ~/.railway/bin/railway)
#   RAILWAY_SERVICE         Database service name (default: Postgres)
#   RAILWAY_ENVIRONMENT     Railway environment (default: linked environment)
#   RAILWAY_TUNNEL_PORT     Local tunnel port (default: 5433)
#   BACKUP_DIR              Output directory (default: ~/backups/smartfinance)
#   BACKUP_RETENTION_DAYS   Delete dumps older than this (default: 30)
set -euo pipefail

usage() {
    cat <<'EOF'
Creates a logical PostgreSQL backup of the Railway service through the
Railway CLI private tunnel, validates the archive, writes a checksum and
prunes old copies. No database port is published.

It expects a linked Railway project and an authenticated CLI:
  railway login
  railway link

Optional environment variables:
  RAILWAY_BIN             Railway CLI path (default: railway or ~/.railway/bin/railway)
  RAILWAY_SERVICE         Database service name (default: Postgres)
  RAILWAY_ENVIRONMENT     Railway environment (default: linked environment)
  RAILWAY_TUNNEL_PORT     Local tunnel port (default: 5433)
  BACKUP_DIR              Output directory (default: ~/backups/smartfinance)
  BACKUP_RETENTION_DAYS   Delete dumps older than this (default: 30)
EOF
}

case "${1:-}" in
    -h|--help)
        usage
        exit 0
        ;;
    "")
        ;;
    *)
        echo "backup: argumento desconhecido: $1" >&2
        usage >&2
        exit 2
        ;;
esac

if [[ -n "${RAILWAY_BIN:-}" ]]; then
    railway_bin="$RAILWAY_BIN"
elif command -v railway >/dev/null 2>&1; then
    railway_bin="$(command -v railway)"
elif [[ -x "$HOME/.railway/bin/railway" ]]; then
    railway_bin="$HOME/.railway/bin/railway"
else
    echo "backup: Railway CLI não encontrado; instale e rode railway login" >&2
    exit 1
fi

if [[ ! -x "$railway_bin" ]]; then
    echo "backup: RAILWAY_BIN não é executável: $railway_bin" >&2
    exit 1
fi

for command in pg_dump pg_restore psql python3 setsid; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "backup: comando obrigatório não encontrado: $command" >&2
        exit 1
    fi
done

service="${RAILWAY_SERVICE:-Postgres}"
environment="${RAILWAY_ENVIRONMENT:-}"
backup_dir="${BACKUP_DIR:-$HOME/backups/smartfinance}"
retention_days="${BACKUP_RETENTION_DAYS:-30}"
tunnel_port="${RAILWAY_TUNNEL_PORT:-5433}"

if [[ ! "$retention_days" =~ ^[0-9]+$ ]]; then
    echo "backup: BACKUP_RETENTION_DAYS deve ser um número inteiro" >&2
    exit 1
fi

if command -v ss >/dev/null 2>&1 \
    && ss -ltn "sport = :$tunnel_port" 2>/dev/null | grep -q LISTEN; then
    echo "backup: porta local $tunnel_port já está em uso; defina RAILWAY_TUNNEL_PORT" >&2
    exit 1
fi

umask 077
mkdir -p "$backup_dir"
chmod 700 "$backup_dir" 2>/dev/null || true

if command -v flock >/dev/null 2>&1; then
    exec 9>"$backup_dir/.railway-backup.lock"
    if ! flock -n 9; then
        echo "backup: outra execução está em andamento" >&2
        exit 1
    fi
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
file="$backup_dir/smartfinance-$timestamp.dump"
partial="$file.part"
checksum="$file.sha256"
error_log="$(mktemp "${TMPDIR:-/tmp}/smartfinance-backup.XXXXXX")"
chmod 600 "$error_log"
tunnel_pid=""

cleanup() {
    if [[ -n "$tunnel_pid" ]]; then
        kill -- -"$tunnel_pid" 2>/dev/null || true
        kill "$tunnel_pid" 2>/dev/null || true
        wait "$tunnel_pid" 2>/dev/null || true
    fi
    # railway connect detaches the ssh child, so also close the local listener.
    # The port was checked as free before the tunnel started.
    if command -v fuser >/dev/null 2>&1; then
        fuser -k -n tcp "$tunnel_port" >/dev/null 2>&1 || true
    elif command -v ss >/dev/null 2>&1; then
        local pids
        pids="$(ss -ltnp "sport = :$tunnel_port" 2>/dev/null \
            | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | sort -u)"
        for pid in $pids; do
            kill "$pid" 2>/dev/null || true
        done
    fi
    rm -f "$partial" "$error_log"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

variable_args=(variable list --service "$service" --json)
tunnel_args=(connect "$service" --ssh --tunnel-only -P "$tunnel_port")
if [[ -n "$environment" ]]; then
    variable_args+=(--environment "$environment")
    tunnel_args+=(--environment "$environment")
fi

if ! variables_json="$("$railway_bin" "${variable_args[@]}" 2>"$error_log")"; then
    echo "backup: não foi possível ler as variáveis do serviço $service" >&2
    echo "backup: confira railway login, railway link e o nome do serviço" >&2
    exit 1
fi

mapfile -d '' -t db_fields < <(
    printf '%s' "$variables_json" | python3 -c '
import json
import sys

data = json.load(sys.stdin)
for key in ("PGUSER", "PGPASSWORD", "PGDATABASE"):
    value = data.get(key)
    if value is None:
        raise SystemExit(f"missing variable: {key}")
    sys.stdout.write(str(value))
    sys.stdout.write("\0")
'
)

if [[ "${#db_fields[@]}" -ne 3 ]]; then
    echo "backup: variáveis PGUSER, PGPASSWORD ou PGDATABASE ausentes no serviço $service" >&2
    exit 1
fi

pguser="${db_fields[0]}"
pgpassword="${db_fields[1]}"
pgdatabase="${db_fields[2]}"

setsid "$railway_bin" "${tunnel_args[@]}" >/dev/null 2>"$error_log" &
tunnel_pid=$!

ready=0
for _ in $(seq 1 60); do
    if ! kill -0 "$tunnel_pid" 2>/dev/null; then
        echo "backup: o túnel encerrou antes de ficar pronto" >&2
        echo "backup: execute railway connect $service --ssh --tunnel-only -P $tunnel_port para diagnosticar" >&2
        exit 1
    fi
    if PGPASSWORD="$pgpassword" psql \
        -h 127.0.0.1 -p "$tunnel_port" -U "$pguser" -d "$pgdatabase" \
        -c 'select 1' >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 1
done

if [[ "$ready" != 1 ]]; then
    echo "backup: o banco não ficou acessível pelo túnel em 60 segundos" >&2
    exit 1
fi

if ! PGPASSWORD="$pgpassword" pg_dump \
    -h 127.0.0.1 -p "$tunnel_port" -U "$pguser" -d "$pgdatabase" \
    --format=custom --no-owner --no-privileges --file="$partial"; then
    echo "backup: pg_dump falhou" >&2
    exit 1
fi

if [[ ! -s "$partial" ]]; then
    echo "backup: dump vazio" >&2
    exit 1
fi

if ! pg_restore --list "$partial" >/dev/null; then
    echo "backup: dump não pôde ser lido pelo pg_restore" >&2
    exit 1
fi

chmod 600 "$partial"
mv "$partial" "$file"
(
    cd "$backup_dir"
    sha256sum "$(basename "$file")" > "$(basename "$checksum")"
)
chmod 600 "$checksum"

removed="$(find "$backup_dir" -maxdepth 1 -type f -name 'smartfinance-*.dump' \
    -mtime "+$retention_days" -delete -print | wc -l)"
if [[ "$removed" -gt 0 ]]; then
    echo "backup: $removed dump(s) com mais de $retention_days dias removido(s)"
fi
find "$backup_dir" -maxdepth 1 -type f -name 'smartfinance-*.dump.sha256' \
    -mtime "+$retention_days" -delete

echo "backup: $file"
echo "backup: tamanho $(du -h "$file" | cut -f1)"
echo "backup: sha256 $(cut -d' ' -f1 "$checksum")"
