#!/usr/bin/env bash

set -euo pipefail

#+#+#+#+#+#+#+#+#+#+#+#+#+#+#+#+#+#+#+#+
# Ligitabl Import Smoke Test Script
#
# This is a DB-level smoke test that:
# - resets the local dev DB (DESTRUCTIVE)
# - runs Liquibase migrations
# - seeds reference data (assumed stable)
# - runs importer workflow for Premier League via `make import-pl`
# - asserts imported matches exist and are wired to a round+season and have a slug
#
# Notes:
# - Uses Football-Data live API via API_FOOTBALL_DATA_KEY (preferred) / FOOTBALL_DATA_API_TOKEN.
# - Uses the competition matches endpoint, so it should be stable (not tied to a narrow date window).

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

# Load test env only (DESTRUCTIVE script; never use .env).
# Optionally load secrets from .env.test.local (ignored by git).
if [[ ! -f "$REPO_ROOT/.env.test" ]]; then
  echo "Error: .env.test not found at $REPO_ROOT/.env.test" >&2
  echo "Refusing to run a destructive script without .env.test." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source "$REPO_ROOT/.env.test"

if [[ -f "$REPO_ROOT/.env.test.local" ]]; then
  # shellcheck disable=SC1091
  source "$REPO_ROOT/.env.test.local"
fi
set +a

# Ensure docker compose does not read `.env` at all.
# - `--env-file .env.test` disables the default `.env` loading for interpolation.
# - An override file ensures services don't reference `.env` via `env_file:`.
COMPOSE_OVERRIDE_FILE="$(mktemp -t ligitabl-compose-test-env-XXXXXX.yml)"
cleanup() {
  rm -f "$COMPOSE_OVERRIDE_FILE" || true
}
trap cleanup EXIT

cat >"$COMPOSE_OVERRIDE_FILE" <<'YAML'
services:
  db:
    env_file:
      - .env.test
  app:
    env_file:
      - .env.test
YAML

DOCKER_COMPOSE_CMD="docker compose --env-file .env.test -f docker-compose.yml -f $COMPOSE_OVERRIDE_FILE"

COMP="PL"

# Prefer API_FOOTBALL_DATA_KEY (matches existing .env.test convention) but support the old var.
FOOTBALL_DATA_API_TOKEN="${FOOTBALL_DATA_API_TOKEN:-${API_FOOTBALL_DATA_KEY:-}}"

if [[ -z "${FOOTBALL_DATA_API_TOKEN:-}" || "${FOOTBALL_DATA_API_TOKEN:-}" == "your-api-token-here" ]]; then
  echo "Error: Football-Data API key is not set" >&2
  echo "Set API_FOOTBALL_DATA_KEY in .env.test.local (preferred) or set FOOTBALL_DATA_API_TOKEN in .env.test.local" >&2
  exit 1
fi

DB_CONTAINER="${DB_CONTAINER:-ligitabl-db}"
DB_NAME="${DB_NAME:-ligitabl}"
DB_USER="${DB_USER:-ligitabl}"

# Makefile includes `.env` (if present), which can override `.env.test` values.
# Use command-line make variables (highest precedence) to keep DB settings consistent.
HOST_DB_PORT="${HOST_DB_PORT:-${DB_PORT:-55433}}"
DB_HOST="${DB_HOST:-localhost}"
DB_PASSWORD="${DB_PASSWORD:-$DB_USER}"

MAKE_DB_VARS=(
  DOCKER_COMPOSE="$DOCKER_COMPOSE_CMD"
  HOST_DB_PORT="$HOST_DB_PORT"
  DB_HOST="$DB_HOST"
  DB_PORT="$HOST_DB_PORT"
  DB_NAME="$DB_NAME"
  DB_USER="$DB_USER"
  DB_PASSWORD="$DB_PASSWORD"
  FOOTBALL_DATA_API_TOKEN="$FOOTBALL_DATA_API_TOKEN"
)

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo -e "${RED}Missing required command: $1${NC}" >&2
    exit 1
  }
}

psql_scalar() {
  local sql="$1"
  docker exec -i "$DB_CONTAINER" \
    psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -tAc "$sql" \
    | tr -d '\r\n' | sed 's/[[:space:]]//g'
}

assert_eq() {
  local actual="$1"
  local expected="$2"
  local label="$3"

  if [[ "$actual" != "$expected" ]]; then
    echo -e "${RED}FAIL${NC} $label (expected $expected, got $actual)"
    return 1
  fi
  echo -e "${GREEN}OK${NC}   $label ($actual)"
}

assert_gt() {
  local actual="$1"
  local threshold="$2"
  local label="$3"

  if ! [[ "$actual" =~ ^[0-9]+$ ]]; then
    echo -e "${RED}FAIL${NC} $label (not a number: $actual)"
    return 1
  fi

  if (( actual <= threshold )); then
    echo -e "${RED}FAIL${NC} $label (expected > $threshold, got $actual)"
    return 1
  fi
  echo -e "${GREEN}OK${NC}   $label ($actual)"
}

require_cmd docker
require_cmd make

echo "=========================================="
echo "Ligitabl Import Smoke Test"
echo "- Competition: $COMP"
echo "DB_CONTAINER=$DB_CONTAINER"
echo "DB_NAME=$DB_NAME"
echo "DB_USER=$DB_USER"
echo "=========================================="
echo ""

echo -e "${BLUE}Step 1: Start DB (docker compose)${NC}"
(cd "$REPO_ROOT" && make "${MAKE_DB_VARS[@]}" compose-up-db)

echo -e "${BLUE}Step 2: Reset DB (DESTRUCTIVE)${NC}"
(cd "$REPO_ROOT" && make "${MAKE_DB_VARS[@]}" reset-db)

echo -e "${BLUE}Step 3: Run migrations${NC}"
(cd "$REPO_ROOT" && make "${MAKE_DB_VARS[@]}" migrate)

echo -e "${BLUE}Step 4: Seed reference data (seeding/main.yaml)${NC}"
(cd "$REPO_ROOT" && make "${MAKE_DB_VARS[@]}" db-seed)

echo -e "${BLUE}Step 5: Run importer (make import-pl)${NC}"
(cd "$REPO_ROOT" && make "${MAKE_DB_VARS[@]}" import-pl)

echo -e "${BLUE}Step 6: Validate imported matches${NC}"

TOTAL_MATCHES="$(psql_scalar "select count(*) from t_match;")"
assert_gt "$TOTAL_MATCHES" "0" "at least one match imported"

WIRED_MATCHES="$(psql_scalar "select count(*) from t_match m join t_round r on r.pk_id = m.fk_round_id where r.fk_season_id is not null and m.c_slug is not null and length(trim(m.c_slug)) > 0;")"
assert_eq "$WIRED_MATCHES" "$TOTAL_MATCHES" "all matches have round+season wiring and slug"

echo "=========================================="
echo -e "${GREEN}Import smoke test passed.${NC}"
echo "=========================================="
