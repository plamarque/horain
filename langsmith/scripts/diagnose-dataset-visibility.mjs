#!/usr/bin/env node
/**
 * Diagnoses why a LangSmith dataset may not be visible in Studio.
 *
 * It validates endpoint/key context, lists datasets, resolves a target dataset
 * by id or name, counts examples, and prints Studio URLs.
 *
 * Usage:
 *   node langsmith/scripts/diagnose-dataset-visibility.mjs
 *   node langsmith/scripts/diagnose-dataset-visibility.mjs --dataset-id <uuid>
 *   node langsmith/scripts/diagnose-dataset-visibility.mjs --dataset-name "Horain-promptfoo"
+ *   node langsmith/scripts/diagnose-dataset-visibility.mjs --limit 200
 */

import path from 'path'
import { fileURLToPath } from 'url'
import { loadEnvFile } from './lib/load-env.mjs'

const REPO_ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

function parseArgs(argv) {
  const out = {
    limit: 100,
    datasetId: '',
    datasetName: '',
    skipEnvLoad: false
  }
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--skip-env-load') {
      out.skipEnvLoad = true
    } else if (a.startsWith('--limit=')) {
      out.limit = Math.max(1, parseInt(a.slice('--limit='.length), 10) || 100)
    } else if (a === '--limit' && argv[i + 1]) {
      out.limit = Math.max(1, parseInt(argv[++i], 10) || 100)
    } else if (a.startsWith('--dataset-id=')) {
      out.datasetId = a.slice('--dataset-id='.length).trim()
    } else if (a === '--dataset-id' && argv[i + 1]) {
      out.datasetId = String(argv[++i] || '').trim()
    } else if (a.startsWith('--dataset-name=')) {
      out.datasetName = a.slice('--dataset-name='.length).trim()
    } else if (a === '--dataset-name' && argv[i + 1]) {
      out.datasetName = String(argv[++i] || '').trim()
    }
  }
  return out
}

function inferStudioBase(apiBase) {
  try {
    const u = new URL(apiBase)
    if (u.hostname === 'eu.api.smith.langchain.com') {
      return 'https://eu.smith.langchain.com'
    }
    if (u.hostname === 'api.smith.langchain.com') {
      return 'https://smith.langchain.com'
    }
  } catch {
    /* ignore */
  }
  return 'https://smith.langchain.com'
}

async function fetchJson(base, apiKey, pathCandidates) {
  let lastError = ''
  for (const p of pathCandidates) {
    const res = await fetch(`${base}${p}`, {
      method: 'GET',
      headers: { 'x-api-key': apiKey }
    })
    if (res.ok) {
      const text = await res.text()
      try {
        return { path: p, data: JSON.parse(text) }
      } catch {
        throw new Error(`Expected JSON body for GET ${p}, got: ${text.slice(0, 300)}`)
      }
    }
    const body = await res.text()
    lastError = `GET ${p} -> ${res.status}: ${body.slice(0, 200)}`
    if (res.status !== 404 && res.status !== 405) {
      throw new Error(lastError)
    }
  }
  throw new Error(lastError || 'All endpoint candidates failed')
}

function normalizeDatasets(payload) {
  if (Array.isArray(payload)) {
    return payload
  }
  if (payload && Array.isArray(payload.datasets)) {
    return payload.datasets
  }
  if (payload && Array.isArray(payload.items)) {
    return payload.items
  }
  return []
}

async function listDatasets(base, apiKey, limit) {
  const q = `?limit=${encodeURIComponent(limit)}`
  const { data, path: usedPath } = await fetchJson(base, apiKey, [`/datasets${q}`, `/api/v1/datasets${q}`])
  return { usedPath, datasets: normalizeDatasets(data) }
}

function pickDataset(datasets, datasetId, datasetName) {
  if (datasetId) {
    return datasets.find((d) => String(d.id || '').trim() === datasetId) || null
  }
  if (datasetName) {
    const low = datasetName.toLowerCase()
    return (
      datasets.find((d) => String(d.name || '').trim().toLowerCase() === low) ||
      datasets.find((d) => String(d.name || '').toLowerCase().includes(low)) ||
      null
    )
  }
  return datasets[0] || null
}

