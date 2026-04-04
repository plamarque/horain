#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

usage() {
  echo "Usage: $0 --patch | --minor | --major [--fast] [--skip-tests]"
  echo "  --patch   0.1.0-SNAPSHOT → release 0.1.1, then 0.1.2-SNAPSHOT"
  echo "  --minor   0.1.0-SNAPSHOT → release 0.2.0, then 0.2.1-SNAPSHOT"
  echo "  --major   0.1.0-SNAPSHOT → release 1.0.0, then 1.0.1-SNAPSHOT"
  echo "  --fast    Run backend unit tests + frontend build only (skip local e2e; e2e still runs on push to main in CI)."
  echo "  --skip-tests  Skip all local tests and frontend build before bumping (trust CI; use sparingly)."
  echo ""
  echo "The release workflow (triggered by the tag) creates the GitHub release"
  echo "with an auto-generated changelog from commits."
  exit 1
}

BUMP=""
SKIP_TESTS=false
FAST=false

for arg in "$@"; do
  case "$arg" in
    --patch) BUMP="patch" ;;
    --minor) BUMP="minor" ;;
    --major) BUMP="major" ;;
    --skip-tests) SKIP_TESTS=true ;;
    --fast) FAST=true ;;
    *) usage ;;
  esac
done

if [ -z "$BUMP" ]; then
  usage
fi

# 1. Check clean working tree
if [ -n "$(git status --porcelain)" ]; then
  echo "Error: working tree is not clean. Commit or stash your changes."
  exit 1
fi

# 2. Check gh
if ! command -v gh &>/dev/null; then
  echo "Error: GitHub CLI (gh) not installed. See https://cli.github.com/"
  exit 1
fi
if ! gh auth status &>/dev/null; then
  echo "Error: gh not authenticated. Run: gh auth login"
  exit 1
fi

# 3. Tests and build
if [ "$SKIP_TESTS" = true ]; then
  echo "Skipping local tests and frontend build (--skip-tests)."
elif [ "$FAST" = true ]; then
  echo "Running backend unit tests (--fast; skipping local e2e)..."
  cd "$ROOT_DIR/backend"
  mvn verify -q
  echo "Building frontend..."
  cd "$ROOT_DIR/frontend"
  npm run build
  cd "$ROOT_DIR"
else
  echo "Running backend tests..."
  cd "$ROOT_DIR/backend"
  mvn verify -q

  # E2E require backend on 8080 (same as CI). Ensure port is free, then start and run Playwright.
  if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health 2>/dev/null | grep -q 200; then
    echo "Error: Port 8080 is already in use (e.g. backend from start-dev.sh). Stop it and retry."
    exit 1
  fi
  if command -v lsof &>/dev/null; then
    if lsof -i :8080 -sTCP:LISTEN -t &>/dev/null; then
      echo "Error: Port 8080 is in use. Stop the process (e.g. ./scripts/start-dev.sh) and retry."
      exit 1
    fi
  fi

  echo "Starting backend for e2e..."
  export HORAIN_E2E_CHAT_LLM_STUB=true
  mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.address=0.0.0.0" &
  BACKEND_PID=$!
  cleanup_backend() { kill $BACKEND_PID 2>/dev/null || true; }
  trap cleanup_backend EXIT

  echo "Waiting for backend to be ready..."
  for i in $(seq 1 60); do
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health 2>/dev/null | grep -q 200; then
      echo "Backend ready."
      break
    fi
    sleep 2
  done
  if ! curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health 2>/dev/null | grep -q 200; then
    echo "Error: Backend did not become ready on 8080. Check backend logs above."
    exit 1
  fi

  echo "Running frontend e2e tests..."
  cd "$ROOT_DIR/frontend"
  npm run test:e2e
  cleanup_backend
  trap - EXIT

  echo "Building frontend..."
  npm run build

  cd "$ROOT_DIR"
fi

# 4. Phase 1: Release — extract base, bump, update to release version
CURRENT=$(node -p "require('./package.json').version")
if [[ "$CURRENT" == *-SNAPSHOT ]]; then
  BASE="${CURRENT%-SNAPSHOT}"
else
  BASE="$CURRENT"
fi

RELEASE_VERSION=$(npx semver -i "$BUMP" "$BASE")

echo "Phase 1: Releasing v$RELEASE_VERSION (from $CURRENT)"

# Update all 3 files with release version
node -e "
const p = require('./package.json');
p.version = '$RELEASE_VERSION';
require('fs').writeFileSync('./package.json', JSON.stringify(p, null, 2) + '\n');
"

node -e "
const p = require('./frontend/package.json');
p.version = '$RELEASE_VERSION';
require('fs').writeFileSync('./frontend/package.json', JSON.stringify(p, null, 2) + '\n');
"

perl -i -0pe 's/(<artifactId>horain-backend<\/artifactId>\s*)<version>[^<]+<\/version>/\1<version>'"$RELEASE_VERSION"'<\/version>/' backend/pom.xml

# 5. Commit, tag, push
TAG="v$RELEASE_VERSION"
git add package.json frontend/package.json backend/pom.xml
git commit -m "chore: release $TAG"
git tag "$TAG"

echo "Pushing release to origin..."
git push origin main --tags

# 6. Phase 2: Prepare next dev — bump patch, add -SNAPSHOT
NEXT_SNAPSHOT=$(npx semver -i patch "$RELEASE_VERSION")-SNAPSHOT

echo "Phase 2: Preparing next dev $NEXT_SNAPSHOT"

node -e "
const p = require('./package.json');
p.version = '$NEXT_SNAPSHOT';
require('fs').writeFileSync('./package.json', JSON.stringify(p, null, 2) + '\n');
"

node -e "
const p = require('./frontend/package.json');
p.version = '$NEXT_SNAPSHOT';
require('fs').writeFileSync('./frontend/package.json', JSON.stringify(p, null, 2) + '\n');
"

perl -i -0pe 's/(<artifactId>horain-backend<\/artifactId>\s*)<version>[^<]+<\/version>/\1<version>'"$NEXT_SNAPSHOT"'<\/version>/' backend/pom.xml

git add package.json frontend/package.json backend/pom.xml
git commit -m "chore: prepare next dev $NEXT_SNAPSHOT"

echo "Pushing next dev to origin..."
git push origin main

echo ""
echo "Release $TAG pushed. The GitHub Actions workflow will create the release with the changelog."
echo "Next dev version: $NEXT_SNAPSHOT"
