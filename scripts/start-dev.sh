#!/bin/bash
# Start Horain in local dev mode: backend (Spring Boot) + frontend (Vite).
# Press Ctrl+C to stop both.

set -e
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cleanup() {
  echo ""
  echo "Stopping..."
  kill "$BACKEND_PID" 2>/dev/null || true
  kill "$FRONTEND_PID" 2>/dev/null || true
  exit 0
}
trap cleanup SIGINT SIGTERM

echo "Starting backend (port 8080)..."
cd "$ROOT/backend"
mvn spring-boot:run -Dspring-boot.run.arguments="--server.address=0.0.0.0" &
BACKEND_PID=$!

echo "Waiting for backend to be ready..."
until curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health 2>/dev/null | grep -q 200; do
  sleep 1
done
echo "Backend ready."

echo "Starting frontend (port 5173)..."
cd "$ROOT/frontend"
npm run dev -- --host &
FRONTEND_PID=$!

sleep 2
LOCAL_IP=$(ifconfig | grep "inet " | grep -v 127.0.0.1 | awk '{print $2}' | head -1)
echo ""
echo "Horain dev server running:"
echo "  Backend:  http://localhost:8080"
echo "  Frontend: https://localhost:5173"
if [ -n "$LOCAL_IP" ]; then
  echo "  Réseau local (smartphone): https://${LOCAL_IP}:5173"
fi
echo "  Press Ctrl+C to stop"
echo ""

wait
