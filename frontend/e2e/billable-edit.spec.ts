import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY } from './e2eEnv'

/**
 * E2E: Toggle billable on a time log entry via the edit modal.
 * Creates project and time log via API, opens edit modal, unchecks Facturable,
 * saves, and verifies the table shows "Non" for that entry.
 */
test('edit entry - toggle billable via modal', async ({ page, request }) => {
  const projectRes = await request.post(`${API_BASE}/projects`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: { name: 'BillableEditE2E', description: 'e2e billable toggle', billable: true },
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
  const projectCell = page
    .locator('.log-table')
    .getByRole('cell', { name: 'BillableEditE2E' })
    .first()
  await expect(projectCell).toBeVisible({ timeout: 5000 })
  await projectCell.dblclick()

  await expect(page.getByRole('heading', { name: 'Edit entry' })).toBeVisible()

  const billableCheckbox = page.getByRole('checkbox', { name: 'Facturable' })
  await expect(billableCheckbox).toBeVisible()
  await expect(billableCheckbox).toBeChecked()

  await billableCheckbox.uncheck()
  await page.getByRole('button', { name: 'Save' }).click()

  await expect(page.getByRole('heading', { name: 'Edit entry' })).not.toBeVisible()

  const row = page.locator('.log-table tbody tr').filter({ has: page.getByRole('cell', { name: 'BillableEditE2E' }) })
  await expect(row.getByRole('cell', { name: 'Non' })).toBeVisible()
})
