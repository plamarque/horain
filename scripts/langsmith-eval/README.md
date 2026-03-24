# LangSmith evaluation (generated from Promptfoo)

Last stats refresh: 2026-03-23T17:05:05.864Z (example count: **74** for config `promptfoo/promptfooconfig.yaml`; **17** scored Promptfoo cases with `llm-rubric` in `promptfoo/tests/scored/`).

## Flow

1. **Import examples** into a LangSmith dataset (inputs align with this eval script):
   ```bash
   node scripts/import-promptfoo-to-langsmith.mjs
   ```
2. **Set** `LANGSMITH_DATASET_ID` (and `LANGSMITH_ENDPOINT` for EU if needed). Keys often live in `backend/.env`.
3. **Optional — Mistral judge** (same rubrics as Promptfoo scored tests): `PROMPTFOO_JUDGE_MISTRAL_API_KEY` and optional `PROMPTFOO_JUDGE_MODEL` in `promptfoo/.env` (loaded automatically). Use `--no-scored-judge` to skip LLM calls.
4. **Run experiment** (calls Horain `POST /chat/message` like Promptfoo):
   ```bash
   node scripts/langsmith-eval/run-evaluation.mjs
   ```

## Relation to Promptfoo

- **Target** [`run-evaluation.mjs`](./run-evaluation.mjs): same HTTP contract as [`promptfoo/providers/horain-api.mjs`](../promptfoo/providers/horain-api.mjs).
- **Code evaluators**: `horain_ok`, `promptfoo_meta`.
- **Scored tests (`llm-rubric`)**: when the judge API key is set, columns `promptfoo_llm_applied` (1 = Mistral called for this row), `promptfoo_llm_score` (0–1), `promptfoo_llm_pass` (1 if score ≥ Promptfoo threshold). Rows whose `metadata.description` is not in the scored YAML catalog get `promptfoo_llm_applied` = 0 (filter on `promptfoo_file` containing `/scored/` for aggregates).
- **Extra judges in LangSmith UI**: still possible via [bind evaluator to dataset](https://docs.langchain.com/langsmith/bind-evaluator-to-dataset).

## Regenerate this README

```bash
node scripts/generate-langsmith-eval-from-promptfoo.mjs
```
