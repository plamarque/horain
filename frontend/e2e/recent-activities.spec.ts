import { test, expect } from '@playwright/test'

/**
 * E2E: Recent activities displayed on launch.
 * When the conversation is empty, the app shows the 5 most recent logged activities
 * (fetched via API, no LLM call). If no data exists, a placeholder is shown.
 */
test('recent activities displayed on launch when data exists', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  const input = page.getByPlaceholder('Ask anything')
  await expect(input).toBeVisible()

  // Create a time log first (requires backend with LLM)
  await input.fill('25 minutes on RecentActivitiesTest working on e2e')
  await input.press('Enter')

  await expect(
    page.getByText(/logged|created|minutes|RecentActivitiesTest/i)
  ).toBeVisible({ timeout: 5000 })

  // Reload page: conversation is empty, recentLogs fetch runs on mount
  await page.reload()

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  // Expect "Dernières activités" and the project name in the empty state
  await expect(page.getByText('Dernières activités')).toBeVisible({ timeout: 5000 })
  await expect(
    page.getByRole('cell', { name: 'RecentActivitiesTest' }).first()
  ).toBeVisible()
})

test('empty state shows placeholder or recent activities', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  // Empty state shows either: "Dernières activités" (with data) or "Say something like" (without)
  await expect(
    page.getByText('Dernières activités').or(page.getByText('Say something like'))
  ).toBeVisible({ timeout: 5000 })
})
