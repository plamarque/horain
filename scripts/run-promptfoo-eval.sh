#!/bin/bash
# Run Promptfoo evals: start backend (if needed), seed DB, run evals.
# Usage: ./scripts/run-promptfoo-eval.sh [promptfoo eval args...]
#
# Prerequisites:
#   - Java 21+, Maven (backend)
#   - Node 20+ (for npx promptfoo)
#   - OPENAI_API_KEY or LLM_API_KEY in backend/.env for real LLM responses
#   - For scored evals: PROMPTFOO_JUDGE_MISTRAL_API_KEY and PROMPTFOO_JUDGE_MODEL
#     in promptfoo/.env (loaded automatically)

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
  -d '{"fixedToday":"2025-03-10"}')
SEED_HTTP=$(echo "$SEED_RES" | tail -n1)
if [ "$SEED_HTTP" != "200" ]; then
  echo "Warning: Seed request failed (HTTP $SEED_HTTP). Some evals may fail."
  echo "Ensure horain.dev.seed-enabled=true and API key matches backend."
else
  echo "Seed complete."
fi

# Load judge vars from promptfoo/.env (for scored evals with Mistral)
PROMPTFOO_ENV="$ROOT/promptfoo/.env"
if [ -f "$PROMPTFOO_ENV" ]; then
  _extract() {
    grep -E "^${1}\s*=" "$PROMPTFOO_ENV" 2>/dev/null | head -1 | cut -d= -f2- | sed -e 's/[#].*$//' -e 's/[[:space:]]*$//' -e 's/^["'\'']*//' -e 's/["'\'']*$//'
  }
  JUDGE_KEY="$(_extract PROMPTFOO_JUDGE_MISTRAL_API_KEY)"
  [ -n "$JUDGE_KEY" ] && export PROMPTFOO_JUDGE_MISTRAL_API_KEY="$JUDGE_KEY"
  JUDGE_MODEL="$(_extract PROMPTFOO_JUDGE_MODEL)"
  [ -n "$JUDGE_MODEL" ] && export PROMPTFOO_JUDGE_MODEL="$JUDGE_MODEL"
fi

# Parse mode: --deterministic-only (no scored) or --scored (full run with rate limit)
PROMPTFOO_ARGS=()
CONFIG_FILE="promptfooconfig.yaml"
while [ $# -gt 0 ]; do
  case "$1" in
    --deterministic-only)
      CONFIG_FILE="promptfooconfig.deterministic.yaml"
      shift
      ;;
    --scored)
      PROMPTFOO_ARGS+=(--max-concurrency 1)
      shift
      ;;
    *)
      PROMPTFOO_ARGS+=("$1")
      shift
      ;;
  esac
done

# Unique run ID for eval-only project names (avoids DB pollution between runs)
export EVAL_RUN_ID="${EVAL_RUN_ID:-$(date +%s)}"
EVAL_PROPOSAL_PROJECT="EvalProposal_${EVAL_RUN_ID}"

# Run Promptfoo evals
echo ""
echo "Running Promptfoo evals..."
cd "$ROOT/promptfoo"
export HORAIN_API_KEY="$API_KEY"
export PROMPTFOO_API_URL="$API_BASE"
export PROMPTFOO_DISABLE_WAL_MODE="${PROMPTFOO_DISABLE_WAL_MODE:-true}"
npx promptfoo eval -c "$CONFIG_FILE" --var "evalProposalProject=${EVAL_PROPOSAL_PROJECT}" "${PROMPTFOO_ARGS[@]}"
