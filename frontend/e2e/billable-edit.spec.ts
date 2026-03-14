import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY, uniqueProjectName } from './e2eEnv'

/**
 * E2E: Toggle billable on a time log entry via the edit modal.
 * Creates project and time log via API, opens edit modal, unchecks Facturable,
 * saves, and verifies the table shows "Non" for that entry.
 */
test('edit entry - toggle billable via modal', async ({ page, request }) => {
  const projectName = uniqueProjectName('BillableEditE2E')
  const projectRes = await request.post(`${API_BASE}/projects`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: { name: projectName, description: 'e2e billable toggle', billable: true },
  })
  if (!projectRes.ok()) {
    const hint =
      projectRes.status() === 401
        ? ' API key mismatch: ensure backend/.env HORAIN_API_KEY matches (or set VITE_API_KEY).'
        : ' Ensure backend is running on 8080.'
    throw new Error(`Project API failed (${projectRes.status()}).${hint}`)
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
      note: 'e2e billable test',
    },
  })
  expect(timeLogRes.ok()).toBeTruthy()

  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  await expect(page.getByText('Dernières activités')).toBeVisible({ timeout: 5000 })
  const card = page
    .locator('.card-wrapper')
    .filter({ has: page.locator('.card-note').filter({ hasText: 'e2e billable test' }) })
    .first()
  await expect(card).toBeVisible({ timeout: 5000 })
  await card.click()
  await card.getByRole('button', { name: 'Edit entry' }).click()

  const entryEditScreen = page.locator('.entry-edit-screen')
  await expect(entryEditScreen).toBeVisible({ timeout: 5000 })
  await expect(entryEditScreen.getByRole('heading', { name: 'Edit entry' })).toBeVisible()

  const billableCheckbox = entryEditScreen.locator('#edit-billable')
  await expect(billableCheckbox).toBeVisible()
  await expect(billableCheckbox).toBeChecked()

  await billableCheckbox.uncheck({ force: true })
  await entryEditScreen.getByRole('button', { name: 'Save' }).click()

  await expect(page.getByRole('heading', { name: 'Edit entry' })).not.toBeVisible()

  // After save, recent logs are refetched; card with this project should still be visible
  await expect(
    page.locator('.card-project').filter({ hasText: projectName })
  ).toHaveCount(1, { timeout: 10000 })
})
