#!/usr/bin/env node
/**
 * Imports Promptfoo test cases into a LangSmith dataset via POST /examples.
 *
 * Reads a Promptfoo config (default: promptfoo/promptfooconfig.yaml), expands inline tests
 * and file:// references, extracts vars.message per case, and uploads one example per test.
 *
 * Environment (after optional load of backend/.env — see below):
 *   - LANGCHAIN_API_KEY (required for upload; often set in backend/.env)
 *   - LANGSMITH_DATASET_ID — optional; if unset, a new dataset is created via POST /datasets
 *   - LANGSMITH_ENDPOINT (default: https://api.smith.langchain.com; EU: https://eu.api.smith.langchain.com)
 *
 * By default, variables from backend/.env are merged into process.env (existing env wins).
 * Use --no-backend-env to skip. Does not expand ${VAR} in .env values.
 *
 * Usage (from repo root):
 *   node scripts/import-promptfoo-to-langsmith.mjs --dry-run
 *   node scripts/import-promptfoo-to-langsmith.mjs
 *   node scripts/import-promptfoo-to-langsmith.mjs --dataset-name "My corpus"
 *   node scripts/import-promptfoo-to-langsmith.mjs --config promptfoo/promptfooconfig.deterministic.yaml
 *   node scripts/import-promptfoo-to-langsmith.mjs --skip-scored --delay-ms 100
 *   node scripts/import-promptfoo-to-langsmith.mjs --only "clarification|analytics"
 */

import path from 'path'
import { loadEnvFile } from './lib/load-env.mjs'
import { REPO_ROOT, collectExamples, parseCorpusArgv } from './lib/promptfoo-corpus.mjs'

function parseArgs(argv) {
  const corpus = parseCorpusArgv(argv)
  const out = {
    ...corpus,
    dryRun: false,
    delayMs: 0,
    backendEnv: true,
    datasetName: null
  }
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--dry-run') {
      out.dryRun = true
    } else if (a === '--no-backend-env') {
      out.backendEnv = false
    } else if (a.startsWith('--delay-ms=')) {
      out.delayMs = Math.max(0, parseInt(a.slice('--delay-ms='.length), 10) || 0)
    } else if (a === '--delay-ms' && argv[i + 1]) {
      out.delayMs = Math.max(0, parseInt(argv[++i], 10) || 0)
    } else if (a.startsWith('--dataset-name=')) {
      out.datasetName = a.slice('--dataset-name='.length)
    } else if (a === '--dataset-name' && argv[i + 1]) {
      out.datasetName = argv[++i]
    }
  }
  return out
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms))
}

function defaultDatasetName() {
  const d = new Date()
  const iso = d.toISOString().replace(/[:.]/g, '-').slice(0, 19)
  return `Horain-promptfoo-${iso}`
}

/**
 * Creates a dataset in LangSmith. Tries POST /datasets then POST /api/v1/datasets.
 * @returns {Promise<string>} dataset id (UUID)
 */
async function createDataset(base, apiKey, name, description) {
  const body = JSON.stringify({
    name,
    description: description || 'Imported from Horain Promptfoo corpus (scripts/import-promptfoo-to-langsmith.mjs)'
  })
  const paths = ['/datasets', '/api/v1/datasets']
  let lastErr = ''
  for (const p of paths) {
    const res = await fetch(`${base}${p}`, {
      method: 'POST',
      headers: {
        'x-api-key': apiKey,
        'Content-Type': 'application/json'
      },
      body
    })
    const text = await res.text()
    if (res.ok) {
      let json
      try {
        json = JSON.parse(text)
      } catch {
        throw new Error(`LangSmith POST ${p}: expected JSON body, got: ${text.slice(0, 200)}`)
      }
      const id = json.id ?? json.dataset_id
      if (!id || typeof id !== 'string') {
        throw new Error(`LangSmith POST ${p}: missing id in response: ${text.slice(0, 500)}`)
      }
      return id
    }
    lastErr = `${res.status}: ${text}`
    if (res.status !== 404 && res.status !== 405) {
      throw new Error(`LangSmith POST ${p} failed: ${lastErr}`)
    }
  }
  throw new Error(`LangSmith create dataset failed (tried /datasets and /api/v1/datasets): ${lastErr}`)
}

async function postExample(row, datasetId, base, apiKey, index) {
  const body = {
    dataset_id: datasetId,
    inputs: {
      user_message: row.user_message,
      source_turn_id: ''
    },
    outputs: {},
    metadata: {
      source: 'promptfoo',
      promptfoo_file: row.promptfoo_file,
      description: row.description,
      test_index: index
    }
  }
  const res = await fetch(`${base}/examples`, {
    method: 'POST',
    headers: {
      'x-api-key': apiKey,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body)
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`LangSmith POST /examples ${res.status}: ${text}`)
  }
}

async function main() {
  const options = parseArgs(process.argv)
  if (options.backendEnv) {
    loadEnvFile(path.join(REPO_ROOT, 'backend', '.env'))
  }

  const { skipScored, only, config } = options
  const examples = collectExamples(config, { skipScored, only })

  console.error(`Collected ${examples.length} Promptfoo test case(s) from ${options.config}`)

  if (examples.length === 0) {
    console.error('No examples to upload (check --only / --skip-scored / config).')
    process.exit(1)
  }

  if (options.dryRun) {
    const sample = examples.slice(0, 3)
    console.error('Sample (first 3):')
    for (const s of sample) {
      console.error(JSON.stringify(s, null, 2))
    }
    process.exit(0)
  }

  const API_KEY = process.env.LANGCHAIN_API_KEY
  const BASE = (process.env.LANGSMITH_ENDPOINT || 'https://api.smith.langchain.com').replace(/\/$/, '')

  if (!API_KEY) {
    console.error(
      'Missing LANGCHAIN_API_KEY (set in environment or backend/.env; use --no-backend-env to skip loading backend/.env)'
    )
    process.exit(1)
  }

  let datasetId = process.env.LANGSMITH_DATASET_ID?.trim()
  if (!datasetId) {
    const name = (options.datasetName && options.datasetName.trim()) || defaultDatasetName()
    console.error(`Creating LangSmith dataset: ${name}`)
    datasetId = await createDataset(BASE, API_KEY, name)
    console.error(`Created dataset id: ${datasetId}`)
  }

  let ok = 0
  for (let i = 0; i < examples.length; i++) {
    const row = examples[i]
    const idx = i + 1
    await postExample(row, datasetId, BASE, API_KEY, idx)
    ok++
    if (options.delayMs > 0 && i < examples.length - 1) {
      await sleep(options.delayMs)
    }
  }
  console.error(`Uploaded ${ok} example(s) to dataset ${datasetId}`)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
