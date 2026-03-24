#!/bin/bash
# Start LangGraph dev server using backend/.env as source of truth.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LANGSMITH_DIR="$ROOT/langsmith"
BACKEND_ENV_FILE="$ROOT/backend/.env"

if [ ! -d "$LANGSMITH_DIR" ]; then
  echo "Error: langsmith directory not found at $LANGSMITH_DIR"
  exit 1
fi

if [ -f "$BACKEND_ENV_FILE" ]; then
  echo "Loading environment from $BACKEND_ENV_FILE"
  set -a
  . "$BACKEND_ENV_FILE"
  set +a
else
  echo "Warning: backend/.env not found. Continuing with current shell environment."
fi

cd "$LANGSMITH_DIR"
echo "Starting LangGraph dev server from $LANGSMITH_DIR"
exec npx @langchain/langgraph-cli dev
