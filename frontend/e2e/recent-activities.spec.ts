import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY } from './e2eEnv'

/**
 * E2E: Recent activities displayed on launch.
 * When the conversation is empty, the app shows the 8 most recent logged activities
 * (fetched via API, no LLM call). If no data exists, a placeholder is shown.
 *
 * Uses dev seed API to populate data (no LLM) for reliability.
 */
test('recent activities displayed on launch when data exists', async ({
  page,
  request,
}) => {
  // Seed data via API (no LLM) — reliable, works in CI
  // Prerequisite: backend must be running on port 8080 (e.g. ./scripts/start-dev.sh or mvn spring-boot:run)
  const seedRes = await request.post(`${API_BASE}/dev/seed`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: {},
  })
  if (!seedRes.ok()) {
    const body = await seedRes.text()
    const hint =
      seedRes.status() === 401
        ? ' API key mismatch: ensure backend/.env HORAIN_API_KEY matches (or set VITE_API_KEY).'
        : ' Ensure backend is running on 8080.'
    throw new Error(`Seed API failed (${seedRes.status()}).${hint} Response: ${body}`)
  }

  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  // Expect "Dernières activités" and seeded project names (Horain, HatCast, etc.)
  await expect(page.getByText('Dernières activités')).toBeVisible({
    timeout: 5000,
  })
  await expect(
    page
      .locator('.log-entries-bubble')
      .getByText(/Horain|HatCast|Chrono|Festibask|Meeds|Weather/i)
      .first()
  ).toBeVisible({ timeout: 10000 })
})

test('empty state shows placeholder or recent activities', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  // Empty state shows either: "Dernières activités" (with data) or "Say something like" (without)
  await expect(
    page.getByText('Dernières activités').or(page.getByText('Say something like'))
  ).toBeVisible({ timeout: 5000 })
})
