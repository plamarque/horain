#!/usr/bin/env node
/**
 * Pushes eval candidate rows (JSONL) into a LangSmith dataset as examples.
 *
 * Prerequisites:
 *   - LANGCHAIN_API_KEY (LangSmith / LangChain API key)
 *   - LANGSMITH_DATASET_ID (UUID of an existing dataset in LangSmith)
 * Optional:
 *   - LANGSMITH_ENDPOINT (default: https://api.smith.langchain.com)
 *
 * Usage (from repo root):
 *   curl -sS -H "Authorization: Bearer $HORAIN_API_KEY" "$EVAL_CANDIDATES_ENDPOINT" \
 *     | node scripts/export-eval-to-langsmith.mjs
 *
 * Or from a file:
 *   node scripts/export-eval-to-langsmith.mjs < scripts/out/eval-candidates.jsonl
 */

const API_KEY = process.env.LANGCHAIN_API_KEY
const DATASET_ID = process.env.LANGSMITH_DATASET_ID
const BASE = (process.env.LANGSMITH_ENDPOINT || 'https://api.smith.langchain.com').replace(/\/$/, '')

async function postExample(row) {
  const body = {
    dataset_id: DATASET_ID,
    inputs: {
      user_message: row.user_message ?? '',
      source_turn_id: row.source_turn_id ?? ''
    },
    outputs: {
      assistant_message: row.assistant_message ?? '',
      status: row.status ?? '',
      feedback: row.feedback ?? null
    },
    metadata: {
      conversation_id: row.conversation_id ?? null,
      model: row.model ?? null
    }
  }
  const res = await fetch(`${BASE}/examples`, {
    method: 'POST',
    headers: {
      'x-api-key': API_KEY,
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
  if (!API_KEY || !DATASET_ID) {
    console.error('Missing LANGCHAIN_API_KEY or LANGSMITH_DATASET_ID')
    process.exit(1)
  }
  const chunks = []
  for await (const chunk of process.stdin) {
    chunks.push(chunk)
  }
  const text = Buffer.concat(chunks).toString('utf8')
  const lines = text.split('\n').map((l) => l.trim()).filter(Boolean)
  let ok = 0
  for (const line of lines) {
    let row
    try {
      row = JSON.parse(line)
    } catch (e) {
      console.error('Skip invalid JSON line:', line.slice(0, 80))
      continue
    }
    await postExample(row)
    ok++
  }
  console.error(`Uploaded ${ok} example(s) to dataset ${DATASET_ID}`)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