async function countExamples(base, apiKey, datasetId, cap = 10000) {
  let total = 0
  let offset = 0
  const pageSize = 100
  let usedPath = ''
  for (;;) {
    const queries = [
      `?dataset_id=${encodeURIComponent(datasetId)}&limit=${pageSize}&offset=${offset}`,
      `?id=${encodeURIComponent(datasetId)}&limit=${pageSize}&offset=${offset}`
    ]
    let response = null
    let lastErr = ''
    for (const q of queries) {
      try {
        response = await fetchJson(base, apiKey, [`/examples${q}`, `/api/v1/examples${q}`])
        usedPath = response.path.replace(q, '')
        break
      } catch (e) {
        lastErr = e instanceof Error ? e.message : String(e)
      }
    }
    if (!response) {
      throw new Error(lastErr || 'Cannot list examples for dataset')
    }
    const { data } = response
    const rows = Array.isArray(data) ? data : Array.isArray(data.examples) ? data.examples : []
    total += rows.length
    if (rows.length < pageSize || total >= cap) {
      break
    }
    offset += rows.length
  }
  return { total, usedPath, capped: total >= cap }
}

async function main() {
  const opts = parseArgs(process.argv)
  if (!opts.skipEnvLoad) {
    loadEnvFile(path.join(REPO_ROOT, 'backend', '.env'))
    loadEnvFile(path.join(REPO_ROOT, 'promptfoo', '.env'))
    loadEnvFile(path.join(REPO_ROOT, 'langsmith', '.env'))
  }

  const apiKey = process.env.LANGCHAIN_API_KEY || process.env.LANGSMITH_API_KEY
  const apiBase = (process.env.LANGSMITH_ENDPOINT || 'https://api.smith.langchain.com').replace(/\/$/, '')
  const studioBase = inferStudioBase(apiBase)
  const configuredProject = process.env.LANGSMITH_PROJECT || '(unset)'

  if (!apiKey) {
    console.error('Missing LANGCHAIN_API_KEY or LANGSMITH_API_KEY')
    process.exit(1)
  }

  console.error(`LangSmith endpoint: ${apiBase}`)
  console.error(`Studio base (inferred): ${studioBase}`)
  console.error(`Configured project name: ${configuredProject}`)

  const { datasets, usedPath } = await listDatasets(apiBase, apiKey, opts.limit)
  console.error(`Dataset listing path: ${usedPath}`)
  console.error(`Datasets visible with this key/endpoint: ${datasets.length}`)

  if (datasets.length === 0) {
    console.error('No dataset found for this key/endpoint context. Likely wrong endpoint or wrong workspace key.')
    process.exit(2)
  }

  const dataset = pickDataset(datasets, opts.datasetId, opts.datasetName)
  if (!dataset) {
    console.error('Target dataset not found in visible list (check --dataset-id/--dataset-name).')
    process.exit(3)
  }

  const datasetId = String(dataset.id || '').trim()
  const datasetName = String(dataset.name || '(unnamed)')
  const approx =
    dataset.example_count ??
    dataset.num_examples ??
    dataset.exampleCount ??
    dataset.count ??
    dataset.examples_count ??
    null

  let total = -1
  let examplesPath = '(unknown)'
  let capped = false
  if (datasetId) {
    try {
      const counted = await countExamples(apiBase, apiKey, datasetId)
      total = counted.total
      examplesPath = counted.usedPath
      capped = counted.capped
    } catch (e) {
      console.error(`Example counting fallback: ${e instanceof Error ? e.message : String(e)}`)
    }
  }
  const datasetUrl = datasetId ? `${studioBase}/datasets/${encodeURIComponent(datasetId)}` : '(missing dataset id)'

  console.log('')
  console.log('=== LangSmith Dataset Diagnosis ===')
  console.log(`dataset_id: ${datasetId || '(missing)'}`)
  console.log(`dataset_name: ${datasetName}`)
  console.log(`visible_in_current_context: yes`)
  console.log(`api_endpoint: ${apiBase}`)
  console.log(`dataset_list_path: ${usedPath}`)
  console.log(`examples_list_path: ${examplesPath}`)
  console.log(`example_count_exact: ${total >= 0 ? `${total}${capped ? ' (capped)' : ''}` : 'unavailable'}`)
  console.log(`example_count_from_dataset_object: ${approx == null ? 'n/a' : approx}`)
  console.log(`studio_dataset_url: ${datasetUrl}`)
  console.log(`studio_project_url: ${studioBase}/projects`)
  console.log('')
  console.log('Hints:')
  console.log('- If the dataset appears here but not in Studio, verify you are in the same LangSmith region/workspace.')
  console.log('- Compare endpoint region (US vs EU) and API key workspace with your browser account.')
  console.log('- If you only know a name, rerun with --dataset-name to confirm which UUID is visible.')
}

main().catch((err) => {
  console.error(err instanceof Error ? err.message : String(err))
  process.exit(1)
})
