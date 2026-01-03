#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

# Load test env only (test script; never use .env).
# Optionally load secrets from .env.test.local (ignored by git).
if [[ ! -f "$REPO_ROOT/.env.test" ]]; then
  echo "Error: .env.test not found at $REPO_ROOT/.env.test" >&2
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

# Ligitabl Auth API Test Script
#
# This is a curl-based smoke test for the auth + role-protected endpoints.
# It assumes the API is already running and that the referenced users exist in the DB.

BASE_URL="${BASE_URL:-http://localhost:8080}"
LOGIN_PATH="${LOGIN_PATH:-/auth/login}"
ME_PATH="${ME_PATH:-/api/me}"

ADMIN_ONLY_PATH="${ADMIN_ONLY_PATH:-/api/admin}"
PLAYER_ONLY_PATH="${PLAYER_ONLY_PATH:-/api/player}"

ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin12345}"

PLAYER_EMAIL="${PLAYER_EMAIL:-player1@example.com}"
PLAYER_PASSWORD="${PLAYER_PASSWORD:-player12345}"

SUPER_EMAIL="${SUPER_EMAIL:-superuser@example.com}"
SUPER_PASSWORD="${SUPER_PASSWORD:-super12345}"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo -e "${RED}Missing required command: $1${NC}" >&2
    exit 1
  }
}

http_post_json() {
  local url="$1"
  local json="$2"
  curl -sS -X POST "$url" -H "Content-Type: application/json" -d "$json" -w "\n%{http_code}"
}

http_get() {
  local url="$1"
  local token="${2:-}"
  if [[ -n "$token" ]]; then
    curl -sS "$url" -H "Authorization: Bearer $token" -w "\n%{http_code}"
  else
    curl -sS "$url" -w "\n%{http_code}"
  fi
}

http_get_basic() {
  local url="$1"
  local username="$2"
  local password="$3"
  curl -sS "$url" -u "$username:$password" -w "\n%{http_code}"
}

split_body_status() {
  # Input: "<body>\n<status>"  Output: sets globals BODY and STATUS
  local raw="$1"
  STATUS="${raw##*$'\n'}"
  BODY="${raw%$'\n'*}"
}

extract_token() {
  # Accepts either {"token":"..."} (this repo) or {"accessToken":"..."} (some references)
  local body="$1"
  local token=""

  if command -v jq >/dev/null 2>&1; then
    token="$(echo "$body" | jq -r '.token // .accessToken // empty' 2>/dev/null || true)"
  fi

  if [[ -z "$token" ]]; then
    token="$(echo "$body" | grep -o '"token":"[^"]*' | cut -d'"' -f4 || true)"
  fi
  if [[ -z "$token" ]]; then
    token="$(echo "$body" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4 || true)"
  fi

  echo "$token"
}

assert_status() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo -e "${RED}FAIL${NC} $label (expected $expected, got $actual)"
    return 1
  fi
  echo -e "${GREEN}OK${NC}   $label ($actual)"
}

assert_status_any() {
  local actual="$1"
  local expected1="$2"
  local expected2="$3"
  local label="$4"
  if [[ "$actual" != "$expected1" && "$actual" != "$expected2" ]]; then
    echo -e "${RED}FAIL${NC} $label (expected $expected1 or $expected2, got $actual)"
    return 1
  fi
  echo -e "${GREEN}OK${NC}   $label ($actual)"
}

assert_status_any3() {
  local actual="$1"
  local expected1="$2"
  local expected2="$3"
  local expected3="$4"
  local label="$5"
  if [[ "$actual" != "$expected1" && "$actual" != "$expected2" && "$actual" != "$expected3" ]]; then
    echo -e "${RED}FAIL${NC} $label (expected $expected1/$expected2/$expected3, got $actual)"
    return 1
  fi
  echo -e "${GREEN}OK${NC}   $label ($actual)"
}

assert_status_any4() {
  local actual="$1"
  local expected1="$2"
  local expected2="$3"
  local expected3="$4"
  local expected4="$5"
  local label="$6"
  if [[ "$actual" != "$expected1" && "$actual" != "$expected2" && "$actual" != "$expected3" && "$actual" != "$expected4" ]]; then
    echo -e "${RED}FAIL${NC} $label (expected $expected1/$expected2/$expected3/$expected4, got $actual)"
    return 1
  fi
  echo -e "${GREEN}OK${NC}   $label ($actual)"
}

echo "=========================================="
echo "Ligitabl Auth API Tests"
echo "BASE_URL=$BASE_URL"
echo "LOGIN_PATH=$LOGIN_PATH"
echo "ME_PATH=$ME_PATH"
echo "ADMIN_ONLY_PATH=$ADMIN_ONLY_PATH"
echo "PLAYER_ONLY_PATH=$PLAYER_ONLY_PATH"
echo "=========================================="
echo ""

require_cmd curl

echo -e "${BLUE}Test 1: Login as Admin${NC}"
RAW="$(http_post_json "$BASE_URL$LOGIN_PATH" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")"
split_body_status "$RAW"
ADMIN_TOKEN="$(extract_token "$BODY")"
assert_status "$STATUS" "200" "admin login" || { echo "Response: $BODY"; exit 1; }
if [[ -z "$ADMIN_TOKEN" ]]; then
  echo -e "${RED}Failed to extract admin token${NC}"
  echo "Response: $BODY"
  exit 1
fi
echo -e "${GREEN}Admin token obtained${NC}"
echo ""

