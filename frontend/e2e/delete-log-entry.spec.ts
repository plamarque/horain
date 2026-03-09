import { test, expect } from '@playwright/test'
import { API_BASE, API_KEY } from './e2eEnv'

/**
 * E2E: Delete a time log entry via the edit modal.
 * Creates data via API (no LLM), then double-clicks on the row to open the edit modal,
 * clicks Delete, confirms, and the entry is removed.
 */
test('delete log entry via edit modal', async ({ page, request }) => {
  // Create project and time log via API (no LLM) — reliable
  const projectRes = await request.post(`${API_BASE}/projects`, {
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    data: { name: 'DeleteLogEntryTest', description: 'e2e delete test' },
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
      durationMinutes: 15,
      note: 'e2e delete test',
    },
  })
  expect(timeLogRes.ok()).toBeTruthy()

  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Horain' })).toBeVisible()

  // Recent activities or empty state shows the entry (most recent)
  await expect(page.getByText('Dernières activités')).toBeVisible({
    timeout: 5000,
  })
  const projectCell = page
    .locator('.log-table')
    .getByRole('cell', { name: 'DeleteLogEntryTest' })
    .first()
  await expect(projectCell).toBeVisible({ timeout: 5000 })
  await projectCell.dblclick()

  // Edit modal should open
  await expect(page.getByRole('heading', { name: 'Edit entry' })).toBeVisible()

  // Click Delete to show confirmation
  await page.getByRole('button', { name: 'Delete' }).first().click()

  // Confirm deletion
  await expect(page.getByText('Delete this entry permanently?')).toBeVisible()
  await page.getByRole('button', { name: 'Delete' }).click()

  // Modal closes, entry removed from table
  await expect(page.getByRole('heading', { name: 'Edit entry' })).not.toBeVisible()
  await expect(page.getByRole('cell', { name: 'DeleteLogEntryTest' })).not.toBeVisible()
})
