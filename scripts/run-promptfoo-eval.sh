#!/bin/bash
# Run Promptfoo evals: start backend (if needed), seed DB, run evals.
# Usage: ./scripts/run-promptfoo-eval.sh
#
# Prerequisites:
#   - Java 21+, Maven (backend)
#   - Node 20+ (for npx promptfoo)
#   - OPENAI_API_KEY or LLM_API_KEY in backend/.env for real LLM responses

set -e
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BACKEND_PID=""
API_BASE="${PROMPTFOO_API_URL:-http://localhost:8080}"

# Load API key: backend/.env (matches backend config) > HORAIN_API_KEY env > default
API_KEY=""
if [ -f "$ROOT/backend/.env" ]; then
  EXTRACTED=$(grep -E '^HORAIN_API_KEY\s*=' "$ROOT/backend/.env" 2>/dev/null | head -1)
  if [ -n "$EXTRACTED" ]; then
    API_KEY=$(echo "$EXTRACTED" | cut -d= -f2- | sed -e 's/[#].*$//' -e 's/[[:space:]]*$//' -e 's/^["'\'']*//' -e 's/["'\'']*$//')
  fi
fi
API_KEY="${API_KEY:-${HORAIN_API_KEY:-HORAIN_DEV_KEY}}"

cleanup() {
  echo ""
  echo "Stopping backend..."
  if [ -n "$BACKEND_PID" ]; then
    kill "$BACKEND_PID" 2>/dev/null || true
  fi
  exit 0
}
trap cleanup SIGINT SIGTERM

# Check if backend is already running
if curl -s -o /dev/null -w "%{http_code}" "$API_BASE/health" 2>/dev/null | grep -q 200; then
  echo "Backend already running at $API_BASE"
else
  echo "Starting backend on port 8080..."
  cd "$ROOT/backend"
  mvn spring-boot:run -Dspring-boot.run.arguments="--server.address=0.0.0.0" &
  BACKEND_PID=$!
  cd "$ROOT"

  echo "Waiting for backend to be ready..."
  for i in $(seq 1 60); do
    if curl -s -o /dev/null -w "%{http_code}" "$API_BASE/health" 2>/dev/null | grep -q 200; then
      echo "Backend ready."
      break
    fi
    if [ $i -eq 60 ]; then
      echo "Timeout waiting for backend"
      exit 1
    fi
    sleep 2
  done
fi

# Seed the database (projects + time logs for evals)
echo "Seeding database..."
SEED_RES=$(curl -s -w "\n%{http_code}" -X POST "$API_BASE/dev/seed" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d '{}')
SEED_HTTP=$(echo "$SEED_RES" | tail -n1)
if [ "$SEED_HTTP" != "200" ]; then
  echo "Warning: Seed request failed (HTTP $SEED_HTTP). Some evals may fail."
  echo "Ensure horain.dev.seed-enabled=true and API key matches backend."
else
  echo "Seed complete."
fi

# Run Promptfoo evals
echo ""
echo "Running Promptfoo evals..."
cd "$ROOT/promptfoo"
export HORAIN_API_KEY="$API_KEY"
export PROMPTFOO_API_URL="$API_BASE"
npx promptfoo eval "$@"
