#!/usr/bin/env bash

set -euo pipefail

# Combined smoke test runner:
# 1) Seeds a fresh local dev DB (DESTRUCTIVE)
# 2) Runs the auth endpoint smoke tests (JWT + Basic auth) against a running API
#
# If START_API=1, this script will start the API via Maven using the current env
# (typically .env.test), wait for /actuator/health, run auth tests, then stop the API.
#
# Note: this script does NOT start the API for you. Start it separately:
# - make run-api
# - or: make compose-up-app

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

echo "=========================================="
echo "Ligitabl Combined Smoke Tests"
echo "- Seeding: $SCRIPT_DIR/TestSeeding.sh"
echo "- Auth:    $SCRIPT_DIR/TestAuth.sh"
echo "=========================================="
echo ""

"$SCRIPT_DIR/TestSeeding.sh"
echo ""

START_API="${START_API:-0}"
API_PID=""

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

wait_for_api() {
  local url="${BASE_URL%/}/actuator/health"
  local tries=60
  local i

  for i in $(seq 1 "$tries"); do
    code="$(curl -s -o /dev/null -w "%{http_code}" "$url" || true)"
    if [[ "$code" == "200" ]]; then
      echo "API is ready ($url)"
      return 0
    fi
    sleep 1
  done

  echo "API did not become ready in time ($url)" >&2
  return 1
}

cleanup() {
  if [[ -n "${API_PID:-}" ]]; then
    kill "$API_PID" >/dev/null 2>&1 || true
    wait "$API_PID" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

if [[ "$START_API" == "1" ]]; then
  require_cmd mvn
  require_cmd curl

  echo "Starting API (spring-boot:run) on PORT=${PORT:-8080}..."
  (cd "$REPO_ROOT" && mvn -q -pl api -am spring-boot:run) &
  API_PID="$!"

  wait_for_api
fi

"$SCRIPT_DIR/TestAuth.sh"