echo -e "${BLUE}Test 2: Login as Player${NC}"
RAW="$(http_post_json "$BASE_URL$LOGIN_PATH" "{\"email\":\"$PLAYER_EMAIL\",\"password\":\"$PLAYER_PASSWORD\"}")"
split_body_status "$RAW"
PLAYER_TOKEN="$(extract_token "$BODY")"
assert_status "$STATUS" "200" "player login" || { echo "Response: $BODY"; exit 1; }
if [[ -z "$PLAYER_TOKEN" ]]; then
  echo -e "${RED}Failed to extract player token${NC}"
  echo "Response: $BODY"
  exit 1
fi
echo -e "${GREEN}Player token obtained${NC}"
echo ""

echo -e "${BLUE}Test 3: Login as Superuser${NC}"
RAW="$(http_post_json "$BASE_URL$LOGIN_PATH" "{\"email\":\"$SUPER_EMAIL\",\"password\":\"$SUPER_PASSWORD\"}")"
split_body_status "$RAW"
SUPER_TOKEN="$(extract_token "$BODY")"
assert_status "$STATUS" "200" "superuser login" || { echo "Response: $BODY"; exit 1; }
if [[ -z "$SUPER_TOKEN" ]]; then
  echo -e "${RED}Failed to extract superuser token${NC}"
  echo "Response: $BODY"
  exit 1
fi
echo -e "${GREEN}Superuser token obtained${NC}"
echo ""

echo -e "${BLUE}Test 4: Get ME with Admin JWT${NC}"
RAW="$(http_get "$BASE_URL$ME_PATH" "$ADMIN_TOKEN")"
split_body_status "$RAW"
assert_status "$STATUS" "200" "me (admin)" || { echo "Response: $BODY"; exit 1; }
echo "Response: $BODY"
echo ""

echo -e "${BLUE}Test 4b: Get ME with Basic Auth (optional)${NC}"
RAW="$(http_get_basic "$BASE_URL$ME_PATH" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
split_body_status "$RAW"
# If httpBasic is not enabled, Spring Security will ignore the header and the endpoint
# is typically handled as anonymous (often returning 400/401). If httpBasic is enabled,
# and credentials are correct, this should be 200.
assert_status_any4 "$STATUS" "200" "400" "401" "403" "me (basic auth)" || { echo "Response: $BODY"; exit 1; }
echo "Response: $BODY"
echo ""

echo -e "${BLUE}Test 5: Access admin endpoint as Admin${NC}"
RAW="$(http_get "$BASE_URL$ADMIN_ONLY_PATH" "$ADMIN_TOKEN")"
split_body_status "$RAW"
assert_status "$STATUS" "200" "admin endpoint as admin" || { echo "Response: $BODY"; exit 1; }
echo ""

echo -e "${BLUE}Test 6: Access player endpoint as Admin (should be 403)${NC}"
RAW="$(http_get "$BASE_URL$PLAYER_ONLY_PATH" "$ADMIN_TOKEN")"
split_body_status "$RAW"
assert_status "$STATUS" "403" "player endpoint as admin" || { echo "Response: $BODY"; exit 1; }
echo ""

echo -e "${BLUE}Test 7: Access player endpoint as Player${NC}"
RAW="$(http_get "$BASE_URL$PLAYER_ONLY_PATH" "$PLAYER_TOKEN")"
split_body_status "$RAW"
assert_status "$STATUS" "200" "player endpoint as player" || { echo "Response: $BODY"; exit 1; }
echo ""

echo -e "${BLUE}Test 8: Access admin endpoint as Player (should be 403)${NC}"
RAW="$(http_get "$BASE_URL$ADMIN_ONLY_PATH" "$PLAYER_TOKEN")"
split_body_status "$RAW"
assert_status "$STATUS" "403" "admin endpoint as player" || { echo "Response: $BODY"; exit 1; }
echo ""

echo -e "${BLUE}Test 9: Access admin endpoint as Superuser${NC}"
RAW="$(http_get "$BASE_URL$ADMIN_ONLY_PATH" "$SUPER_TOKEN")"
split_body_status "$RAW"
assert_status "$STATUS" "200" "admin endpoint as superuser" || { echo "Response: $BODY"; exit 1; }
echo ""

echo -e "${BLUE}Test 10: Access player endpoint as Superuser${NC}"
RAW="$(http_get "$BASE_URL$PLAYER_ONLY_PATH" "$SUPER_TOKEN")"
split_body_status "$RAW"
assert_status "$STATUS" "200" "player endpoint as superuser" || { echo "Response: $BODY"; exit 1; }
echo ""

echo -e "${BLUE}Test 11: Login with Invalid Credentials${NC}"
RAW="$(http_post_json "$BASE_URL$LOGIN_PATH" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"wrongpassword\"}")"
split_body_status "$RAW"
assert_status "$STATUS" "401" "invalid login should be 401" || { echo "Response: $BODY"; exit 1; }
echo ""

echo -e "${BLUE}Test 12: Access ME without Authentication${NC}"
RAW="$(http_get "$BASE_URL$ME_PATH")"
split_body_status "$RAW"
assert_status_any3 "$STATUS" "400" "401" "403" "me without token should be 400/401/403" || { echo "Response: $BODY"; exit 1; }
echo ""

echo "=========================================="
echo -e "${GREEN}All tests completed successfully.${NC}"
echo "=========================================="
