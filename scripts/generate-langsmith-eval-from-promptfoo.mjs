#!/usr/bin/env node
/**
 * Refreshes scripts/langsmith-eval/README.md with current Promptfoo corpus stats
 * (same filters as import-promptfoo-to-langsmith.mjs). Does not modify run-evaluation.mjs;
 * edit that file by hand to add evaluators mirroring promptfoo asserts / LLM rubrics.
 *
 * Usage:
 *   node scripts/generate-langsmith-eval-from-promptfoo.mjs
 *   node scripts/generate-langsmith-eval-from-promptfoo.mjs --config promptfoo/promptfooconfig.deterministic.yaml --skip-scored
 */

import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { collectExamples, parseCorpusArgv, REPO_ROOT } from './lib/promptfoo-corpus.mjs'
import { loadPromptfooScoredCatalog } from './lib/promptfoo-scored-catalog.mjs'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))

function parseArgs(argv) {
  const out = {
    ...parseCorpusArgv(argv),
    outDir: path.join(SCRIPT_DIR, 'langsmith-eval')
  }
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i]
    if (a.startsWith('--out-dir=')) {
      out.outDir = path.isAbsolute(a.slice('--out-dir='.length))
        ? a.slice('--out-dir='.length)
        : path.resolve(REPO_ROOT, a.slice('--out-dir='.length))
    } else if (a === '--out-dir' && argv[i + 1]) {
      const p = argv[++i]
      out.outDir = path.isAbsolute(p) ? p : path.resolve(REPO_ROOT, p)
    }
  }
  return out
}

function readmeBody(count, configPath, scoredCatalogSize) {
  const iso = new Date().toISOString()
  return `# LangSmith evaluation (generated from Promptfoo)

Last stats refresh: ${iso} (example count: **${count}** for config \`${path.relative(REPO_ROOT, configPath)}\`; **${scoredCatalogSize}** scored Promptfoo cases with \`llm-rubric\` in \`promptfoo/tests/scored/\`).

## Flow

1. **Import examples** into a LangSmith dataset (inputs align with this eval script):
   \`\`\`bash
   node scripts/import-promptfoo-to-langsmith.mjs
   \`\`\`
2. **Set** \`LANGSMITH_DATASET_ID\` (and \`LANGSMITH_ENDPOINT\` for EU if needed). Keys often live in \`backend/.env\`.
3. **Optional — Mistral judge** (same rubrics as Promptfoo scored tests): \`PROMPTFOO_JUDGE_MISTRAL_API_KEY\` and optional \`PROMPTFOO_JUDGE_MODEL\` in \`promptfoo/.env\` (loaded automatically). Use \`--no-scored-judge\` to skip LLM calls.
4. **Run experiment** (calls Horain \`POST /chat/message\` like Promptfoo):
   \`\`\`bash
   node scripts/langsmith-eval/run-evaluation.mjs
   \`\`\`

## Relation to Promptfoo

- **Target** [\`run-evaluation.mjs\`](./run-evaluation.mjs): same HTTP contract as [\`promptfoo/providers/horain-api.mjs\`](../promptfoo/providers/horain-api.mjs).
- **Code evaluators**: \`horain_ok\`, \`promptfoo_meta\`.
- **Scored tests (\`llm-rubric\`)**: when the judge API key is set, columns \`promptfoo_llm_applied\` (1 = Mistral called for this row), \`promptfoo_llm_score\` (0–1), \`promptfoo_llm_pass\` (1 if score ≥ Promptfoo threshold). Rows whose \`metadata.description\` is not in the scored YAML catalog get \`promptfoo_llm_applied\` = 0 (filter on \`promptfoo_file\` containing \`/scored/\` for aggregates).
- **Extra judges in LangSmith UI**: still possible via [bind evaluator to dataset](https://docs.langchain.com/langsmith/bind-evaluator-to-dataset).

## Regenerate this README

\`\`\`bash
node scripts/generate-langsmith-eval-from-promptfoo.mjs
\`\`\`
`
}

function main() {
  const opts = parseArgs(process.argv)
  const { skipScored, only, config } = opts
  const examples = collectExamples(config, { skipScored, only })
  const scoredSize = loadPromptfooScoredCatalog().size
  const readme = readmeBody(examples.length, config, scoredSize)
  const outFile = path.join(opts.outDir, 'README.md')
  fs.mkdirSync(opts.outDir, { recursive: true })
  fs.writeFileSync(outFile, readme, 'utf8')
  console.error(`Wrote ${path.relative(REPO_ROOT, outFile)} (${examples.length} examples for selected config/filters).`)
}

main()
