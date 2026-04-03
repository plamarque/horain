#!/usr/bin/env node
/**
 * Runs a LangSmith experiment on a dataset produced by import-promptfoo-to-langsmith.mjs
 * (inputs.user_message per example). Target = Horain POST /chat/message (same idea as promptfoo/providers/horain-api.mjs).
 */

import path from 'path'
import { fileURLToPath } from 'url'
import { Client } from 'langsmith'
import { evaluate } from 'langsmith/evaluation'
import { loadEnvFile } from './lib/load-env.mjs'
import { loadPromptfooScoredCatalog } from './lib/promptfoo-scored-catalog.mjs'
import { callMistralJudge, normalizeJudgeScore, parseJudgeJson } from './lib/mistral-judge.mjs'

const REPO_ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const DEFAULT_DATASET_ID = 'e3fee2fc-c2a7-4d6c-bad3-c0655293ff82'

if (process.env.HORAIN_SKIP_BACKEND_ENV !== '1') {
  loadEnvFile(path.join(REPO_ROOT, 'backend', '.env'))
  loadEnvFile(path.join(REPO_ROOT, 'promptfoo', '.env'))
  loadEnvFile(path.join(REPO_ROOT, 'langsmith', '.env'))
}

if (!process.env.LANGSMITH_TRACING && !process.env.LANGCHAIN_TRACING_V2) {
  process.env.LANGSMITH_TRACING = 'true'
}

const API_BASE = process.env.HORAIN_API_URL || 'http://localhost:8080'
const HORAIN_KEY = process.env.HORAIN_API_KEY || 'HORAIN_DEV_KEY'
const DATASET_ID = process.env.LANGSMITH_DATASET_ID?.trim() || DEFAULT_DATASET_ID

const client = new Client({
  apiKey: process.env.LANGCHAIN_API_KEY || process.env.LANGSMITH_API_KEY,
  apiUrl: (process.env.LANGSMITH_ENDPOINT || 'https://api.smith.langchain.com').replace(/\/$/, '')
})

const SCORED_CATALOG = loadPromptfooScoredCatalog()

function parseRunArgv(argv) {
  const forceJudge = argv.includes('--with-scored-judge')
  const noJudge = argv.includes('--no-scored-judge')
  const hasKey = Boolean(process.env.PROMPTFOO_JUDGE_MISTRAL_API_KEY?.trim())
  let useScoredJudge = false
  if (noJudge) {
    useScoredJudge = false
  } else if (forceJudge) {
    useScoredJudge = true
  } else {
    useScoredJudge = hasKey
  }
  return { useScoredJudge, forceJudge, hasKey }
}

function describeFetchFailure(err) {
  if (!(err instanceof Error)) {
    return String(err)
  }
  const cause = err.cause
  if (cause && typeof cause === 'object' && 'code' in cause) {
    return `${err.message} [${String(cause.code)}]`
  }
  return err.message
}

async function horainTarget(inputs) {
  const message = inputs.user_message
  if (typeof message !== 'string' || !message.trim()) {
    throw new Error('Dataset inputs must include user_message (see langsmith/scripts/import-promptfoo-to-langsmith.mjs)')
  }
  const url = `${API_BASE.replace(/\/$/, '')}/chat/message`
  let res
  try {
    res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${HORAIN_KEY}`
      },
      body: JSON.stringify({ message: message.trim(), history: [] })
    })
  } catch (err) {
    return {
      error: 'fetch_failed',
      detail: describeFetchFailure(err),
      hint: `Check backend is running and HORAIN_API_URL matches (default http://localhost:8080). Target: ${url}`
    }
  }
  const text = await res.text()
  let json
  try {
    json = text ? JSON.parse(text) : {}
  } catch {
    return { error: 'invalid_json', snippet: text.slice(0, 400) }
  }
  if (!res.ok) {
    return {
      error: json.error || json.message || `HTTP ${res.status}`,
      status: res.status
    }
  }
  return {
    message: json.assistantMessage || '',
    data: json.data ?? null,
    toolCalls: (json.toolCalls || []).map((t) => ({
      name: t.name,
      arguments: t.arguments,
      result: t.result != null ? String(t.result).slice(0, 500) : undefined
    }))
  }
}

async function horainResponseShape(ctx, _example) {
  const out = ctx.outputs
  if (!out || typeof out !== 'object') {
    return { key: 'horain_ok', score: 0, comment: 'missing outputs' }
  }
  if ('error' in out && out.error) {
    return { key: 'horain_ok', score: 0, comment: String(out.error) }
  }
  const msg = out.message
  const ok = typeof msg === 'string' && msg.trim().length > 0
  return {
    key: 'horain_ok',
    score: ok ? 1 : 0,
    comment: ok ? 'assistant message present' : 'empty or missing message'
  }
}

async function promptfooTraceability(ctx, example) {
  const meta = example.metadata || {}
  const desc = typeof meta.description === 'string' ? meta.description : ''
  const file = typeof meta.promptfoo_file === 'string' ? meta.promptfoo_file : ''
  return {
    key: 'promptfoo_meta',
    score: desc.length > 0 || file.length > 0 ? 1 : 0,
    comment: [file, desc].filter(Boolean).join(' — ').slice(0, 500)
  }
}

