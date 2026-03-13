#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

# Run e2e tests. Ensure the backend is started and ready on 8080 before launching Playwright.

backend_ready() {
  curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health 2>/dev/null | grep -q 200
}

BACKEND_PID=""
cleanup_backend() {
  if [ -n "$BACKEND_PID" ]; then
    kill $BACKEND_PID 2>/dev/null || true
    # Wait for backend to shut down so Maven does not print BUILD FAILURE after the script exits.
    # Ignore backend exit code: we care only about e2e result.
    wait $BACKEND_PID 2>/dev/null || true
  fi
}

if backend_ready; then
  echo "Backend already running on 8080."
else
  echo "Starting backend for e2e..."
  cd "$ROOT_DIR/backend"
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

cd "$ROOT_DIR/frontend"
echo "Running e2e tests..."
npm run test:e2e
EXIT_CODE=$?

cleanup_backend 2>/dev/null || true
trap - EXIT 2>/dev/null || true
exit $EXIT_CODE
