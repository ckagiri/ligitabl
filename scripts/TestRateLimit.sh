#!/usr/bin/env bash
# Smoke test for the Bucket4j rate limiting filter.
#
# Fires 25 requests at the API and asserts:
#   - The first 20 are NOT 429
#   - At least one of the last 5 IS 429
#
# Usage:
#   ./scripts/TestRateLimit.sh
#   BASE_URL=http://staging.example.com ./scripts/TestRateLimit.sh
#
# Assumes the server is already running.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

BASE_URL="${BASE_URL:-http://localhost:8080}"
TARGET_PATH="${TARGET_PATH:-/api/status}"
TOTAL_REQUESTS=25
LIMIT=20

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo -e "${RED}Missing required command: $1${NC}" >&2
    exit 1
  }
}

require_cmd curl

echo "=========================================="
echo "LigiTabl Rate Limit Smoke Test"
echo "BASE_URL=$BASE_URL"
echo "TARGET=$TARGET_PATH"
echo "Firing $TOTAL_REQUESTS requests (limit=$LIMIT/min)"
echo "=========================================="
echo ""

PASS=0
FAIL=0
THROTTLED=0

for i in $(seq 1 $TOTAL_REQUESTS); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$TARGET_PATH")

  if [[ "$i" -le "$LIMIT" ]]; then
    # First LIMIT requests must not be throttled
    if [[ "$STATUS" == "429" ]]; then
      echo -e "${RED}FAIL${NC}  Request $i: expected non-429, got $STATUS (throttled too early)"
      FAIL=$((FAIL + 1))
    else
      echo -e "${GREEN}OK${NC}    Request $i: $STATUS"
      PASS=$((PASS + 1))
    fi
  else
    # Requests beyond the limit should eventually be throttled
    if [[ "$STATUS" == "429" ]]; then
      echo -e "${YELLOW}THROTTLED${NC} Request $i: $STATUS (expected)"
      THROTTLED=$((THROTTLED + 1))
    else
      echo -e "${GREEN}OK${NC}    Request $i: $STATUS (bucket may have refilled)"
    fi
  fi
done

echo ""
echo "=========================================="
echo "Results"
echo "  Passed (non-429 within limit): $PASS / $LIMIT"
echo "  Throttled (429 beyond limit):  $THROTTLED / $((TOTAL_REQUESTS - LIMIT))"
echo "  Early failures:                $FAIL"
echo "=========================================="

if [[ "$FAIL" -gt 0 ]]; then
  echo -e "${RED}FAILED: $FAIL request(s) were throttled before the limit was reached.${NC}"
  exit 1
fi

if [[ "$THROTTLED" -eq 0 ]]; then
  echo -e "${RED}FAILED: No requests were throttled after the limit. Is the filter active?${NC}"
  exit 1
fi

echo -e "${GREEN}All checks passed.${NC}"
