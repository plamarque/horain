#!/usr/bin/env node
/**
 * Refreshes langsmith/scripts/README.md with current Promptfoo corpus stats.
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
    outDir: SCRIPT_DIR
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
  return `# LangSmith evaluation scripts (generated from Promptfoo)

Last stats refresh: ${iso} (example count: **${count}** for config \`${path.relative(REPO_ROOT, configPath)}\`; **${scoredCatalogSize}** scored Promptfoo cases with \`llm-rubric\` in \`promptfoo/tests/scored/\`).

## Flow

1. **Diagnose visibility** (optional but recommended):
   \`\`\`bash
   node langsmith/scripts/diagnose-dataset-visibility.mjs
   \`\`\`
2. **Import examples** into a LangSmith dataset:
   \`\`\`bash
   node langsmith/scripts/import-promptfoo-to-langsmith.mjs
   \`\`\`
3. **Set** \`LANGSMITH_DATASET_ID\` (and \`LANGSMITH_ENDPOINT\` for EU if needed). Keys often live in \`backend/.env\`.
4. **Run experiment**:
   \`\`\`bash
   node langsmith/scripts/run-evaluation.mjs
   \`\`\`

## Relation to Promptfoo

- **Target** [\`run-evaluation.mjs\`](./run-evaluation.mjs): same HTTP contract as [\`promptfoo/providers/horain-api.mjs\`](../../promptfoo/providers/horain-api.mjs).
- **Code evaluators**: \`horain_ok\`, \`promptfoo_meta\`.
- **Scored tests (\`llm-rubric\`)**: if \`PROMPTFOO_JUDGE_MISTRAL_API_KEY\` is set, columns \`promptfoo_llm_applied\`, \`promptfoo_llm_score\`, \`promptfoo_llm_pass\` are computed from ` + '`promptfoo/tests/scored/*`' + `.

## Regenerate this README

\`\`\`bash
node langsmith/scripts/generate-langsmith-eval-from-promptfoo.mjs
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
