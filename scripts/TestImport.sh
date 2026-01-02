#!/usr/bin/env bash

set -euo pipefail

# Import smoke runner:
# - Assumes the DB is already migrated and seeded with reference data.
# - Runs the importer workflow against Football-Data using FOOTBALL_DATA_API_TOKEN.
#
# Usage:
#   ./scripts/TestImport.sh PL
#   COMP=PL ./scripts/TestImport.sh

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

# Load test env (preferred) or dev env (fallback) if present.
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

COMP_ARG="${1:-${COMP:-}}"
if [[ -z "$COMP_ARG" ]]; then
  echo "Error: competition code required" >&2
  echo "Usage: ./scripts/TestImport.sh PL" >&2
  echo "   or: COMP=PL ./scripts/TestImport.sh" >&2
  exit 1
fi

if [[ -z "${FOOTBALL_DATA_API_TOKEN:-}" || "${FOOTBALL_DATA_API_TOKEN:-}" == "your-api-token-here" ]]; then
  echo "Error: FOOTBALL_DATA_API_TOKEN is not set" >&2
  echo "Set it in .env/.env.test or export FOOTBALL_DATA_API_TOKEN=..." >&2
  exit 1
fi

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

require_cmd make
require_cmd java

echo "=========================================="
echo "Ligitabl Import Smoke Test"
echo "- Competition: $COMP_ARG"
echo "- Assumes DB is already seeded"
echo "=========================================="
echo ""

(cd "$REPO_ROOT" && make compose-up-db)
(cd "$REPO_ROOT" && make api-build)

JAR_PATH="$(ls -1 "$REPO_ROOT"/api/target/*.jar 2>/dev/null | grep -v '\.original$' | head -n 1 || true)"
if [[ -z "$JAR_PATH" || ! -f "$JAR_PATH" ]]; then
  echo "Error: could not find built API jar in api/target" >&2
  exit 1
fi

echo "Running importer workflow..."
java -jar "$JAR_PATH" \
  --workflow.run=true \
  --workflow.competition="$COMP_ARG" \
  --workflow.exit-after=true
