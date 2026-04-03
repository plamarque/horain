# LangSmith evaluation scripts (generated from Promptfoo)

Last stats refresh: 2026-03-24T14:32:42.219Z (example count: **74** for config `promptfoo/promptfooconfig.yaml`; **17** scored Promptfoo cases with `llm-rubric` in `promptfoo/tests/scored/`).

## Flow

1. **Diagnose visibility** (optional but recommended):
   ```bash
   node langsmith/scripts/diagnose-dataset-visibility.mjs
   ```
2. **Import examples** into a LangSmith dataset:
   ```bash
   export LANGSMITH_DATASET_ID=<uuid-du-dataset>
   node langsmith/scripts/import-promptfoo-to-langsmith.mjs
   ```
3. **Alternative (explicit create + import)**:
   ```bash
   node langsmith/scripts/import-promptfoo-to-langsmith.mjs --create-dataset --dataset-name "Horain Promptfoo Seed v1"
   ```
4. Set `LANGSMITH_ENDPOINT` for EU if needed.
5. **Run experiment**:
   ```bash
   node langsmith/scripts/run-evaluation.mjs
   ```

## Shell wrapper

You can use a single wrapper script with a default dataset id:

```bash
sh langsmith/scripts/langsmith-workflow.sh help
sh langsmith/scripts/langsmith-workflow.sh import -- --only "promptfoo/promptfooconfig.yaml"
sh langsmith/scripts/langsmith-workflow.sh experiment -- --no-scored-judge
```

## Relation to Promptfoo

- **Target** [`run-evaluation.mjs`](./run-evaluation.mjs): same HTTP contract as [`promptfoo/providers/horain-api.mjs`](../../promptfoo/providers/horain-api.mjs).
- **Code evaluators**: `horain_ok`, `promptfoo_meta`.
- **Scored tests (`llm-rubric`)**: if `PROMPTFOO_JUDGE_MISTRAL_API_KEY` is set, columns `promptfoo_llm_applied`, `promptfoo_llm_score`, `promptfoo_llm_pass` are computed from `promptfoo/tests/scored/*`.

## Regenerate this README

```bash
node langsmith/scripts/generate-langsmith-eval-from-promptfoo.mjs
```
