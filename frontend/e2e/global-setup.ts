import type { FullConfig } from '@playwright/test'
import { API_BASE, API_KEY } from './e2eEnv'

/**
 * Resets the dev database and loads canonical seed data once per test run.
 * Avoids POST /dev/seed failing with 500 when local DB has duplicate project names
 * (same name as seed projects but different UUIDs → time logs reference missing projects).
 */
export default async function globalSetup(_config: FullConfig): Promise<void> {
  const res = await fetch(`${API_BASE}/dev/seed/reset`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    body: '{}',
  })
  if (!res.ok) {
    const body = await res.text()
    throw new Error(
      `E2E global setup: POST /dev/seed/reset failed (${res.status}). ` +
        `Start the backend on ${API_BASE} with horain.dev.seed-enabled=true. ${body}`
    )
  }
}
