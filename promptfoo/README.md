# Horain Promptfoo Evals

LLM evaluation suite for the Horain chat API using [Promptfoo](https://www.promptfoo.dev/).

## Prerequisites

- Backend running on `http://localhost:8080`
- `OPENAI_API_KEY` or `LLM_API_KEY` in `backend/.env` (backend uses it for LLM)
- For tests that need projects: run `POST /dev/seed` first (or use `./scripts/run-promptfoo-eval.sh`)

## Run evals

```bash
# From project root (backend must be running)
cd promptfoo && npx promptfoo eval

# Or use the helper script (starts backend, seeds, runs evals)
./scripts/run-promptfoo-eval.sh
```

## Configuration

- `promptfooconfig.yaml` - Main config: prompts, providers, tests
- `HORAIN_API_KEY` - API key for backend auth (default: HORAIN_DEV_KEY)
- Tests are defined inline; additional test files can be added under `tests/`

## Output

- Default: summary in terminal
- `npx promptfoo eval --output results.html` - HTML report
- `npx promptfoo eval --output results.json` - JSON results
