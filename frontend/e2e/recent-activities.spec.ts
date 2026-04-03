import { test, expect } from '@playwright/test'
import { recentActivitiesTitleLocator } from './e2eEnv'

/**
 * E2E: Recent activities displayed on launch.
 * When the conversation is empty, the app shows recent logged activities in the selected
 * activity period (fetched via API, no LLM call). If no data exists, a placeholder is shown.
 *
 * Data comes from Playwright global setup (POST /dev/seed/reset once per run).
 */
test('recent activities displayed on launch when data exists', async ({ page }) => {
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
