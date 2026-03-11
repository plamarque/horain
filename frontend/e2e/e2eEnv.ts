/**
 * E2E test environment config.
 * Reads API base URL and key, falling back to backend/.env when env vars are not set.
 * This ensures tests use the same API key as the backend (avoids 401).
 */
import fs from 'node:fs'
import path from 'node:path'

function loadApiKey(): string {
  if (process.env.VITE_API_KEY) return process.env.VITE_API_KEY
  if (process.env.HORAIN_API_KEY) return process.env.HORAIN_API_KEY

  const backendEnvPath = path.resolve(process.cwd(), '..', 'backend', '.env')
  try {
    const content = fs.readFileSync(backendEnvPath, 'utf8')
    const match = content.match(/HORAIN_API_KEY\s*=\s*["']?([^"'#\n]+)["']?/)
    if (match) return match[1].trim()
  } catch {
    /* .env may not exist */
  }
  return 'HORAIN_DEV_KEY'
}

export const API_BASE = process.env.PLAYWRIGHT_API_URL || 'http://localhost:8080'
export const API_KEY = loadApiKey()

/** Unique project name for e2e to avoid 500 on duplicate name (backend enforces unique). */
export function uniqueProjectName(prefix: string): string {
  const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  return `${prefix}-${id}`
}
