import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY, recentActivitiesTitleLocator } from './e2eEnv'

/**
 * E2E: Recent activities displayed on launch.
 * When the conversation is empty, the app shows recent logged activities in the selected
 * activity period (fetched via API, no LLM call). If no data exists, a placeholder is shown.
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

  await Promise.all([
    page.waitForResponse(
      (resp) => resp.url().includes('/time-logs/recent') && resp.status() === 200,
      { timeout: 15000 }
    ),
    page.goto('/'),
  ])

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  await expect(recentActivitiesTitleLocator(page)).toBeVisible({
    timeout: 5000,
  })
  // At least one card; first card may be from seed or another test (order varies with parallel runs)
  const bubble = page.locator('.log-entries-bubble')
  await expect(bubble.locator('.card-wrapper').first()).toBeVisible({ timeout: 5000 })
  // Expand first card and ensure it shows a project name
  const firstCard = bubble.locator('.card-wrapper').first()
  await firstCard.click()
  await expect(firstCard).toHaveClass(/card-wrapper--expanded/, { timeout: 3000 })
  await expect(firstCard.locator('.card-project')).toBeVisible({ timeout: 2000 })
  await expect(firstCard.locator('.card-project')).not.toHaveText(/^\s*—\s*$|^$/)
})

test('empty state shows placeholder or recent activities', async ({ page }) => {
  await Promise.all([
    page.waitForResponse(
      (resp) => resp.url().includes('/time-logs/recent') && resp.status() === 200,
      { timeout: 15000 }
    ),
    page.goto('/'),
  ])

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  // Empty state shows either activity block title (with data in period) or placeholder (without)
  await expect(
    recentActivitiesTitleLocator(page).or(page.getByText('Say something like'))
  ).toBeVisible({ timeout: 5000 })
})
