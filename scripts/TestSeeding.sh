#!/usr/bin/env bash

set -euo pipefail

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

# Ligitabl Seeding Smoke Test Script
#
# This is a DB-level smoke test that:
# - resets the local dev DB (DESTRUCTIVE)
# - runs Liquibase migrations
# - runs reference seeding (seeding/main.yaml)
# - asserts the expected contest + initial standings exist

DB_CONTAINER="${DB_CONTAINER:-ligitabl-db}"
DB_NAME="${DB_NAME:-ligitabl}"
DB_USER="${DB_USER:-ligitabl}"

# Makefile includes `.env` (if present), which can override `.env.test` values.
# Use command-line make variables (highest precedence) to keep DB settings consistent.
HOST_DB_PORT="${HOST_DB_PORT:-${DB_PORT:-55433}}"
DB_HOST="${DB_HOST:-localhost}"
DB_PASSWORD="${DB_PASSWORD:-$DB_USER}"

MAKE_DB_VARS=(
  HOST_DB_PORT="$HOST_DB_PORT"
  DB_HOST="$DB_HOST"
  DB_PORT="$HOST_DB_PORT"
  DB_NAME="$DB_NAME"
  DB_USER="$DB_USER"
  DB_PASSWORD="$DB_PASSWORD"
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

echo "=========================================="
echo "Ligitabl Seeding Smoke Test"
echo "DB_CONTAINER=$DB_CONTAINER"
echo "DB_NAME=$DB_NAME"
echo "DB_USER=$DB_USER"
echo "=========================================="
echo ""

require_cmd docker
require_cmd make

echo -e "${BLUE}Step 1: Start DB (docker compose)${NC}"
make "${MAKE_DB_VARS[@]}" compose-up-db

echo -e "${BLUE}Step 2: Reset DB (DESTRUCTIVE)${NC}"
make "${MAKE_DB_VARS[@]}" reset-db

echo -e "${BLUE}Step 3: Run migrations${NC}"
make "${MAKE_DB_VARS[@]}" migrate

echo -e "${BLUE}Step 4: Seed reference data (seeding/main.yaml)${NC}"
make "${MAKE_DB_VARS[@]}" db-seed

echo -e "${BLUE}Step 5: Validate expected seeded state${NC}"

# Defaults applied
assert_eq "$(psql_scalar "select count(*) from t_competition where fk_active_season_id is not null;")" "1" "competition has active season"
assert_eq "$(psql_scalar "select count(*) from t_season where c_current_match_day = 1;")" "1" "season current matchday is 1"
assert_eq "$(psql_scalar "select count(*) from t_season s join t_round r on r.pk_id = s.fk_current_round_id where r.c_position = 1;")" "1" "season current round FK is matchday 1"

# Contest + season wiring
assert_eq "$(psql_scalar "select count(*) from t_contest;")" "1" "main contest created"
assert_eq "$(psql_scalar "select count(*) from t_season where fk_main_contest_id is not null;")" "1" "season.main_contest_id set"

# Initial standings
assert_eq "$(psql_scalar "select count(*) from t_standings where c_round_position = 1;")" "1" "matchday 1 standings row exists"
assert_eq "$(psql_scalar "select count(*) from t_standings where c_round_position = 1 and c_finalised = false and c_finalised_at is null;")" "1" "matchday 1 standings not finalised"
assert_eq "$(psql_scalar "select (select jsonb_array_length(c_rankings) from t_standings where c_round_position = 1 limit 1) = (select c_total_teams from t_season limit 1);")" "t" "standings rankings length matches season total teams"

echo "=========================================="
echo -e "${GREEN}Seeding smoke test passed.${NC}"
echo "=========================================="
