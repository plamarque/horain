import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { parse } from 'yaml'

export const REPO_ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

/**
 * Shared CLI flags for reading the Promptfoo corpus (config + file refs).
 * @param {string[]} argv
 * @returns {{ config: string, skipScored: boolean, only: string | null }}
 */
export function parseCorpusArgv(argv) {
  const out = {
    config: path.join(REPO_ROOT, 'promptfoo', 'promptfooconfig.yaml'),
    skipScored: false,
    only: null
  }
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--skip-scored') {
      out.skipScored = true
    } else if (a.startsWith('--config=')) {
      const p = a.slice('--config='.length)
      out.config = path.isAbsolute(p) ? p : path.resolve(REPO_ROOT, p)
    } else if (a === '--config' && argv[i + 1]) {
      const p = argv[++i]
      out.config = path.isAbsolute(p) ? p : path.resolve(REPO_ROOT, p)
    } else if (a.startsWith('--only=')) {
      out.only = a.slice('--only='.length)
    } else if (a === '--only' && argv[i + 1]) {
      out.only = argv[++i]
    }
  }
  return out
}

export function toPosix(p) {
  return p.split(path.sep).join('/')
}

export function parseYamlFile(filePath) {
  const content = fs.readFileSync(filePath, 'utf8')
  return parse(content, { merge: true })
}

export function getMessage(test) {
  if (!test || typeof test !== 'object' || !test.vars) {
    return null
  }
  const m = test.vars.message
  if (typeof m !== 'string') {
    return null
  }
  const t = m.trim()
  return t.length > 0 ? m : null
}

export function shouldIncludePath(relPosix, options) {
  if (options.skipScored && relPosix.includes('/scored/')) {
    return false
  }
  if (options.only) {
    try {
      const re = new RegExp(options.only, 'i')
      if (!re.test(relPosix)) {
        return false
      }
    } catch {
      console.error('Invalid --only regex:', options.only)
      process.exit(1)
    }
  }
  return true
}

/**
 * @param {string} fullPath
 * @param {string} relFromRepo
 * @param {{ skipScored: boolean, only: string | null }} options
 */
export function extractTestsFromYamlFile(fullPath, relFromRepo, options) {
  const relPosix = toPosix(relFromRepo)
  if (!shouldIncludePath(relPosix, options)) {
    return []
  }
  let doc
  try {
    doc = parseYamlFile(fullPath)
  } catch (e) {
    console.error(`YAML parse failed (${relPosix}):`, e.message)
    return []
  }
  const items = Array.isArray(doc) ? doc : []
  const rows = []
  for (const item of items) {
    const msg = getMessage(item)
    if (!msg) {
      continue
    }
    const desc =
      typeof item.description === 'string' && item.description.trim()
        ? item.description.trim()
        : ''
    rows.push({
      user_message: msg,
      description: desc,
      promptfoo_file: relPosix
    })
  }
  return rows
}

/**
 * @param {string} configPath
 * @param {{ skipScored: boolean, only: string | null }} options
 * @returns {Array<{ user_message: string, description: string, promptfoo_file: string }>}
 */
export function collectExamples(configPath, options) {
  const configAbs = path.resolve(configPath)
  if (!fs.existsSync(configAbs)) {
    console.error('Config not found:', configAbs)
    process.exit(1)
  }
  const baseDir = path.dirname(configAbs)
  let root
  try {
    root = parseYamlFile(configAbs)
  } catch (e) {
    console.error('Config parse failed:', e.message)
    process.exit(1)
  }
  const tests = root.tests
  if (!Array.isArray(tests)) {
    console.error('Config has no tests: array')
    process.exit(1)
  }

  const examples = []
  const configRel = toPosix(path.relative(REPO_ROOT, configAbs))

  for (const entry of tests) {
    if (typeof entry === 'string' && entry.startsWith('file://')) {
      const rel = entry.replace(/^file:\/\//, '')
      const fullPath = path.join(baseDir, rel)
      if (!fs.existsSync(fullPath)) {
        console.error('Missing test file:', fullPath)
        continue
      }
      const relFromRepo = path.relative(REPO_ROOT, fullPath)
      examples.push(...extractTestsFromYamlFile(fullPath, relFromRepo, options))
    } else if (entry && typeof entry === 'object' && entry.vars) {
      const msg = getMessage(entry)
      if (!msg) {
        continue
      }
      const desc =
        typeof entry.description === 'string' && entry.description.trim()
          ? entry.description.trim()
          : ''
      examples.push({
        user_message: msg,
        description: desc,
        promptfoo_file: configRel
      })
    }
  }

  return examples
}
