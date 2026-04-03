#!/usr/bin/env sh
set -eu

# Wrapper around npm scripts for LangSmith workflows.
# It centralizes a default dataset id and forwards extra args.
#
# Usage:
#   sh langsmith/scripts/langsmith-workflow.sh diagnose [-- ...]
#   sh langsmith/scripts/langsmith-workflow.sh import [-- ...]
#   sh langsmith/scripts/langsmith-workflow.sh create-import [-- ...]
#   sh langsmith/scripts/langsmith-workflow.sh experiment [-- ...]
#   sh langsmith/scripts/langsmith-workflow.sh export [-- ...]
#
# Examples:
#   sh langsmith/scripts/langsmith-workflow.sh import -- --only "promptfoo/promptfooconfig.yaml"
#   sh langsmith/scripts/langsmith-workflow.sh experiment -- --no-scored-judge
#   curl -sS -H "Authorization: Bearer $HORAIN_API_KEY" "$EVAL_CANDIDATES_ENDPOINT" \
#     | sh langsmith/scripts/langsmith-workflow.sh export

DEFAULT_DATASET_ID="${DEFAULT_DATASET_ID:-e3fee2fc-c2a7-4d6c-bad3-c0655293ff82}"

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)

if [ $# -lt 1 ]; then
  echo "Missing command."
  echo "Commands: diagnose | import | create-import | experiment | export | help"
  exit 1
fi

cmd="$1"
shift

run_npm() {
  # Enforce a stable default dataset id, while allowing override:
  # LANGSMITH_DATASET_ID=<other-id> sh ... <command>
  LANGSMITH_DATASET_ID="${LANGSMITH_DATASET_ID:-$DEFAULT_DATASET_ID}" \
    npm --prefix "$REPO_ROOT" run "$@"
}

case "$cmd" in
  diagnose)
    run_npm langsmith:dataset:diagnose "$@"
    ;;
  import)
    run_npm langsmith:dataset:import "$@"
    ;;
  create-import)
    run_npm langsmith:dataset:create-and-import "$@"
    ;;
  experiment)
    run_npm langsmith:experiment:run "$@"
    ;;
  export)
    run_npm langsmith:dataset:export "$@"
    ;;
  help|-h|--help)
    cat <<'EOF'
langsmith-workflow.sh commands:
  diagnose       Run dataset visibility diagnosis
  import         Import Promptfoo corpus into existing dataset
  create-import  Create dataset explicitly then import corpus
  experiment     Run LangSmith experiment
  export         Export eval-candidates JSONL to dataset

Environment:
  DEFAULT_DATASET_ID   Fallback dataset id used by this wrapper
  LANGSMITH_DATASET_ID Overrides dataset id for one run

Pass-through args:
  Add script args directly after command, including `-- ...` when needed.
EOF
    ;;
  *)
    echo "Unknown command: $cmd"
    echo "Use: sh langsmith/scripts/langsmith-workflow.sh help"
    exit 1
    ;;
esac
