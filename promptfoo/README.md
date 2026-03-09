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
- `providers/horain-api.mjs` - Custom provider; supports `vars.history` for multi-turn

## Test suites

| Suite | File | Coverage |
|-------|------|----------|
| log-time | log-time.yaml | Direct log, toolCalls validation |
| clarification | clarification.yaml | Missing duration, unknown project, ambiguous (HatCast V1/V2) |
| analytics | analytics.yaml | Time queries, structure/format |
| json-ui | json-ui.yaml | data.timeLogs, data.chart, is-json |
| state-transitions | state-transitions.yaml | Multi-turn, history |
| robustness | robustness.yaml | Paraphrases, fr/en mix |
| no-data-and-safety | no-data-and-safety.yaml | No create without confirmation |

## Output

- Default: summary in terminal
- `npx promptfoo eval --output results.html` - HTML report
- `npx promptfoo eval --output results.json` - JSON results
