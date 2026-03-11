#!/usr/bin/env bash
# Export eval candidates (turns with thumbs-down or tool/empty errors) to JSONL.
# Requires backend datasource to point at a DB with agent_turn/agent_feedback data
# (e.g. Supabase). Output: scripts/out/eval-candidates.jsonl (or pass --export.output=path).
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$SCRIPT_DIR/out"
OUT_FILE="$OUT_DIR/eval-candidates.jsonl"

mkdir -p "$OUT_DIR"
cd "$ROOT_DIR/backend"

# Run Spring Boot with profile "export"; the ExportEvalCandidatesRunner writes JSONL and exits.
mvn spring-boot:run \
  -Dspring-boot.run.profiles=export \
  -Dspring-boot.run.arguments="--export.output=$OUT_FILE"

echo "Done. Output: $OUT_FILE"
