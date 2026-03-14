import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY, uniqueProjectName } from './e2eEnv'

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
      note: 'e2e activity type value',
      activityTypeCode: 'DEV',
    },
  })
  expect(timeLogRes.ok()).toBeTruthy()

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()
  await expect(page.getByText('Dernières activités')).toBeVisible({ timeout: 5000 })

  const card = page
    .locator('.card-wrapper')
    .filter({ has: page.locator('.card-verso-project').filter({ hasText: projectName }) })
    .first()
  await expect(card).toBeVisible({ timeout: 5000 })

  await card.click()
  await expect(card.locator('.card-verso')).toBeVisible()
  // When entry has activity type with rate, we show amount in .card-amount-value (not .card-billable-icon)
  await expect(card.locator('.card-amount-value').filter({ hasText: '25 €' })).toBeVisible()
})
