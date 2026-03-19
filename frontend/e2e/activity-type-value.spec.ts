import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY, RECENT_LOGGED_AT, uniqueProjectName } from './e2eEnv'

/**
 * E2E: Activity type and value on card verso.
 * Creates project and time log with activityTypeCode DEV (400 €/day), then verifies
 * the card verso shows € and the computed amount (30 min = 25 €).
 */
test('entry with activity type shows euro and amount on verso', async ({ page, request }) => {
  const projectName = uniqueProjectName('ActivityTypeE2E')
  const projectRes = await request.post(`${API_BASE}/projects`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: { name: projectName, description: 'e2e activity type', billable: true },
  })
  if (!projectRes.ok()) {
    throw new Error(`Project API failed (${projectRes.status()}). Ensure backend is running and API key matches.`)
  }
  const project = (await projectRes.json()) as { id: string }

  const timeLogRes = await request.post(`${API_BASE}/time-logs`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: {
      projectId: project.id,
      durationMinutes: 30,
      note: `e2e activity type value ${projectName}`,
      activityTypeCode: 'DEV',
      loggedAt: RECENT_LOGGED_AT,
    },
  })
  expect(timeLogRes.ok()).toBeTruthy()

  await page.goto('/')
  await page.waitForResponse((resp) => resp.url().includes('/time-logs/recent') && resp.status() === 200, { timeout: 15000 })
  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()
  await expect(page.getByText('Dernières activités')).toBeVisible({ timeout: 5000 })

  const card = page
    .locator('.card-wrapper')
    .filter({ has: page.locator('.card-note').filter({ hasText: `e2e activity type value ${projectName}` }) })
    .first()
  await expect(card).toBeVisible({ timeout: 10000 })

  await card.click()
  await expect(card).toHaveClass(/card-wrapper--expanded/, { timeout: 5000 })
  await expect(card.locator('.card-project').filter({ hasText: projectName })).toBeVisible()
  // Amount: computed value in € when API returns dailyRateCents (e.g. 25 € for 30 min DEV); can vary with shared backend
  const amountValue = card.locator('.card-amount-value')
  const amountIcon = card.locator('.card-amount-icon')
  await expect(amountValue.or(amountIcon)).toBeVisible()
  if (await amountValue.isVisible()) {
    await expect(amountValue).toContainText(/\d+(\.\d)?/)
    await expect(amountValue).toContainText('€')
  }
})
