#!/usr/bin/env bash
# Export eval candidates from the **production** backend (turns with thumbs-down or tool/empty errors).
# Calls GET /admin/export-eval-candidates on the prod API. Does not use local DB or backend/.env.
#
# Required env:
#   EVAL_CANDIDATES_ENDPOINT - Full URL of the export endpoint (e.g. https://horain-xxx.run.app/admin/export-eval-candidates)
#   HORAIN_API_KEY          - API key (Bearer) for that instance
#
# Output: scripts/out/eval-candidates.jsonl (or set OUT_FILE).
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$SCRIPT_DIR/out"
OUT_FILE="${OUT_FILE:-$OUT_DIR/eval-candidates.jsonl}"

if [ -z "${EVAL_CANDIDATES_ENDPOINT:-}" ] || [ -z "${HORAIN_API_KEY:-}" ]; then
  echo "Error: EVAL_CANDIDATES_ENDPOINT and HORAIN_API_KEY must be set." >&2
  echo "Example: EVAL_CANDIDATES_ENDPOINT=https://horain-xxx.run.app/admin/export-eval-candidates HORAIN_API_KEY=your-key $0" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"

echo "Fetching eval candidates from $EVAL_CANDIDATES_ENDPOINT ..."
if ! curl -sS -f -H "Authorization: Bearer $HORAIN_API_KEY" "$EVAL_CANDIDATES_ENDPOINT" -o "$OUT_FILE"; then
  echo "Error: curl failed (check URL and API key)." >&2
  exit 1
fi

echo "Done. Output: $OUT_FILE"
