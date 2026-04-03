/**
 * Mistral chat completion for LLM-as-judge (same API as Promptfoo scored evals).
 */

const MISTRAL_URL = 'https://api.mistral.ai/v1/chat/completions'

/**
 * @param {string} system
 * @param {string} user
 * @param {string} apiKey
 * @param {string} [model]
 * @returns {Promise<string>} assistant message content
 */
export async function callMistralJudge(system, user, apiKey, model) {
  const res = await fetch(MISTRAL_URL, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      model: model || 'mistral-small-latest',
      temperature: 0,
      messages: [
        { role: 'system', content: system },
        { role: 'user', content: user }
      ]
    })
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) {
    const msg = data.message || data.error?.message || JSON.stringify(data).slice(0, 400)
    throw new Error(`Mistral API ${res.status}: ${msg}`)
  }
  const content = data.choices?.[0]?.message?.content
  return typeof content === 'string' ? content : ''
}

/**
 * Extract JSON object with score / reason / pass from model output (handles ```json fences).
 * @param {string} text
 * @returns {{ score?: number, reason?: string, pass?: boolean } | null}
 */
export function parseJudgeJson(text) {
  if (!text || typeof text !== 'string') {
    return null
  }
  let s = text.trim()
  const fence = s.match(/^```(?:json)?\s*([\s\S]*?)```$/m)
  if (fence) {
    s = fence[1].trim()
  }
  try {
    const o = JSON.parse(s)
    if (o && typeof o === 'object') {
      return o
    }
  } catch {
    const idx = s.indexOf('{')
    const last = s.lastIndexOf('}')
    if (idx >= 0 && last > idx) {
      try {
        const o = JSON.parse(s.slice(idx, last + 1))
        if (o && typeof o === 'object') {
          return o
        }
      } catch {
        /* ignore */
      }
    }
  }
  return null
}

/**
 * Normalize model score to 0..1 (supports 0-10 scale).
 * @param {number} n
 */
export function normalizeJudgeScore(n) {
  if (typeof n !== 'number' || Number.isNaN(n)) {
    return null
  }
  if (n >= 0 && n <= 1) {
    return n
  }
  if (n > 1 && n <= 10) {
    return n / 10
  }
  if (n > 10) {
    return Math.min(1, n / 100)
  }
  return Math.max(0, Math.min(1, n))
}
