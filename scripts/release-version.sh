#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

usage() {
  echo "Usage: $0 --patch | --minor | --major"
  echo "  --patch   0.1.0 → 0.1.1"
  echo "  --minor   0.1.1 → 0.2.0"
  echo "  --major   0.2.0 → 1.0.0"
  echo ""
  echo "The release workflow (triggered by the tag) creates the GitHub release"
  echo "with an auto-generated changelog from commits."
  exit 1
}

BUMP=""
for arg in "$@"; do
  case "$arg" in
    --patch) BUMP="patch" ;;
    --minor) BUMP="minor" ;;
    --major) BUMP="major" ;;
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
echo "Running backend tests..."
cd "$ROOT_DIR/backend"
mvn test -q

echo "Running frontend e2e tests..."
cd "$ROOT_DIR/frontend"
npm run test:e2e

echo "Building frontend..."
npm run build

cd "$ROOT_DIR"

# 4. Bump version (all 3 files)
CURRENT=$(node -p "require('./package.json').version")
NEW_VERSION=$(npx semver -i "$BUMP" "$CURRENT")

echo "Bumping version: $CURRENT → $NEW_VERSION"

# Update root package.json
node -e "
const p = require('./package.json');
p.version = '$NEW_VERSION';
require('fs').writeFileSync('./package.json', JSON.stringify(p, null, 2) + '\n');
"

# Update frontend package.json
node -e "
const p = require('./frontend/package.json');
p.version = '$NEW_VERSION';
require('fs').writeFileSync('./frontend/package.json', JSON.stringify(p, null, 2) + '\n');
"

# Update backend pom.xml (only the project version, not parent or dependencies)
perl -i -0pe 's/(<artifactId>horain-backend<\/artifactId>\s*)<version>[^<]+<\/version>/\1<version>'"$NEW_VERSION"'<\/version>/' backend/pom.xml

# 5. Commit and tag
TAG="v$NEW_VERSION"
git add package.json frontend/package.json backend/pom.xml
git commit -m "chore: release $TAG"
git tag "$TAG"

# 6. Push
echo "Pushing to origin..."
git push origin main --tags

echo ""
echo "Release $TAG pushed. The GitHub Actions workflow will create the release with the changelog."
