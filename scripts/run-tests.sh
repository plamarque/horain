#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SCRIPT_NAME="$(basename "$0")"

usage() {
  cat <<EOF
Usage: $SCRIPT_NAME [MODE] [OPTIONS]

Run tests: unit (backend), e2e (Playwright), Promptfoo evals (deterministic, scored), or all.

MODE (optional, default: e2e):
  unit           Backend unit tests (mvn verify, JaCoCo gate). No backend server needed.
  e2e            Playwright e2e tests. Starts backend on 8080 if needed, then runs frontend e2e.
  deterministic  Promptfoo deterministic evals only (no LLM-as-judge). Uses run-promptfoo-eval.sh --deterministic-only.
  scored         Promptfoo evals including scored (LLM-as-judge). Uses run-promptfoo-eval.sh --scored.
  promptfoo      Promptfoo full suite (deterministic + scored). Uses run-promptfoo-eval.sh.

OPTIONS:
  -h, --help     Show this help and exit.
  --all          Run all test suites in order: unit → e2e → deterministic → scored. Stops on first failure.

Examples:
  $SCRIPT_NAME                    Run e2e tests (default).
  $SCRIPT_NAME unit                Run backend unit tests only.
  $SCRIPT_NAME e2e                 Run Playwright e2e.
  $SCRIPT_NAME deterministic       Run only deterministic Promptfoo evals.
  $SCRIPT_NAME scored              Run Promptfoo evals with scored (Mistral judge); requires promptfoo/.env.
  $SCRIPT_NAME promptfoo           Run full Promptfoo suite (deterministic + scored).
  $SCRIPT_NAME --all               Run unit, then e2e, then deterministic, then scored.
  $SCRIPT_NAME --help              Show this help.

See also: docs/DEVELOPMENT.md (E2E tests, Evals Promptfoo).
EOF
}

# Parse first argument as MODE; -h/--help and --all supported.
MODE="e2e"
while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --all)
      MODE="all"
      shift
      break
      ;;
    unit|e2e|deterministic|scored|promptfoo)
      MODE="$1"
      shift
      break
      ;;
    -*)
      echo "Error: Unknown option: $1" >&2
      echo "" >&2
      usage >&2
      exit 1
      ;;
    *)
      echo "Error: Unknown argument: $1 (expected: unit, e2e, deterministic, scored, promptfoo, or --all)" >&2
      echo "" >&2
      usage >&2
      exit 1
      ;;
  esac
done
# Remaining args passed to promptfoo when MODE is deterministic/scored/promptfoo

cd "$ROOT_DIR"

# --all: run unit → e2e → deterministic → scored in order; stop on first failure.
if [ "$MODE" = "all" ]; then
  echo "=== Running all test suites (unit → e2e → deterministic → scored) ==="
  "$SCRIPT_DIR/run-tests.sh" unit || exit $?
  "$SCRIPT_DIR/run-tests.sh" e2e || exit $?
  "$SCRIPT_DIR/run-tests.sh" deterministic || exit $?
  "$SCRIPT_DIR/run-tests.sh" scored || exit $?
  echo "=== All test suites passed ==="
  exit 0
fi

case "$MODE" in
  unit)
    echo "Running backend unit tests..."
    cd "$ROOT_DIR/backend"
    mvn verify
    exit $?
    ;;
  deterministic)
    exec "$SCRIPT_DIR/run-promptfoo-eval.sh" --deterministic-only "$@"
    ;;
  scored)
    exec "$SCRIPT_DIR/run-promptfoo-eval.sh" --scored "$@"
    ;;
  promptfoo)
    exec "$SCRIPT_DIR/run-promptfoo-eval.sh" "$@"
    ;;
  e2e)
    # Run e2e tests. Ensure the backend is started and ready on 8080 before launching Playwright.
    backend_ready() {
      curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health 2>/dev/null | grep -q 200
    }

    BACKEND_PID=""
    cleanup_backend() {
      if [ -n "$BACKEND_PID" ]; then
        kill $BACKEND_PID 2>/dev/null || true
        wait $BACKEND_PID 2>/dev/null || true
      fi
    }

    if backend_ready; then
      echo "Backend already running on 8080."
      echo "Note: for deterministic chat e2e, restart it with HORAIN_E2E_CHAT_LLM_STUB=true (see docs/DEVELOPMENT.md)."
    else
      echo "Starting backend for e2e..."
      cd "$ROOT_DIR/backend"
      export HORAIN_E2E_CHAT_LLM_STUB=true
      mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.address=0.0.0.0" &
      BACKEND_PID=$!
      trap cleanup_backend EXIT
      cd "$ROOT_DIR"

      echo "Waiting for backend to be ready on 8080..."
      for i in $(seq 1 60); do
        if backend_ready; then
          echo "Backend ready."
          break
        fi
        sleep 2
      done
      if ! backend_ready; then
        echo "Error: Backend did not become ready on 8080. Check backend logs above."
        exit 1
      fi
    fi

    echo "Ensuring backend is reachable before e2e..."
    if ! backend_ready; then
      echo "Error: Backend on 8080 is not responding. Start it (e.g. ./scripts/start-dev.sh) and retry."
      exit 1
    fi

    # Build and serve frontend so Playwright's webServer can reuse it (avoids flaky "npm run build" in subprocess).
    frontend_ready() {
      curl -s -o /dev/null -w "%{http_code}" http://localhost:4173 2>/dev/null | grep -q 200
    }
    SERVE_PID=""
    cleanup_serve() {
      if [ -n "$SERVE_PID" ]; then
        kill $SERVE_PID 2>/dev/null || true
        wait $SERVE_PID 2>/dev/null || true
      fi
    }
    if frontend_ready; then
      echo "Frontend already serving on 4173."
    else
      echo "Building frontend for e2e..."
      cd "$ROOT_DIR/frontend"
      export BUILD_E2E=1
      export VITE_API_URL="http://localhost:8080"
      if [ -f "$ROOT_DIR/backend/.env" ]; then
        key=$(grep -E '^HORAIN_API_KEY=' "$ROOT_DIR/backend/.env" | head -1 | cut -d= -f2- | sed "s/^[\"' \t]*//;s/[\"' \t]*\$//")
        [ -n "$key" ] && export VITE_API_KEY="$key"
      fi
      [ -z "${VITE_API_KEY:-}" ] && export VITE_API_KEY="HORAIN_DEV_KEY"
      npm run build
      echo "Starting frontend server on 4173..."
      npx serve -s dist -l 4173 &
      SERVE_PID=$!
      trap "cleanup_serve; cleanup_backend" EXIT
      cd "$ROOT_DIR"
      for i in $(seq 1 30); do
        if frontend_ready; then
          echo "Frontend ready."
          break
        fi
        sleep 1
      done
      if ! frontend_ready; then
        echo "Error: Frontend did not become ready on 4173."
        exit 1
      fi
    fi

    cd "$ROOT_DIR/frontend"
    echo "Running e2e tests..."
    npm run test:e2e
    EXIT_CODE=$?

    cleanup_serve 2>/dev/null || true
    cleanup_backend 2>/dev/null || true
    trap - EXIT 2>/dev/null || true
    exit $EXIT_CODE
    ;;
  *)
    echo "Error: Unsupported mode: $MODE" >&2
    usage >&2
    exit 1
    ;;
esac
