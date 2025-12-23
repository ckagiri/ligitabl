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
make compose-up-db

echo -e "${BLUE}Step 2: Reset DB (DESTRUCTIVE)${NC}"
make reset-db

echo -e "${BLUE}Step 3: Run migrations${NC}"
make migrate

echo -e "${BLUE}Step 4: Seed reference data (seeding/main.yaml)${NC}"
make db-seed

echo -e "${BLUE}Step 5: Validate expected seeded state${NC}"

# Defaults applied
assert_eq "$(psql_scalar "select count(*) from t_competition where fk_active_season_id is not null;")" "1" "competition has active season"
assert_eq "$(psql_scalar "select count(*) from t_season s join t_round r on r.pk_id = s.fk_current_round_id where r.c_position = 1;")" "1" "season current round is position 1"

# Contest + season wiring
assert_eq "$(psql_scalar "select count(*) from t_contest;")" "1" "main contest created"
assert_eq "$(psql_scalar "select count(*) from t_season where fk_main_contest_id is not null;")" "1" "season.main_contest_id set"

# Initial standings
assert_eq "$(psql_scalar "select count(*) from t_standings where c_round_position = 1;")" "1" "round 1 standings row exists"
assert_eq "$(psql_scalar "select count(*) from t_standings where c_round_position = 1 and c_finalised = false and c_finalised_at is null;")" "1" "round 1 standings not finalised"
assert_eq "$(psql_scalar "select (select jsonb_array_length(c_rankings) from t_standings where c_round_position = 1 limit 1) = (select c_total_teams from t_season limit 1);")" "t" "standings rankings length matches season total teams"

echo "=========================================="
echo -e "${GREEN}Seeding smoke test passed.${NC}"
echo "=========================================="