async function promptfooScoredLlmJudge(ctx, example) {
  const meta = example.metadata || {}
  const desc = typeof meta.description === 'string' ? meta.description.trim() : ''
  const entry = desc ? SCORED_CATALOG.get(desc) : null
  const userMsg = example.inputs?.user_message
  const userQuestion = typeof userMsg === 'string' ? userMsg : ''

  if (!entry) {
    return {
      results: [
        {
          key: 'promptfoo_llm_applied',
          score: 0,
          comment: 'no matching Promptfoo scored test (description not in catalog)'
        },
        {
          key: 'promptfoo_llm_score',
          score: 0,
          comment: 'n/a'
        }
      ]
    }
  }

  const apiKey = process.env.PROMPTFOO_JUDGE_MISTRAL_API_KEY?.trim()
  if (!apiKey) {
    return {
      results: [
        { key: 'promptfoo_llm_applied', score: 0, comment: 'missing PROMPTFOO_JUDGE_MISTRAL_API_KEY' },
        { key: 'promptfoo_llm_score', score: 0, comment: 'n/a' }
      ]
    }
  }

  const outputStr = JSON.stringify(ctx.outputs ?? {})
  const userContent = `User asked: ${userQuestion}\nOutput: ${outputStr}\nRubric: ${entry.rubric}`

  try {
    const model = process.env.PROMPTFOO_JUDGE_MODEL?.trim() || 'mistral-small-latest'
    const raw = await callMistralJudge(entry.system, userContent, apiKey, model)
    const parsed = parseJudgeJson(raw)
    const rawScore = parsed?.score
    const normalized = normalizeJudgeScore(typeof rawScore === 'number' ? rawScore : Number(rawScore))
    if (normalized === null) {
      return {
        results: [
          { key: 'promptfoo_llm_applied', score: 1, comment: 'judge called' },
          {
            key: 'promptfoo_llm_score',
            score: 0,
            comment: `parse_error: ${raw.slice(0, 300)}`
          }
        ]
      }
    }
    const passFromModel = typeof parsed.pass === 'boolean' ? parsed.pass : normalized >= entry.threshold
    const reason = typeof parsed.reason === 'string' ? parsed.reason : ''
    return {
      results: [
        { key: 'promptfoo_llm_applied', score: 1, comment: `threshold ${entry.threshold}` },
        {
          key: 'promptfoo_llm_score',
          score: normalized,
          comment: `${passFromModel ? 'PASS' : 'FAIL'} vs ${entry.threshold}: ${reason}`.slice(0, 1000)
        },
        {
          key: 'promptfoo_llm_pass',
          score: passFromModel ? 1 : 0,
          comment: `binary vs Promptfoo threshold ${entry.threshold}`
        }
      ]
    }
  } catch (e) {
    return {
      results: [
        { key: 'promptfoo_llm_applied', score: 1, comment: 'judge error' },
        {
          key: 'promptfoo_llm_score',
          score: 0,
          comment: e instanceof Error ? e.message.slice(0, 500) : String(e)
        },
        { key: 'promptfoo_llm_pass', score: 0, comment: 'judge request failed' }
      ]
    }
  }
}

async function main() {
  const { useScoredJudge, forceJudge, hasKey } = parseRunArgv(process.argv)

  if (!DATASET_ID) {
    console.error('Missing dataset id. Set LANGSMITH_DATASET_ID or update DEFAULT_DATASET_ID in this script.')
    process.exit(1)
  }
  if (!client.apiKey) {
    console.error('Missing LANGCHAIN_API_KEY or LANGSMITH_API_KEY')
    process.exit(1)
  }
  if (forceJudge && !hasKey) {
    console.error('--with-scored-judge requires PROMPTFOO_JUDGE_MISTRAL_API_KEY (e.g. in promptfoo/.env)')
    process.exit(1)
  }

  const evaluators = [horainResponseShape, promptfooTraceability]
  if (useScoredJudge) {
    evaluators.push(promptfooScoredLlmJudge)
    console.error(
      `Scored LLM judge: ON (catalog ${SCORED_CATALOG.size} cases, Mistral key ${hasKey ? 'present' : 'missing'})`
    )
    if (!hasKey) {
      console.error('Warning: judge enabled but no API key — scored rows will get 0 on promptfoo_llm_score.')
    }
  } else {
    console.error(
      'Scored LLM judge: OFF (set PROMPTFOO_JUDGE_MISTRAL_API_KEY or pass --with-scored-judge; use --no-scored-judge to force off)'
    )
  }

  console.error(`Dataset: ${DATASET_ID}`)
  console.error(`Horain API: ${API_BASE}`)

  const useSlowMistral = useScoredJudge && hasKey
  const results = await evaluate(horainTarget, {
    data: DATASET_ID,
    evaluators,
    client,
    experimentPrefix: 'horain-promptfoo',
    maxConcurrency: useSlowMistral ? 1 : 2
  })

  await client.awaitPendingTraceBatches()
  console.error(`Done. Experiment: ${results.experimentName}, rows: ${results.length}`)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
