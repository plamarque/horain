#!/usr/bin/env bash
# Export eval candidates from the **production** backend (turns with thumbs-down or tool/empty errors).
# Calls GET /admin/export-eval-candidates on the prod API. Does not use local DB or backend/.env.
#
# Required env:
#   HORAIN_PROD_URL     - Base URL of the prod API (e.g. https://horain-xxx.run.app)
#   HORAIN_PROD_API_KEY - API key for prod (Bearer token)
#
# Output: scripts/out/eval-candidates.jsonl (or set OUT_FILE).
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$SCRIPT_DIR/out"
OUT_FILE="${OUT_FILE:-$OUT_DIR/eval-candidates.jsonl}"

if [ -z "${HORAIN_PROD_URL:-}" ] || [ -z "${HORAIN_PROD_API_KEY:-}" ]; then
  echo "Error: HORAIN_PROD_URL and HORAIN_PROD_API_KEY must be set." >&2
  echo "Example: HORAIN_PROD_URL=https://horain-xxx.run.app HORAIN_PROD_API_KEY=your-key $0" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
URL="${HORAIN_PROD_URL%/}/admin/export-eval-candidates"

echo "Fetching eval candidates from $URL ..."
if ! curl -sS -f -H "Authorization: Bearer $HORAIN_PROD_API_KEY" "$URL" -o "$OUT_FILE"; then
  echo "Error: curl failed (check URL and API key)." >&2
  exit 1
fi

echo "Done. Output: $OUT_FILE"
