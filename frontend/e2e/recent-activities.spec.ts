import { test, expect } from '@playwright/test'

const API_BASE = process.env.PLAYWRIGHT_API_URL || 'http://localhost:8080'
const API_KEY = process.env.VITE_API_KEY || 'HORAIN_DEV_KEY'

/**
 * E2E: Recent activities displayed on launch.
 * When the conversation is empty, the app shows the 5 most recent logged activities
 * (fetched via API, no LLM call). If no data exists, a placeholder is shown.
 *
 * Uses dev seed API to populate data (no LLM) for reliability.
 */
test('recent activities displayed on launch when data exists', async ({
  page,
  request,
}) => {
  // Seed data via API (no LLM) — reliable, works in CI
  const seedRes = await request.post(`${API_BASE}/dev/seed`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: {},
  })
  expect(seedRes.ok()).toBeTruthy()

  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  // Expect "Dernières activités" and seeded project names (Horain, HatCast, etc.)
  await expect(page.getByText('Dernières activités')).toBeVisible({
    timeout: 5000,
  })
  await expect(
    page.locator('.log-table').getByText(/Horain|HatCast|Chrono|Festibask|Meeds|Weather/i)
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
