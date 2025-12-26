#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

# Load test env (preferred) or dev env (fallback) if present.
# Use `set -a` so variables are exported to child processes (make/docker/psql).
if [[ -f "$REPO_ROOT/.env.test" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$REPO_ROOT/.env.test"
  set +a
elif [[ -f "$REPO_ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$REPO_ROOT/.env"
  set +a
fi

DB_NAME="${DB_NAME:-ligitabl}"
DB_USER="${DB_USER:-ligitabl}"
DB_PASSWORD="${DB_PASSWORD:-$DB_USER}"
DB_HOST="${DB_HOST:-localhost}"
HOST_DB_PORT="${HOST_DB_PORT:-${DB_PORT:-55432}}"

MAKE_DB_VARS=(
  HOST_DB_PORT="$HOST_DB_PORT"
  DB_HOST="$DB_HOST"
  DB_PORT="$HOST_DB_PORT"
  DB_NAME="$DB_NAME"
  DB_USER="$DB_USER"
  DB_PASSWORD="$DB_PASSWORD"
)

echo "=========================================="
echo "Demo seeding + seed-season runner"
echo "DB_NAME=$DB_NAME"
echo "DB_USER=$DB_USER"
echo "DB_HOST=$DB_HOST"
echo "DB_PORT=$HOST_DB_PORT"
echo "competitionSlug=super-premier-league"
echo "seasonSlug=2024-25"
echo "=========================================="
echo ""

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

require_cmd make

echo "[1/4] Start DB (docker compose)"
make "${MAKE_DB_VARS[@]}" compose-up-db

echo "[2/4] Reset DB (DESTRUCTIVE)"
make "${MAKE_DB_VARS[@]}" reset-db

echo "[3/4] Run migrations"
make "${MAKE_DB_VARS[@]}" migrate

echo "[4/4] Seed demo-data then seed-season"
make "${MAKE_DB_VARS[@]}" db-seed-demo

echo ""
echo "=========================================="
echo "Done. Demo league + season extras seeded."
echo "=========================================="
