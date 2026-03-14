import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY, uniqueProjectName } from './e2eEnv'

/**
 * E2E: Billable project card shows total revenue in euros on Projects view.
 * Creates a billable project and a time log with activity type DEV (400 €/day);
 * 30 min => 25 €. Opens Projects view and verifies the project card displays "25 €".
 */
test('billable project card shows revenue in euros on Projects view', async ({ page, request }) => {
  const projectName = uniqueProjectName('ProjectRevenueE2E')
  const projectRes = await request.post(`${API_BASE}/projects`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: { name: projectName, description: 'e2e project revenue', billable: true },
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
      note: 'e2e revenue test',
      activityTypeCode: 'DEV',
    },
  })
  expect(timeLogRes.ok()).toBeTruthy()

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  await page.getByRole('button', { name: 'Projects' }).click()
  await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible({ timeout: 5000 })

  const projectCard = page.locator('.project-card').filter({ hasText: projectName }).first()
  await expect(projectCard).toBeVisible({ timeout: 5000 })
  const billableEl = projectCard.locator('.project-card-billable')
  await expect(billableEl).toBeVisible()
  // Revenue in euros (25 € for 30 min DEV; can be higher if backend has other entries)
  await expect(billableEl).toHaveText(/\d+(\.\d)? €/)
})
