import fs from 'fs'
import path from 'path'
import { parse } from 'yaml'
import { REPO_ROOT } from './promptfoo-corpus.mjs'

const SCORED_GLOB_DIR = path.join(REPO_ROOT, 'promptfoo', 'tests', 'scored')

const DEFAULT_SYSTEM =
  'You are an evaluator. Return only valid JSON: {"reason":"string","score":number between 0 and 1,"pass":boolean}.'

/**
 * @param {unknown} asserts
 * @returns {{ value: string, threshold: number } | null}
 */
function extractLlmRubric(asserts) {
  if (!Array.isArray(asserts)) {
    return null
  }
  for (const a of asserts) {
    if (a && a.type === 'llm-rubric' && typeof a.value === 'string') {
      const threshold =
        typeof a.threshold === 'number' && !Number.isNaN(a.threshold) ? a.threshold : 0.75
      return { value: a.value, threshold }
    }
  }
  return null
}

/**
 * @param {string | undefined} rubricPrompt
 */
function parseSystemFromRubricPrompt(rubricPrompt) {
  if (typeof rubricPrompt !== 'string' || !rubricPrompt.trim()) {
    return DEFAULT_SYSTEM
  }
  try {
    const arr = JSON.parse(rubricPrompt.trim())
    if (Array.isArray(arr) && arr[0]?.role === 'system' && typeof arr[0].content === 'string') {
      return arr[0].content
    }
  } catch {
    /* ignore */
  }
  return DEFAULT_SYSTEM
}

/**
 * Load all scored Promptfoo tests (llm-rubric + rubricPrompt) keyed by exact `description`
 * (must match metadata.description on LangSmith examples from import-promptfoo-to-langsmith.mjs).
 * @returns {Map<string, { rubric: string, threshold: number, system: string, sourceFile: string }>}
 */
export function loadPromptfooScoredCatalog() {
  const map = new Map()
  if (!fs.existsSync(SCORED_GLOB_DIR)) {
    return map
  }
  const files = fs.readdirSync(SCORED_GLOB_DIR).filter((f) => f.endsWith('.yaml') || f.endsWith('.yml'))
  for (const f of files) {
    const fp = path.join(SCORED_GLOB_DIR, f)
    let doc
    try {
      doc = parse(fs.readFileSync(fp, 'utf8'), { merge: true })
    } catch (e) {
      console.error(`[promptfoo-scored-catalog] skip ${f}: ${e.message}`)
      continue
    }
    const items = Array.isArray(doc) ? doc : []
    const rel = path.join('promptfoo', 'tests', 'scored', f).split(path.sep).join('/')
    for (const item of items) {
      const rub = extractLlmRubric(item.assert)
      if (!rub) {
        continue
      }
      const desc = typeof item.description === 'string' ? item.description.trim() : ''
      if (!desc) {
        continue
      }
      const system = parseSystemFromRubricPrompt(item.options?.rubricPrompt)
      map.set(desc, {
        rubric: rub.value,
        threshold: rub.threshold,
        system,
        sourceFile: rel
      })
    }
  }
  return map
}
